package com.vmap.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vmap.app.domain.model.Board
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.model.Tag
import com.vmap.app.domain.repository.SectionStats
import com.vmap.app.domain.repository.SpotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 路线图层：官方路线（档案里开过的场次）与我的路线（我真去过的）分开画。 */
enum class RouteMode(val label: String) {
    OFF("路线关"),
    OFFICIAL("官方路线"),
    MINE("我的路线"),
    BOTH("双线"),
    ;

    val showOfficial: Boolean get() = this == OFFICIAL || this == BOTH
    val showMine: Boolean get() = this == MINE || this == BOTH
}

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val repository: SpotRepository,
) : ViewModel() {

    private val _section = MutableStateFlow(Section.TIANYI)
    val section: StateFlow<Section> = _section.asStateFlow()

    /** 视图范围：单板块或综合。综合模式下 [section] 仍保留上次选择，用于新建打卡的归属。 */
    private val _board = MutableStateFlow(Board.TIANYI)
    val board: StateFlow<Board> = _board.asStateFlow()

    /** 当前筛选的标签 id，null 表示不过滤。 */
    private val _filterTag = MutableStateFlow<String?>(null)
    val filterTag: StateFlow<String?> = _filterTag.asStateFlow()

    private val _routeMode = MutableStateFlow(RouteMode.BOTH)
    val routeMode: StateFlow<RouteMode> = _routeMode.asStateFlow()

    private val _isDark = MutableStateFlow(true)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    /** 年份分层：按演出年份深浅着色，越早越淡。默认开。 */
    private val _yearLayers = MutableStateFlow(true)
    val yearLayers: StateFlow<Boolean> = _yearLayers.asStateFlow()

    /** 只看某一年，null 表示全年份。 */
    private val _focusYear = MutableStateFlow<Int?>(null)
    val focusYear: StateFlow<Int?> = _focusYear.asStateFlow()

    private val query = combine(_board, _section, _filterTag) { b, s, t -> Triple(b, s, t) }

    /** 按时间倒序，用于地图打点与列表。综合模式取全部板块。 */
    val spots: StateFlow<List<Spot>> = query
        .flatMapLatest { (b, s, t) ->
            when {
                b == Board.ALL -> repository.observeAllSpots()
                t == null -> repository.observeSpots(s)
                else -> repository.observeSpotsByTag(s, t)
            }
        }
        .map { dedupeSpots(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按时间正序，用于连线与箭头方向。 */
    val chronological: StateFlow<List<Spot>> = query
        .flatMapLatest { (b, s, t) ->
            when {
                b == Board.ALL -> repository.observeAllSpots()
                    .map { list -> list.sortedBy { it.performedAt } }
                t == null -> repository.observeChronological(s)
                else -> repository.observeChronologicalByTag(s, t)
            }
        }
        .map { dedupeSpots(it).sortedBy { spot -> spot.performedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 我的路线：只取「我去过」的点，按时间正序连线；官方档案点不进来。 */
    val myRoute: StateFlow<List<Spot>> = spots
        .map { list ->
            list.filter { it.mine && it.adcode.isNotBlank() }
                .sortedBy { it.performedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 统计从 spots 聚合，筛选后自然跟随。线上活动没有城市，不计入城市数。 */
    val stats: StateFlow<SectionStats> = spots
        .map { list ->
            SectionStats(
                cityCount = list.map { it.adcode }.filter { it.isNotBlank() }.distinct().size,
                spotCount = list.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionStats())

    val tags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部打卡（不分板块），成就/年度回顾用。 */
    val allSpots: StateFlow<List<Spot>> = repository.observeAllSpots()
        .map { dedupeSpots(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleSection() {
        _board.value = Board.of(_section.value.other)
        _section.value = _section.value.other
    }

    fun selectSection(target: Section) {
        _section.value = target
    }

    /** 切换视图范围（单板块 / 综合）。综合模式下保留上次的归属板块。 */
    fun selectBoard(target: Board) {
        if (_board.value == target) return
        _board.value = target
        target.section?.let { _section.value = it }
    }

    fun setFilterTag(tagId: String?) {
        _filterTag.value = tagId
    }

    fun toggleRoute() {
        _routeMode.value = when (_routeMode.value) {
            RouteMode.OFF -> RouteMode.OFFICIAL
            RouteMode.OFFICIAL -> RouteMode.MINE
            RouteMode.MINE -> RouteMode.BOTH
            RouteMode.BOTH -> RouteMode.OFF
        }
    }

    fun setRouteMode(mode: RouteMode) {
        _routeMode.value = mode
    }

    fun toggleTheme() {
        _isDark.value = !_isDark.value
    }

    fun toggleYearLayers() {
        _yearLayers.value = !_yearLayers.value
        if (!_yearLayers.value) _focusYear.value = null
    }

    fun setFocusYear(year: Int?) {
        _focusYear.value = if (_focusYear.value == year) null else year
        if (_focusYear.value != null) _yearLayers.value = true
    }

    /** 直接聚焦某一年（点年份时用，不toggle，重复点同一年也会重播动画）。 */
    fun selectYear(year: Int?) {
        _focusYear.value = year
        if (year != null) _yearLayers.value = true
    }

    suspend fun delete(spotId: String) {
        repository.softDelete(spotId)
    }

    /** 档案页「记入足迹」用。 */
    suspend fun save(spot: Spot) {
        repository.save(spot)
    }

    /**
     * 标记「这个地方我去过 / 没去过」。官方档案导入的点默认不算我的行程，
     * 从往音簿或者地图上的「我的行程」勾一下，才会进我自己的路线。
     */
    fun setMine(spotId: String, mine: Boolean) {
        val spot = allSpots.value.firstOrNull { it.id == spotId } ?: return
        if (spot.mine == mine) return
        viewModelScope.launch { repository.save(spot.copy(mine = mine)) }
    }

    /** 直接按档案场次打卡（「快速添加已确认的官方演出」）。 */
    fun markArchive(event: com.vmap.app.data.archive.ArchiveEvent, mine: Boolean = true) {
        viewModelScope.launch {
            val existing = allSpots.value.firstOrNull { it.id == "arc-${event.id}" }
            when {
                existing != null -> if (existing.mine != mine) {
                    repository.save(existing.copy(mine = mine))
                }

                mine -> repository.saveIfAbsent(event.toSpot().copy(mine = true))
            }
        }
    }

    /**
     * 历年演出档案自动导入：首次启动时把档案里每一场演出写进打卡记录，
     * 地图上每年的演出路径就直接有了，不需要逐条手动添加。
     * 用 id 前缀 arc- 保证幂等，只导入一次；导入的点不算「我的行程」。
     */
    fun autoImportArchive(context: android.content.Context) {
        val prefs = context.getSharedPreferences("vmap_seed", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ARCHIVE_SEEDED, false)) return
        viewModelScope.launch {
            val events = com.vmap.app.data.archive.ConcertArchive.load(context)
            // 档案本身按「板块 + 城市 + 日期」去重，避免同一场演出换了个 id 又进来一遍
            val seen = repository.observeAllSpots().first()
                .map { dedupeKey(it) }
                .toMutableSet()
            events
                .distinctBy { "${it.section}|${it.adcode.ifBlank { it.city }}|${it.dateLabel}" }
                .forEach { event ->
                    val spot = event.toSpot()
                    if (seen.add(dedupeKey(spot))) repository.saveIfAbsent(spot)
                }
            prefs.edit().putBoolean(KEY_ARCHIVE_SEEDED, true).apply()
        }
    }

    /**
     * 演出去重：同一板块、同一座城、同一天只留一条。
     * 历史版本自动导入过两遍、或者手动点过一次再被档案导入，都会留下两条一模一样的记录；
     * 这里在读取时合并（不去动数据库），只要有一条标了「去过」就算我的。
     */
    private fun dedupeSpots(list: List<Spot>): List<Spot> {
        if (list.size < 2) return list
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val grouped = LinkedHashMap<String, Spot>()
        list.forEach { s ->
            val key = "${s.section}|${s.adcode.ifBlank { s.cityName }}|${day.format(java.util.Date(s.performedAt))}"
            val old = grouped[key]
            if (old == null) {
                grouped[key] = s
            } else {
                val winner = when {
                    s.mine && !old.mine -> s
                    !s.mine && old.mine -> old
                    s.id.startsWith("arc-") && !old.id.startsWith("arc-") -> s
                    else -> old
                }
                grouped[key] = winner.copy(mine = old.mine || s.mine)
            }
        }
        return grouped.values.toList()
    }

    /** 演出判重键：同一板块、同一座城、同一天，就是同一场。 */
    private fun dedupeKey(spot: Spot): String {
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date(spot.performedAt))
        return "${spot.section}|${spot.adcode.ifBlank { spot.cityName }}|$day"
    }

    private companion object {
        const val KEY_ARCHIVE_SEEDED = "archive_seeded_v2"
    }
}
