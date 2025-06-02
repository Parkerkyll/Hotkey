package com.parker.hotkey.data.mapper

import com.parker.hotkey.domain.model.Synchronizable

/**
 * 엔티티와 도메인 모델 간 매핑을 위한 확장 함수
 */
inline fun <reified T : Any, reified E : Any> E.mapTo(mapper: (E) -> T): T = mapper(this)

/**
 * 도메인 모델과 엔티티 간 양방향 매핑을 위한 기본 인터페이스
 * 
 * @param D 도메인 모델 타입
 * @param E 엔티티 타입
 */
interface EntityMapper<D : Any, E : Any> {
    /**
     * 도메인 모델을 엔티티로 변환
     */
    fun toEntity(domain: D): E
    
    /**
     * 엔티티를 도메인 모델로 변환
     */
    fun toDomain(entity: E): D
    
    /**
     * 도메인 모델 리스트를 엔티티 리스트로 변환
     */
    fun toEntityList(domainList: List<D>): List<E> = domainList.map(::toEntity)
    
    /**
     * 엔티티 리스트를 도메인 모델 리스트로 변환
     */
    fun toDomainList(entityList: List<E>): List<D> = entityList.map(::toDomain)
}

/**
 * 동기화 가능한 도메인 모델을 위한 엔티티 매퍼
 */
interface SyncEntityMapper<D : Synchronizable, E : Any> : EntityMapper<D, E> 