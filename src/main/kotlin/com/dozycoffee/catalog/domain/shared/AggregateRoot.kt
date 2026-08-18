package com.dozycoffee.catalog.domain.shared

abstract class AggregateRoot<ID : Any>(
    id: ID,
) : Entity<ID>(id) {
    private val domainEvents = mutableListOf<DomainEvent>()

    protected fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }
}
