package com.dozycoffee.catalog.domain.shared

import java.time.Instant

abstract class DomainEvent(
    val occurredAt: Instant = Instant.now(),
)
