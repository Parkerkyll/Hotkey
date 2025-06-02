package com.parker.hotkey.domain.util

import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 약 참조(WeakReference)를 사용하여 객체를 관리하는 유틸리티 클래스
 * 메모리 누수를 방지하고 더 이상 사용되지 않는 객체에 대한 참조를 자동으로 정리합니다.
 *
 * @param T 관리할 객체 타입
 */
class WeakReferenceManager<T : Any> {
    // 약 참조 맵 (키-값 쌍으로 관리)
    // 접근 제한자를 public으로 변경하여 인라인 함수에서 접근 가능하도록 함
    val references = ConcurrentHashMap<String, WeakReference<T>>()
    
    /**
     * 객체를 등록합니다.
     *
     * @param key 객체의 식별 키
     * @param obj 등록할 객체
     */
    fun register(key: String, obj: T) {
        references[key] = WeakReference(obj)
        Timber.v("[$key] 객체 등록됨 (현재 ${references.size}개 관리 중)")
    }
    
    /**
     * 키로 객체를 찾습니다. 
     * 객체가 이미 GC에 의해 수집된 경우 null을 반환하고 맵에서 제거합니다.
     *
     * @param key 찾을 객체의 키
     * @return 찾은 객체 또는 null
     */
    fun get(key: String): T? {
        return references[key]?.get().also { obj ->
            if (obj == null) {
                // 객체가 이미 수집됨 - 맵에서 제거
                references.remove(key)
                Timber.v("[$key] 객체 참조가 이미 수집됨, 제거됨")
            }
        }
    }
    
    /**
     * 특정 키의 객체 등록을 해제합니다.
     *
     * @param key 해제할 객체의 키
     * @return 해제 성공 여부
     */
    fun unregister(key: String): Boolean {
        return references.remove(key) != null
    }
    
    /**
     * 모든 객체 등록을 해제합니다.
     */
    fun clear() {
        val count = references.size
        references.clear()
        Timber.v("모든 객체 참조 해제됨 (총 ${count}개)")
    }
    
    /**
     * 더 이상 유효하지 않은 참조를 정리합니다.
     * 
     * @return 정리된 항목 수
     */
    fun cleanup(): Int {
        var cleaned = 0
        val iterator = references.entries.iterator()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get() == null) {
                iterator.remove()
                cleaned++
            }
        }
        
        if (cleaned > 0) {
            Timber.v("참조 정리 완료: ${cleaned}개 제거됨 (남은 참조: ${references.size}개)")
        }
        
        return cleaned
    }
    
    /**
     * 모든 등록된 객체에 대해 작업을 수행합니다.
     * 객체가 이미 GC에 의해 수집된 경우 맵에서 제거합니다.
     *
     * @param action 각 객체에 대해 수행할 작업
     * @return 처리된 객체 수
     */
    // inline 키워드를 제거하여 private 필드 접근 문제 해결
    fun forEachObject(action: (T) -> Unit): Int {
        var processed = 0
        val invalidKeys = mutableListOf<String>()
        
        references.forEach { (key, ref) ->
            val obj = ref.get()
            if (obj != null) {
                action(obj)
                processed++
            } else {
                invalidKeys.add(key)
            }
        }
        
        // 유효하지 않은 참조 제거
        invalidKeys.forEach { key ->
            references.remove(key)
            Timber.v("[$key] 객체 참조가 이미 수집됨, 제거됨")
        }
        
        return processed
    }
    
    /**
     * 현재 관리 중인 객체 수를 반환합니다.
     * 이미 GC에 의해 수집된 객체는 포함되지 않습니다.
     *
     * @return 유효한 객체 수
     */
    fun validCount(): Int {
        // 유효하지 않은 참조 정리
        cleanup()
        return references.size
    }
    
    /**
     * 모든 등록된 객체를 목록으로 반환합니다.
     * 이미 GC에 의해 수집된 객체는 포함되지 않습니다.
     *
     * @return 유효한 객체 목록
     */
    fun getAllObjects(): List<T> {
        val result = mutableListOf<T>()
        forEachObject { result.add(it) }
        return result
    }
} 