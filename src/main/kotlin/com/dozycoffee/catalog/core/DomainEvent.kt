package com.dozycoffee.catalog.core

import java.time.Instant

abstract class DomainEvent(
    val occurredAt: Instant = Instant.now(),
)
