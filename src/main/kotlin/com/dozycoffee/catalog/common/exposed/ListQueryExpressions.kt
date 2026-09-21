package com.dozycoffee.catalog.common.exposed

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase

// 목록 조회의 검색어 조건: 대소문자를 가리지 않는 부분 일치. 검색어의 %, _ 는 와일드카드가 아니라 글자로 찾는다.
fun Expression<String>.containsIgnoringCase(keyword: String): Op<Boolean> {
    val literal = LikePattern.ofLiteral(keyword.lowercase())
    return lowerCase() like LikePattern("%${literal.pattern}%", literal.escapeChar)
}

// 앞뒤 공백을 뺀 검색어. 비어 있으면 검색어를 주지 않은 것으로 본다.
fun String?.toSearchKeyword(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

// 이름 순 정렬에 쓰는 한국어 정렬 규칙. DB 기본 정렬 규칙(en_US.utf8, glibc)은 한글을 가나다순으로 정렬하지 않으므로
// ICU 한국어 규칙(ko-x-icu)으로 정렬한다: 한글이 가나다순으로 먼저 오고, 영문은 대소문자를 가리지 않고 그 뒤에 온다.
fun Expression<String>.koreanOrder(): Expression<String> = Collated(this, KOREAN_COLLATION)

private const val KOREAN_COLLATION = "ko-x-icu"

private class Collated(
    private val expression: Expression<String>,
    private val collation: String,
) : Expression<String>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(expression)
        queryBuilder.append(" COLLATE \"$collation\"")
    }
}
