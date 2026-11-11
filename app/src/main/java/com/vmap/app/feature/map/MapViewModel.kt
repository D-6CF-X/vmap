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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _showRoute = MutableStateFlow(true)
    val showRoute: StateFlow<Boolean> = _showRoute.asStateFlow()

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
        _showRoute.value = !_showRoute.value
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
     * 历年演出档案自动导入：首次启动时把档案里每一场演出写进打卡记录，
     * 地图上每年的演出路径就直接有了，不需要逐条手动添加。
     * 用 id 前缀 arc- 保证幂等，只导入一次。
     */
    fun autoImportArchive(context: android.content.Context) {
        val prefs = context.getSharedPreferences("vmap_seed", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ARCHIVE_SEEDED, false)) return
        viewModelScope.launch {
            val events = com.vmap.app.data.archive.ConcertArchive.load(context)
            events.forEach { repository.saveIfAbsent(it.toSpot()) }
            prefs.edit().putBoolean(KEY_ARCHIVE_SEEDED, true).apply()
        }
    }

    private companion object {
        const val KEY_ARCHIVE_SEEDED = "archive_seeded_v1"
    }
}
