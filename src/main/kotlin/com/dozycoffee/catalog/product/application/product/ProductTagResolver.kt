package com.dozycoffee.catalog.product.application.product

import com.dozycoffee.catalog.product.domain.tag.TagId
import com.dozycoffee.catalog.product.domain.tag.TagRepository
import org.springframework.stereotype.Component

// 상품에 붙일 태그를 이름으로 받아 ID로 바꾼다. 같은 이름의 태그가 있으면 재사용하고 없으면 만든다(요구사항 1.7).
// 즉시 변경과 예약 등록이 같은 코드를 쓴다. 만든 태그는 부르는 유스케이스의 트랜잭션에 속하므로,
// 그 요청이 거부되면 함께 롤백되어 남지 않는다.
@Component
class ProductTagResolver(
    private val tagRepository: TagRepository,
) {
    suspend fun resolve(tagNames: List<String>): Set<TagId> = tagNames.map { tagRepository.findOrCreateByName(it).id }.toSet()
}
