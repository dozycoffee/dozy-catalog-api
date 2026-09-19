package com.dozycoffee.catalog.domain.scheduledchange

// 예약할 값. 예약 가능한 필드마다 구현 타입이 하나씩 있고, 대상 종류와 필드 이름은 그 타입이 정한다.
// 값에 다른 애그리거트의 모델(StoreScope, OptionOverride, Option 등)이 들어가므로 구현 타입(sealed 계층)은
// application(application/scheduledchange)에 두고, domain은 대기 예약을 대상·필드별로 구분하는 데 필요한
// 이 두 가지만 안다(docs/adr/0014).
interface ScheduledValue {
    val targetKind: TargetKind

    // 같은 대상·같은 필드의 대기 예약은 최대 1건이다. 이름이 같으면 서로 대체하고, 다르면 함께 대기한다.
    val fieldName: String
}
