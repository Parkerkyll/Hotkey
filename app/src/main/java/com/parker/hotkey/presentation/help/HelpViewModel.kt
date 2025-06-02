package com.parker.hotkey.presentation.help

import com.parker.hotkey.R
import com.parker.hotkey.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 도움말 항목 상태를 나타내는 데이터 클래스
 */
data class HelpState(
    val isLoading: Boolean = false,
    val helpItems: List<HelpItem> = emptyList(),
    val error: String? = null
)

/**
 * 도움말 화면에서 발생할 수 있는 이벤트를 정의하는 sealed 클래스
 */
sealed class HelpEvent {
    data class ShowToast(val message: String) : HelpEvent()
    data class NavigateToDetail(val itemId: Int) : HelpEvent()
}

/**
 * 사용법 화면의 ViewModel
 */
@HiltViewModel
class HelpViewModel @Inject constructor() : BaseViewModel<HelpState, HelpEvent>() {
    
    init {
        loadHelpItems()
    }
    
    /**
     * 초기 상태를 생성합니다.
     */
    override fun createInitialState(): HelpState = HelpState()
    
    /**
     * 도움말 아이템을 초기화합니다.
     */
    private fun loadHelpItems() {
        launchWithErrorHandling({
            setState { copy(isLoading = true) }
            
            // 이미지 리소스 ID만 포함한 아이템 목록
            val items = listOf(
                HelpItem(
                    id = 0,
                    imagePlaceholderId = R.drawable.help_mode_basic
                ),
                HelpItem(
                    id = 1,
                    imagePlaceholderId = R.drawable.help_mode_change
                ),
                HelpItem(
                    id = 2,
                    imagePlaceholderId = R.drawable.help_mode_maker
                ),
                HelpItem(
                    id = 3,
                    imagePlaceholderId = R.drawable.help_mode_makerandmeno
                )
            )
            
            setState { copy(isLoading = false, helpItems = items) }
            Timber.d("도움말 아이템 로드 완료: ${items.size}개")
        }, {
            setState { copy(isLoading = false, error = it.message) }
            launchEvent(HelpEvent.ShowToast("도움말 로드 실패: ${it.message}"))
            Timber.e(it, "도움말 아이템 로드 실패")
        })
    }
    
    /**
     * 도움말 항목 클릭 이벤트 처리
     */
    fun onHelpItemClicked(itemId: Int) {
        Timber.d("도움말 항목 클릭됨: ID=$itemId")
        launchEvent(HelpEvent.NavigateToDetail(itemId))
    }
}

/**
 * 도움말 항목 데이터 클래스 (간소화됨)
 */
data class HelpItem(
    val id: Int,
    val imagePlaceholderId: Int? = null
) 