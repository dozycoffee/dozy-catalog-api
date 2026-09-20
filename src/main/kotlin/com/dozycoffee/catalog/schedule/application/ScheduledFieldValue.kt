package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.schedule.domain.ScheduledValue

// 예약 가능한 필드 전체. 필드마다 값 타입이 하나씩 있고 필드 이름은 타입이 만든다.
// 배치의 적용과 JSONB 직렬화가 when으로 빠짐없이 처리하므로, 필드를 추가하고 처리를 빠뜨리면 컴파일 에러가 된다.
// 값에 다른 애그리거트의 모델이 들어가 domain이 아니라 application에 두며, Kotlin sealed 타입은 같은 패키지에서만
// 구현할 수 있어 하위 타입도 모두 이 패키지에 둔다(docs/adr/0014).
//
// 예약 대상이 아닌 필드(재고 추적 여부, 옵션 그룹의 이름·선택 방식·필수 여부)는 즉시 반영만 하므로 여기 없다.
sealed interface ScheduledFieldValue : ScheduledValue
