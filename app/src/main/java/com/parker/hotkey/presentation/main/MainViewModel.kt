package com.parker.hotkey.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {
    
    // 네비게이션 관련 상태를 관리하는 StateFlow
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Notice)
    val navigationState: StateFlow<NavigationState> = _navigationState
    
    // 드로어 상태 관리
    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen
    
    // 네비게이션 이벤트 (일회성 이벤트)
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent
    
    /**
     * 네비게이션 아이템 선택 처리
     */
    fun onNavigationItemSelected(itemId: Int): Boolean {
        return when (itemId) {
            R.id.nav_notice -> {
                Timber.d("공지사항 메뉴 클릭")
                _navigationState.value = NavigationState.Notice
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateToNotice)
                }
                true
            }
            R.id.nav_profile -> {
                Timber.d("내 정보 메뉴 클릭")
                _navigationState.value = NavigationState.Profile
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateToProfile)
                }
                true
            }
            R.id.nav_help -> {
                Timber.d("사용법 메뉴 클릭")
                _navigationState.value = NavigationState.Help
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateToHelp)
                }
                true
            }
            R.id.nav_report_bug -> {
                Timber.d("버그제보하기 메뉴 클릭")
                _navigationState.value = NavigationState.ReportBug
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateToReportBug)
                }
                true
            }
            R.id.mapFragment -> {
                Timber.d("지도로 돌아가기 메뉴 클릭")
                navigateToMap()
                true
            }
            else -> false
        }
    }
    
    /**
     * 지도로 이동하는 메서드
     * 모든 화면에서 이 메서드를 호출하여 일관된 방식으로 로딩 화면과 함께 지도로 이동할 수 있습니다.
     * 
     * 사용 예시:
     * - 메뉴 버튼에서: viewModel.navigateToMap()
     * - 돌아가기 버튼에서: viewModel.navigateToMap()
     * - 화면 내 기능에서: viewModel.navigateToMap()
     */
    fun navigateToMap() {
        _navigationState.value = NavigationState.Map
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToMap)
        }
    }
    
    /**
     * 드로어 열기
     */
    fun openDrawer() {
        _isDrawerOpen.value = true
    }
    
    /**
     * 드로어 닫기
     */
    fun closeDrawer() {
        _isDrawerOpen.value = false
    }
    
    /**
     * 뒤로가기 처리
     * @return 처리되었으면 true, 아니면 false
     */
    fun onBackPressed(): Boolean {
        return if (isDrawerOpen.value) {
            closeDrawer()
            true
        } else {
            false
        }
    }
    
    /**
     * 네비게이션 상태를 나타내는 sealed class
     */
    sealed class NavigationState {
        object Notice : NavigationState()
        object Help : NavigationState()
        object Profile : NavigationState()
        object ReportBug : NavigationState()
        object Map : NavigationState()
    }
    
    /**
     * 네비게이션 이벤트를 나타내는 sealed class (일회성 이벤트)
     */
    sealed class NavigationEvent {
        object NavigateToNotice : NavigationEvent()
        object NavigateToHelp : NavigationEvent()
        object NavigateToProfile : NavigationEvent()
        object NavigateToReportBug : NavigationEvent()
        object NavigateToMap : NavigationEvent()
    }
    
    override fun onCleared() {
        super.onCleared()
        Timber.d("MainViewModel 정리됨")
    }
} 