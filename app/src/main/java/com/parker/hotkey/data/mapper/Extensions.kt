package com.parker.hotkey.data.mapper

import com.naver.maps.geometry.LatLng
import com.parker.hotkey.data.local.entity.MarkerEntity
import com.parker.hotkey.data.local.entity.MemoEntity
import com.parker.hotkey.domain.model.LastSync
import com.parker.hotkey.domain.model.Marker
import com.parker.hotkey.domain.model.Memo

/**
 * MarkerEntity를 도메인 모델로 변환하는 확장 함수
 */
fun MarkerEntity.toMarkerDomain(): Marker = this.toDomain()

/**
 * MemoEntity를 도메인 모델로 변환하는 확장 함수
 */
fun MemoEntity.toMemoDomain(): Memo = this.toDomain()

/**
 * Marker 도메인 모델을 Entity로 변환하는 확장 함수
 */
fun Marker.toMarkerEntity(): MarkerEntity = MarkerEntity.fromDomain(this)

/**
 * Memo 도메인 모델을 Entity로 변환하는 확장 함수
 */
fun Memo.toMemoEntity(): MemoEntity = MemoEntity.fromDomain(this) 