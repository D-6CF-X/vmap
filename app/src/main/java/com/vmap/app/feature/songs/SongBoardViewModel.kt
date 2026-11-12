package com.vmap.app.feature.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import android.net.Uri
import com.vmap.app.data.db.SongDao
import com.vmap.app.data.song.CoverStore
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 导入结果，用来给用户一句反馈。 */
data class ImportResult(val added: Int, val skipped: Int)

/**
 * 歌单排行：评级 + 歌曲，手动拖拽排序。
 *
 * 排序全部落库成 [Song.orderIndex]（组内递增），所以重进应用顺序不变；
 * 拖动过程中每次跨越一格就写一次库，频率不高，换来的是列表能立刻跟上手指。
 */
class SongBoardViewModel(
    private val dao: SongDao,
    private val coverStore: CoverStore,
    private val context: android.content.Context,
) : ViewModel() {

    val ratings: StateFlow<List<SongRating>> = dao.observeRatings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs: StateFlow<List<Song>> = dao.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            ensureDefaults()
            seedArchive()
            repairSeriesData()
            dedupeSongs()
            dedupeVariants()
        }
    }

    /**
     * 给系列歌单兜底：把库里「没有系列信息」的歌按 externalId 对回内置档案。
     *
     * 老版本导入时可能只写了歌名/歌手/封面，`tour` 是空的，系列歌单页就会一首都没有。
     * 这一步每次启动都跑，但只有真的缺数据时才写库（302 条里挑出空的，成本可以忽略）。
     */
    private suspend fun repairSeriesData() {
        val all = dao.observeSongs().first()
        if (all.isEmpty()) return
        val missing = all.filter { it.tour.isBlank() && it.externalId.isNotBlank() }
        if (missing.isEmpty()) return
        val archive = com.vmap.app.data.song.SongArchive.load(context).associateBy { it.id }
        if (archive.isEmpty()) return
        val fixed = missing.mapNotNull { song ->
            val a = archive[song.externalId] ?: return@mapNotNull null
            if (a.tour.isBlank()) return@mapNotNull null
            song.copy(
                tour = a.tour,
                years = if (song.years.isBlank()) a.years else song.years,
                series = if (song.series.isBlank()) a.series else song.series,
                perfCount = if (song.perfCount <= 0) a.perfCount else song.perfCount,
            )
        }
        android.util.Log.i(
            "VMapRepair",
            "all=${all.size} missing=${missing.size} archive=${archive.size} fixed=${fixed.size}",
        )
        if (fixed.isNotEmpty()) dao.upsertSongs(fixed)
    }

    /**
     * 首次进入给一套默认档位：神 / 夯 / 顶 / 赞 / 稳。
     *
     * 老用户库里还是 S/A/B/C 那套（而且没改过名字）的话，就地改名加一档，
     * 评级 id 不动，所以已经拖好的歌不会错位；用户自己改过档名的就不碰。
     */
    private suspend fun ensureDefaults() {
        val existing = dao.observeRatings().first().sortedBy { it.orderIndex }
        val prefs = context.getSharedPreferences("vmap_seed", android.content.Context.MODE_PRIVATE)

        if (existing.isEmpty()) {
            dao.upsertRatings(DEFAULT_RATINGS)
            prefs.edit()
                .putBoolean(KEY_RATINGS_V2, true)
                .putBoolean(KEY_RATINGS_V3, true)
                .putBoolean(KEY_RATINGS_V4, true)
                .apply()
            return
        }

        if (prefs.getBoolean(KEY_RATINGS_V2, false)) {
            // v3：第四档「赞」→「香」，五档换成 tier list 的暖色阶（只改颜色和这一个名字）
            if (!prefs.getBoolean(KEY_RATINGS_V3, false)) {
                if (existing.size == DEFAULT_RATINGS.size) {
                    val palette = DEFAULT_RATINGS.map { it.color }
                    val updated = existing.mapIndexed { i, r ->
                        r.copy(
                            name = if (r.name == "赞") "香" else r.name,
                            color = palette.getOrElse(i) { r.color },
                        )
                    }
                    dao.upsertRatings(updated)
                }
                prefs.edit().putBoolean(KEY_RATINGS_V3, true).apply()
            }
            // v4：第四档「香」→「赞」，用户最终确认叫「赞」，只改这一个名字
            if (!prefs.getBoolean(KEY_RATINGS_V4, false)) {
                val updated = dao.observeRatings().first().map { r ->
                    if (r.name == "香") r.copy(name = "赞") else r
                }
                if (updated.any { it.name == "赞" }) dao.upsertRatings(updated)
                prefs.edit().putBoolean(KEY_RATINGS_V4, true).apply()
            }
            return
        }
        val names = existing.map { it.name }
        if (names == listOf("S", "A", "B", "C")) {
            val renamed = existing.mapIndexed { i, r ->
                val d = DEFAULT_RATINGS[i]
                r.copy(name = d.name, color = d.color)
            }
            // 补上第五档「稳」，排在最后
            val fifth = DEFAULT_RATINGS[4]
            dao.upsertRatings(renamed + fifth)
        }
        prefs.edit().putBoolean(KEY_RATINGS_V2, true).apply()
        prefs.edit().putBoolean(KEY_RATINGS_V3, true).apply()
        prefs.edit().putBoolean(KEY_RATINGS_V4, true).apply()
    }

    /**
     * 第二遍去重：同曲异名的条目。
     *
     * 「心印」和「心印 (Step on your heart)」、「Monologue」和「【洛天依X Studio】Monologue」、
     * 「ジェミニ」和「ジェミニ (feat. 鏡音リン・レン)」都是同一首歌的两个投稿条目，只留纯标题那条。
     *
     * 判定很保守：**必须存在一条「纯标题」的版本**才合并。像「黑凤梨(AI)」和「黑凤梨remix」
     * 两条都带副标题，算不同版本，都不动。
     */
    private suspend fun dedupeVariants() {
        val all = dao.observeSongs().first()
        if (all.size < 2) return

        val toDelete = ArrayList<String>()
        all.groupBy { looseTitle(it.title) }
            .filterValues { it.size > 1 }
            .values
            .forEach { list ->
                val base = looseTitle(list.first().title)
                // 只有名字就是 base 本身（没带括号副标题）的那条才算「纯版本」
                val plain = list.filter { looseTitleEquals(it.title, base) }
                if (plain.isEmpty()) return@forEach
                val keep = plain.maxByOrNull { it.perfCount } ?: plain.first()
                list.filter { it.id != keep.id }.forEach { toDelete.add(it.id) }
            }

        if (toDelete.isEmpty()) return
        android.util.Log.i("VMapDedupe", "同曲异名删除 ${toDelete.size} 条: $toDelete")
        toDelete.forEach { dao.deleteSong(it) }
    }

    /** 去掉括号副标题、空格、首尾符号后的歌名，用来认「同一首歌」。 */
    private fun looseTitle(t: String): String {
        var s = t.lowercase()
        s = Regex("[（(\\[【].*?[）)\\]】]").replace(s, "")
        s = s.replace(Regex("\\s+"), "")
        s = s.replace(Regex("^[-·—~]+|[-·—~]+$"), "")
        return s.trim()
    }

    /** 原标题是否「就是这个名字」，不带任何副标题。 */
    private fun looseTitleEquals(raw: String, loose: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.contains('(') || trimmed.contains('（') ||
            trimmed.contains('[') || trimmed.contains('【')
        ) {
            return false
        }
        return looseTitle(trimmed) == loose
    }

    /**
     * 把内置的历年演唱会曲目（网易云曲库口径）同步进曲库。
     *
     * 每次内置档案换代（KEY_SEEDED 换版本号）跑一遍：
     * - 新歌按 externalId 插入，并按「在历次演唱会上唱过几次」给初始档位；
     * - 老歌只刷新年份 / 系列 / 出场次数这些场次信息，用户自己拖过的档位和顺序保持不动。
     */
    private suspend fun seedArchive() {
        val prefs = context.getSharedPreferences("vmap_seed", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        val archive = com.vmap.app.data.song.SongArchive.load(context)
        if (archive.isEmpty()) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return
        }

        val existingSongs = dao.observeSongs().first()
        val byExternal = existingSongs.filter { it.externalId.isNotBlank() }.associateBy { it.externalId }

        val refreshed = ArrayList<Song>()
        val incoming = ArrayList<com.vmap.app.data.song.ArchivedSong>()
        archive.forEach { a ->
            val old = byExternal[a.id]
            if (old == null) {
                incoming.add(a)
            } else {
                // 档案换代时把封面也一起换掉：音乐平台（网易云）专辑图优先，没有再退回 B 站视频封面。
                // 只在版本号变化的那一次跑（外层 prefs 门闸），平时启动不会碰用户自己的封面。
                val asset = a.coverAsset.ifBlank { a.biliCoverAsset }
                val cover = if (asset.isNotBlank()) {
                    coverStore.importAsset(asset, old.id, force = true) ?: old.coverPath
                } else {
                    old.coverPath
                }
                refreshed.add(
                    old.copy(
                        years = a.years,
                        tour = a.tour,
                        perfCount = a.perfCount,
                        album = a.album,
                        series = a.series,
                        coverPath = cover,
                        linkUrl = a.linkUrl,
                        linkLabel = a.linkLabel,
                        linkAuthor = a.linkAuthor,
                        linkPlay = a.linkPlay,
                    ),
                )
            }
        }

        val ratings = dao.observeRatings().first().sortedBy { it.orderIndex }
        if (ratings.isEmpty()) return
        // 出场次数 → 初始档位：唱过 6 场以上的进 S，3 场以上 A，2 场以上 B，其余 C
        fun tierOf(perf: Int): SongRating = when {
            perf >= 6 -> ratings[0]
            perf >= 3 -> ratings.getOrNull(1) ?: ratings[0]
            perf >= 2 -> ratings.getOrNull(2) ?: ratings.last()
            else -> ratings.last()
        }

        val perTierCount = HashMap<String, Int>()
        val inserted = ArrayList<Song>(incoming.size)
        incoming.sortedByDescending { it.perfCount }.forEach { a ->
            val tier = tierOf(a.perfCount)
            val idx = perTierCount[tier.id] ?: 0
            perTierCount[tier.id] = idx + 1
            val songId = "arc-${a.id}"
            // 曲绘优先用音乐平台（网易云）专辑图，没有再退回 B 站视频封面
            val cover = coverStore.importAsset(a.coverAsset.ifBlank { a.biliCoverAsset }, songId).orEmpty()
            inserted.add(
                Song(
                    id = songId,
                    title = a.title,
                    artist = a.artist,
                    plays = 0,
                    ratingId = tier.id,
                    orderIndex = idx,
                    source = "netease",
                    externalId = a.id,
                    coverPath = cover,
                    series = a.series,
                    tour = a.tour,
                    years = a.years,
                    perfCount = a.perfCount,
                    album = a.album,
                    linkUrl = a.linkUrl,
                    linkLabel = a.linkLabel,
                    linkAuthor = a.linkAuthor,
                    linkPlay = a.linkPlay,
                ),
            )
        }
        val rows = refreshed + inserted
        if (rows.isNotEmpty()) dao.upsertSongs(rows)
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    /**
     * 曲库去重：歌名 + 主唱相同的就是同一首歌（网易云不同专辑/不同版本会拆成两条）。
     *
     * 保留信息最全的那条（有曲绘 > 出场次数多 > 有 B 站链接），
     * 年份 / 系列 / 场次取并集，把重复的那几条删掉。每次启动跑一遍，幂等。
     */
    private suspend fun dedupeSongs() {
        val all = dao.observeSongs().first()
        if (all.size < 2) return

        fun keyOf(s: Song): String = normKey(s.title) + "|" + normKey(s.artist.split(',', '、', '&', '/').first())

        val groups = all.groupBy { keyOf(it) }.filter { it.value.size > 1 }
        if (groups.isEmpty()) return

        val removed = ArrayList<Song>()
        val merged = ArrayList<Song>()
        groups.values.forEach { list ->
            val keeper = list.maxWithOrNull(
                compareBy<Song> { if (it.coverPath.isBlank()) 0 else 1 }
                    .thenBy { it.perfCount }
                    .thenBy { if (it.linkUrl.isBlank()) 0 else 1 },
            ) ?: return@forEach
            val years = list.asSequence()
                .flatMap { it.years.split(',').asSequence() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.toIntOrNull() ?: 0 }
                .joinToString(",")
            val tours = list.asSequence()
                .flatMap { it.tour.split(',').asSequence() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(",")
            val richest = list.maxByOrNull { it.perfCount } ?: keeper
            // 链接也按「官方 > 播放最高 > 非官方」挑，不光是有没有链接
            val bestLink = list.filter { it.linkUrl.isNotBlank() }
                .maxWithOrNull(
                    compareBy<Song> { if (it.linkLabel.contains("官方")) 1 else 0 }
                        .thenBy { it.linkPlay },
                )
            merged.add(
                keeper.copy(
                    years = years,
                    tour = tours,
                    perfCount = richest.perfCount,
                    album = keeper.album.ifBlank { richest.album },
                    coverPath = keeper.coverPath,
                    linkUrl = bestLink?.linkUrl.orEmpty(),
                    linkLabel = bestLink?.linkLabel.orEmpty(),
                    linkAuthor = bestLink?.linkAuthor.orEmpty(),
                    linkPlay = bestLink?.linkPlay ?: 0,
                ),
            )
            list.filter { it.id != keeper.id }.forEach { removed.add(it) }
        }
        if (merged.isNotEmpty()) dao.upsertSongs(merged)
        removed.forEach { dao.deleteSong(it.id) }
    }

    /** 去重用的归一化：去空白、统一全角括号、刮掉版本后缀（Live / 伴奏 / Remix …）、忽略大小写。 */
    private fun normKey(raw: String): String {
        var s = raw
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace("\u3000", " ")
            .lowercase()
            .trim()
        s = s.replace(
            Regex("\\([^)]{0,24}(live|inst|instrumental|off ?vocal|伴奏|翻自|cover|remix|重置|完整版|官方版|音源|ver\\.?)[^)]{0,14}\\)\\s*$"),
            "",
        )
        s = s.replace(Regex("\\[[^\\]]{0,24}(live|inst|伴奏|翻自|cover|remix|重置|完整版|官方版|音源)[^\\]]{0,14}\\]\\s*$"), "")
        s = s.replace(Regex("\\s*-\\s*(live|inst|instrumental|伴奏|remix|off ?vocal).*$"), "")
        s = s.replace(Regex("[\\s\\-_・·.,，。!！?？:：;；'\"“”‘’]"), "")
        s = s.replace("official", "").replace("公式", "")
        return s.trim()
    }

    fun addRating(name: String, color: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val next = (ratings.value.maxOfOrNull { it.orderIndex } ?: -1) + 1
            dao.upsertRating(SongRating(name = name.trim(), color = color, orderIndex = next))
        }
    }

    fun changeRatingColor(rating: SongRating, color: Long) {
        viewModelScope.launch { dao.upsertRating(rating.copy(color = color)) }
    }

    /** 删评级会连带删掉该评级下的歌，界面上要二次确认。 */
    fun deleteRating(rating: SongRating) {
        viewModelScope.launch {
            dao.deleteSongsOfRating(rating.id)
            dao.deleteRating(rating.id)
        }
    }

    fun addSong(
        title: String,
        artist: String,
        plays: Int,
        ratingId: String,
        coverUri: Uri? = null,
    ) {
        if (title.isBlank() || ratingId.isBlank()) return
        viewModelScope.launch {
            val next = (dao.maxOrderOf(ratingId) ?: -1) + 1
            val song = Song(
                title = title.trim(),
                artist = artist.trim(),
                plays = plays.coerceAtLeast(0),
                ratingId = ratingId,
                orderIndex = next,
            )
            val cover = coverUri?.let { coverStore.import(it, song.id) }.orEmpty()
            dao.upsertSong(song.copy(coverPath = cover))
        }
    }

    /** 编辑：换封面、删封面、或者什么都不动，都在这里收口。 */
    fun updateSong(
        song: Song,
        title: String,
        artist: String,
        plays: Int,
        ratingId: String,
        coverUri: Uri? = null,
        removeCover: Boolean = false,
    ) {
        if (title.isBlank() || ratingId.isBlank()) return
        viewModelScope.launch {
            val coverPath = when {
                coverUri != null -> coverStore.import(coverUri, song.id) ?: song.coverPath
                removeCover -> {
                    coverStore.delete(song.coverPath)
                    ""
                }

                else -> song.coverPath
            }
            dao.upsertSong(
                song.copy(
                    title = title.trim(),
                    artist = artist.trim(),
                    plays = plays.coerceAtLeast(0),
                    ratingId = ratingId,
                    coverPath = coverPath,
                ),
            )
        }
    }

    /** 卡片上的封面：解码放到 IO 线程，由界面侧 produceState 调。 */
    suspend fun loadCover(coverPath: String): ImageBitmap? = coverStore.load(coverPath)

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            coverStore.delete(song.coverPath)
            dao.deleteSong(song.id)
        }
    }

    /**
     * 把一首歌挪到 [targetRatingId] 的第 [targetIndex] 位。
     * 组内重新编号，跨组时原组留下的空号不影响排序（仍按数值升序）。
     */
    fun moveSong(songId: String, targetRatingId: String, targetIndex: Int) {
        viewModelScope.launch {
            val moving = songs.value.firstOrNull { it.id == songId } ?: return@launch
            val target = dao.songsOf(targetRatingId)
                .filter { it.id != songId }
                .toMutableList()
            val index = targetIndex.coerceIn(0, target.size)
            target.add(index, moving.copy(ratingId = targetRatingId))
            dao.upsertSongs(
                target.mapIndexed { i, s -> s.copy(ratingId = targetRatingId, orderIndex = i) },
            )
        }
    }

    /**
     * 批量导入：一行一首，支持
     * `歌名` / `歌名 - 歌手` / `歌名 - 歌手 - 1234`，
     * 也认 Tab、逗号分隔（网易云/QQ 音乐复制出来的列表常见这几种）。
     */
    fun importSongText(text: String, targetRatingId: String): ImportResult {
        val lines = text.lineSequence().mapNotNull { parseLine(it) }.toList()
        if (lines.isEmpty()) return ImportResult(0, 0)

        val existing = songs.value.map { key(it.title, it.artist) }.toMutableSet()
        val toInsert = ArrayList<Song>()
        var skipped = 0
        lines.forEach { (title, artist, plays) ->
            val k = key(title, artist)
            if (!existing.add(k)) {
                skipped++
                return@forEach
            }
            toInsert.add(
                Song(
                    title = title,
                    artist = artist,
                    plays = plays,
                    ratingId = targetRatingId,
                    source = "import",
                ),
            )
        }

        if (toInsert.isNotEmpty()) {
            viewModelScope.launch {
                val base = (dao.maxOrderOf(targetRatingId) ?: -1) + 1
                dao.upsertSongs(
                    toInsert.mapIndexed { i, s ->
                        s.copy(orderIndex = base + i)
                    },
                )
            }
        }
        return ImportResult(toInsert.size, skipped)
    }

    private fun key(title: String, artist: String) = "${title.trim()}|${artist.trim()}".lowercase()

    private companion object {
        // v6：B 站视频封面补齐后重新落一次盘。版本号变化那一次会用档案里的封面覆盖一遍
        // （包括之前手动换过的），之后启动不再动。
        const val KEY_SEEDED = "songs_seeded_v8"

        /** 默认五档，从高到低。颜色按参考的 tier list 走：红 → 橙 → 黄 → 黄绿 → 绿。 */
        val DEFAULT_RATINGS = listOf(
            SongRating(name = "神", color = 0xFFF5696B, orderIndex = 0),
            SongRating(name = "夯", color = 0xFFF7A24B, orderIndex = 1),
            SongRating(name = "顶", color = 0xFFF3D34D, orderIndex = 2),
            SongRating(name = "赞", color = 0xFFC4DD58, orderIndex = 3),
            SongRating(name = "稳", color = 0xFF8FD36A, orderIndex = 4),
        )

        /** 默认档位是否已经从 S/A/B/C 迁到 神/夯/顶/赞/稳。 */
        const val KEY_RATINGS_V2 = "ratings_v2"

        /** 第四档从「赞」改叫「香」、五档配色换成 tier list 的暖色阶，只跑一次。 */
        const val KEY_RATINGS_V3 = "ratings_v3"

        /** 第四档从「香」改回「赞」，只跑一次（用户最终确认叫「赞」）。 */
        const val KEY_RATINGS_V4 = "ratings_v4"
    }

    private fun parseLine(raw: String): Triple<String, String, Int>? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        // 去掉行首的序号，如 "1. " 或 "01 "
        val cleaned = line.replace(Regex("^\\d{1,3}[.、)]\\s*"), "")

        val parts = when {
            cleaned.contains('\t') -> cleaned.split('\t')
            cleaned.contains(',') -> cleaned.split(',')
            cleaned.contains('，') -> cleaned.split('，')
            cleaned.contains(" - ") -> cleaned.split(" - ")
            cleaned.contains(" – ") -> cleaned.split(" – ")
            else -> listOf(cleaned)
        }.map { it.trim() }.filter { it.isNotEmpty() }

        val title = parts.firstOrNull() ?: return null
        val artist = parts.getOrNull(1) ?: ""
        val plays = parts.getOrNull(2)
            ?.let { it.replace(Regex("[^0-9]"), "") }
            ?.toIntOrNull() ?: 0
        return Triple(title, artist, plays)
    }
}
