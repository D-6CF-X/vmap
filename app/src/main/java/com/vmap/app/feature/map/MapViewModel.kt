package com.vmap.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.repository.SectionStats
import com.vmap.app.domain.repository.SpotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val repository: SpotRepository,
) : ViewModel() {

    private val _section = MutableStateFlow(Section.TIANYI)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _showRoute = MutableStateFlow(true)
    val showRoute: StateFlow<Boolean> = _showRoute.asStateFlow()

    /** 按时间倒序，用于地图打点与列表。 */
    val spots: StateFlow<List<Spot>> = _section
        .flatMapLatest { repository.observeSpots(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 按时间正序，用于连线与箭头方向。 */
    val chronological: StateFlow<List<Spot>> = _section
        .flatMapLatest { repository.observeChronological(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<SectionStats> = _section
        .flatMapLatest { repository.observeStats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionStats())

    fun toggleSection() {
        _section.value = _section.value.other
    }

    fun selectSection(target: Section) {
        _section.value = target
    }

    fun toggleRoute() {
        _showRoute.value = !_showRoute.value
    }

    suspend fun delete(spotId: String) {
        repository.softDelete(spotId)
    }
}
