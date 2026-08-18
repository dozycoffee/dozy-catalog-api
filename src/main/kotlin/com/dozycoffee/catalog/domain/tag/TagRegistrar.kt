package com.dozycoffee.catalog.domain.tag

// 상품 등록/수정 흐름에서 태그를 이름으로 입력하면 기존 태그를 재사용하거나
// 없으면 새로 생성한다 — 이 "이름으로 등록/재사용"을 도메인 레벨 개념으로 노출한다.
class TagRegistrar(
    private val tagRepository: TagRepository,
) {
    suspend fun register(name: String): Tag = tagRepository.findOrCreateByName(name)
}
