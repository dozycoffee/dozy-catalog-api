package com.dozycoffee.catalog.domain.shared

abstract class Entity<ID : Any>(
    val id: ID,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Entity<*>
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
