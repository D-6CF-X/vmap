package com.vmap.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val repository: SpotRepository,
) : ViewModel() {

    private val _section = MutableStateFlow(Section.TIANYI)
    val section: StateFlow<Section> = _section.asStateFlow()

    /** 当前筛选的标签 id，null 表示不过滤。 */
    private val _filterTag = MutableStateFlow<String?>(null)
    val filterTag: StateFlow<String?> = _filterTag.asStateFlow()

    private val _showRoute = MutableStateFlow(true)
    val showRoute: StateFlow<Boolean> = _showRoute.asStateFlow()

    private val _isDark = MutableStateFlow(true)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    private val query = combine(_section, _filterTag) { s, t -> s to t }

    /** 按时间倒序，用于地图打点与列表。 */
    val spots: StateFlow<List<Spot>> = query
        .flatMapLatest { (s, t) ->
            if (t == null) repository.observeSpots(s) else repository.observeSpotsByTag(s, t)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按时间正序，用于连线与箭头方向。 */
    val chronological: StateFlow<List<Spot>> = query
        .flatMapLatest { (s, t) ->
            if (t == null) repository.observeChronological(s) else repository.observeChronologicalByTag(s, t)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 统计从 spots 聚合，筛选后自然跟随。 */
    val stats: StateFlow<SectionStats> = spots
        .map { list ->
            SectionStats(
                cityCount = list.map { it.adcode }.distinct().size,
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
        _section.value = _section.value.other
    }

    fun selectSection(target: Section) {
        _section.value = target
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

    suspend fun delete(spotId: String) {
        repository.softDelete(spotId)
    }
}
