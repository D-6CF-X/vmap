package com.vmap.app.feature.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vmap.app.data.db.SongDao
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
class SongBoardViewModel(private val dao: SongDao) : ViewModel() {

    val ratings: StateFlow<List<SongRating>> = dao.observeRatings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs: StateFlow<List<Song>> = dao.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { ensureDefaults() }
    }

    /** 首次进入给一套默认档位，用户可以直接用，也可以全删了重建。 */
    private suspend fun ensureDefaults() {
        if (dao.ratingCount() > 0) return
        dao.upsertRatings(
            listOf(
                SongRating(name = "S", color = 0xFFFF6B8B, orderIndex = 0),
                SongRating(name = "A", color = 0xFFFFB454, orderIndex = 1),
                SongRating(name = "B", color = 0xFF66CCFF, orderIndex = 2),
                SongRating(name = "C", color = 0xFF8E9BAA, orderIndex = 3),
            ),
        )
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

    fun addSong(title: String, artist: String, plays: Int, ratingId: String) {
        if (title.isBlank() || ratingId.isBlank()) return
        viewModelScope.launch {
            val next = (dao.maxOrderOf(ratingId) ?: -1) + 1
            dao.upsertSong(
                Song(
                    title = title.trim(),
                    artist = artist.trim(),
                    plays = plays.coerceAtLeast(0),
                    ratingId = ratingId,
                    orderIndex = next,
                ),
            )
        }
    }

    fun updateSong(song: Song) {
        viewModelScope.launch { dao.upsertSong(song) }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch { dao.deleteSong(song.id) }
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
