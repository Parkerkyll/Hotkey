package com.parker.hotkey.util

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.junit.Assert.assertEquals

class UiExtensionsTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var resources: Resources

    @Mock
    lateinit var displayMetrics: DisplayMetrics

    private val density = 2.5f

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        displayMetrics.density = density
        `when`(context.resources).thenReturn(resources)
        `when`(resources.displayMetrics).thenReturn(displayMetrics)
    }

    @Test
    fun `Number dp2px with Context converts correctly`() {
        // 정수 테스트
        assertEquals(25, 10.dp2px(context))
        
        // Long 테스트
        assertEquals(50, 20L.dp2px(context))
        
        // Float 테스트
        assertEquals(12.5f, 5.0f.dp2px(context), 0.01f)
        
        // Double 테스트
        assertEquals(37.5f, 15.0.dp2px(context), 0.01f)
    }

    @Test
    fun `Number dp2px with Resources converts correctly`() {
        // 정수 테스트
        assertEquals(25, 10.dp2px(resources))
        
        // Long 테스트
        assertEquals(50, 20L.dp2px(resources))
        
        // Float 테스트
        assertEquals(12.5f, 5.0f.dp2px(resources), 0.01f)
        
        // Double 테스트
        assertEquals(37.5f, 15.0.dp2px(resources), 0.01f)
    }

    @Test
    fun `Int dp2pxInt with Context converts and returns Int`() {
        assertEquals(25, 10.dp2pxInt(context))
        assertEquals(0, 0.dp2pxInt(context))
        assertEquals(250, 100.dp2pxInt(context))
    }

    @Test
    fun `Int dp2pxInt with Resources converts and returns Int`() {
        assertEquals(25, 10.dp2pxInt(resources))
        assertEquals(0, 0.dp2pxInt(resources))
        assertEquals(250, 100.dp2pxInt(resources))
    }

    @Test
    fun `Int dp2px with Context returns Int pixel value`() {
        assertEquals(25, 10.dp2px(context))
        assertEquals(0, 0.dp2px(context))
        assertEquals(250, 100.dp2px(context))
    }

    @Test
    fun `Int dp2px with Resources returns Int pixel value`() {
        assertEquals(25, 10.dp2px(resources))
        assertEquals(0, 0.dp2px(resources))
        assertEquals(250, 100.dp2px(resources))
    }

    @Test
    fun `Float dp2px with Context returns Float pixel value`() {
        assertEquals(25.0f, 10.0f.dp2px(context), 0.01f)
        assertEquals(0.0f, 0.0f.dp2px(context), 0.01f)
        assertEquals(12.5f, 5.0f.dp2px(context), 0.01f)
    }

    @Test
    fun `Float dp2px with Resources returns Float pixel value`() {
        assertEquals(25.0f, 10.0f.dp2px(resources), 0.01f)
        assertEquals(0.0f, 0.0f.dp2px(resources), 0.01f)
        assertEquals(12.5f, 5.0f.dp2px(resources), 0.01f)
    }
} 