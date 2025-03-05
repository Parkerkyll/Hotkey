package com.parker.hotkey.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoListViewModel @Inject constructor() : ViewModel() {
    
    private val _mapZoomLevel = MutableStateFlow(17.0)
    val mapZoomLevel: StateFlow<Double> = _mapZoomLevel

    fun returnToMapView() {
        viewModelScope.launch {
            _mapZoomLevel.emit(17.0)
        }
    }
} 