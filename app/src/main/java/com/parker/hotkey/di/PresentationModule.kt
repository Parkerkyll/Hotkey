package com.parker.hotkey.di

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.parker.hotkey.domain.manager.EditModeManager
import com.parker.hotkey.domain.manager.MemoManager
import com.parker.hotkey.domain.repository.AuthRepository
import com.parker.hotkey.presentation.memo.MemoInteractor
import com.parker.hotkey.presentation.memo.MemoViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.FragmentScoped
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * 뷰모델 관련 의존성을 제공하는 모듈
 * MapModule 분리 후 남은 일반적인 뷰모델 의존성만 제공
 */
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    /**
     * MapViewModel에서 사용하는 임시 마커 기능 플래그 제공 (한정자 없음)
     */
    @Provides
    fun provideUseTemporaryMarkerFeature(): Boolean {
        // 임시 마커 기능 활성화
        return true
    }
}

/**
 * Fragment 범위 프레젠테이션 의존성 제공 모듈
 */
@Module
@InstallIn(FragmentComponent::class)
object PresentationFragmentModule {
    
    /**
     * MemoViewModel 의존성 제공
     */
    @Provides
    @FragmentScoped
    fun provideMemoViewModel(
        memoInteractor: MemoInteractor,
        editModeManager: EditModeManager,
        memoManager: MemoManager,
        authRepository: AuthRepository
    ): MemoViewModel {
        return MemoViewModel(
            memoInteractor,
            editModeManager,
            memoManager,
            authRepository
        )
    }
} 