package com.parker.hotkey.presentation.notice

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parker.hotkey.data.model.Notice
import com.parker.hotkey.domain.repository.NoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoticeViewModel @Inject constructor(
    private val noticeRepository: NoticeRepository
) : ViewModel() {
    
    private val _noticeList = MutableLiveData<List<Notice>>()
    val noticeList: LiveData<List<Notice>> = _noticeList
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    private val _selectedNotice = MutableLiveData<Notice?>()
    val selectedNotice: LiveData<Notice?> = _selectedNotice
    
    init {
        loadNotices()
    }
    
    fun loadNotices() {
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            _isLoading.postValue(true)
            
            try {
                noticeRepository.getNotices()
                    .onSuccess { notices ->
                        _noticeList.postValue(notices)
                        _isLoading.postValue(false)
                    }
                    .onFailure { error ->
                        if (error !is CancellationException) {
                            _errorMessage.postValue(error.message ?: "공지사항을 불러오는 중 오류가 발생했습니다.")
                        }
                        _isLoading.postValue(false)
                    }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _errorMessage.postValue(e.message ?: "공지사항을 불러오는 중 오류가 발생했습니다.")
                }
                _isLoading.postValue(false)
            }
        }
    }
    
    fun loadNoticeDetail(noticeId: String) {
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            _isLoading.postValue(true)
            
            try {
                noticeRepository.getNoticeById(noticeId)
                    .onSuccess { notice ->
                        _selectedNotice.postValue(notice)
                        _isLoading.postValue(false)
                    }
                    .onFailure { error ->
                        if (error !is CancellationException) {
                            _errorMessage.postValue(error.message ?: "공지사항 상세 정보를 불러오는 중 오류가 발생했습니다.")
                        }
                        _isLoading.postValue(false)
                    }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _errorMessage.postValue(e.message ?: "공지사항 상세 정보를 불러오는 중 오류가 발생했습니다.")
                }
                _isLoading.postValue(false)
            }
        }
    }
    
    fun clearSelectedNotice() {
        _selectedNotice.value = null
    }
} 