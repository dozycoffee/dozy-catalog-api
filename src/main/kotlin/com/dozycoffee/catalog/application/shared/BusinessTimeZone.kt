package com.dozycoffee.catalog.application.shared

import java.time.ZoneId

// 업무 기준 시간대. 예약의 "지정한 날짜의 00시"(요구사항 1.4)처럼 업무 날짜를 해석할 때 쓴다(docs/adr/0010).
// 지금은 설정값 하나(catalog.business-zone)를 돌려준다. 여러 나라에서 서비스하게 되면 시장이나 매장을 받아
// 시간대를 돌려주도록 바꾼다. 코드는 시스템 기본 시간대(ZoneId.systemDefault())를 쓰지 않는다.
interface BusinessTimeZone {
    val zoneId: ZoneId
}
