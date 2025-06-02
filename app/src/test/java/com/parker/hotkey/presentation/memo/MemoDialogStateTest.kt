package com.parker.hotkey.presentation.memo

import com.parker.hotkey.domain.model.state.DialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DialogState 통합 테스트
 */
class MemoDialogStateTest {
    
    @Test
    fun `DialogState 기본 생성자 테스트`() {
        val state = DialogState()
        
        assertFalse(state.isVisible)
        assertNull(state.markerId)
        assertFalse(state.isTemporary)
    }
    
    @Test
    fun `DialogState 매개변수 생성자 테스트`() {
        val markerId = "marker123"
        val state = DialogState(
            isVisible = true,
            markerId = markerId,
            isTemporary = true
        )
        
        assertTrue(state.isVisible)
        assertEquals(markerId, state.markerId)
        assertTrue(state.isTemporary)
    }
    
    @Test
    fun `DialogState copy 메서드 테스트`() {
        val markerId = "marker123"
        val state = DialogState(isVisible = true, markerId = markerId, isTemporary = false)
        
        // isVisible만 변경
        val updatedVisibility = state.copy(isVisible = false)
        assertFalse(updatedVisibility.isVisible)
        assertEquals(markerId, updatedVisibility.markerId)
        assertFalse(updatedVisibility.isTemporary)
        
        // isTemporary만 변경
        val updatedTemporary = state.copy(isTemporary = true)
        assertTrue(updatedTemporary.isVisible)
        assertEquals(markerId, updatedTemporary.markerId)
        assertTrue(updatedTemporary.isTemporary)
        
        // markerId만 변경
        val newMarkerId = "newMarker456"
        val updatedMarkerId = state.copy(markerId = newMarkerId)
        assertTrue(updatedMarkerId.isVisible)
        assertEquals(newMarkerId, updatedMarkerId.markerId)
        assertFalse(updatedMarkerId.isTemporary)
    }
    
    @Test
    fun `DialogState equals 테스트`() {
        val state1 = DialogState(isVisible = true, markerId = "marker123", isTemporary = false)
        val state2 = DialogState(isVisible = true, markerId = "marker123", isTemporary = false)
        val state3 = DialogState(isVisible = false, markerId = "marker123", isTemporary = false)
        
        assertEquals(state1, state2)
        assertFalse(state1 == state3)
    }
} 