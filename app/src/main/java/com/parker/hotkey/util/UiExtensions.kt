package com.parker.hotkey.util

import android.content.Context
import android.content.res.Resources

/**
 * dp 값을 픽셀 단위로 변환하는 확장 함수
 * 모든 숫자 타입에 적용 가능한 기본 함수
 */
fun Number.dp2px(context: Context): Float {
    return this.toFloat() * context.resources.displayMetrics.density
}

/**
 * dp 값을 픽셀 단위로 변환하는 확장 함수 (Resources 이용)
 * 모든 숫자 타입에 적용 가능한 기본 함수
 */
fun Number.dp2px(resources: Resources): Float {
    return this.toFloat() * resources.displayMetrics.density
}

/**
 * Int 타입 dp 값을 Int 픽셀 단위로 변환하는 확장 함수
 */
fun Int.dp2pxInt(context: Context): Int {
    return (this.toFloat() * context.resources.displayMetrics.density).toInt()
}

/**
 * Int 타입 dp 값을 Int 픽셀 단위로 변환하는 확장 함수 (Resources 이용)
 */
fun Int.dp2pxInt(resources: Resources): Int {
    return (this.toFloat() * resources.displayMetrics.density).toInt()
}

// 기존 함수들 - 하위 호환성을 위해 유지

/**
 * Int 값을 픽셀 단위로 변환하는 확장 함수
 */
fun Int.dp2px(context: Context): Int {
    return this.dp2pxInt(context)
}

/**
 * Int 값을 픽셀 단위로 변환하는 확장 함수 (Resources 이용)
 */
fun Int.dp2px(resources: Resources): Int {
    return this.dp2pxInt(resources)
}

/**
 * Float 값을 픽셀 단위로 변환하는 확장 함수
 */
fun Float.dp2px(context: Context): Float {
    return (this * context.resources.displayMetrics.density)
}

/**
 * Float 값을 픽셀 단위로 변환하는 확장 함수 (Resources 이용)
 */
fun Float.dp2px(resources: Resources): Float {
    return (this * resources.displayMetrics.density)
}