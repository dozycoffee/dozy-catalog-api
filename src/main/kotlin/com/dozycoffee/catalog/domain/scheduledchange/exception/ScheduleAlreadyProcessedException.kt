package com.dozycoffee.catalog.domain.scheduledchange.exception

import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.domain.scheduledchange.ScheduleStatus
import com.dozycoffee.catalog.domain.scheduledchange.ScheduledChangeId

// 상태 전이를 저장하려 했으나 그 사이 다른 쪽(관리자 취소, 배치 적용)이 먼저 이 예약을 처리했다.
// 관리자의 취소가 배치 적용과 겹치면 생길 수 있으므로 호출 코드 오류(require/check)가 아니라 규칙 위반으로 다룬다.
class ScheduleAlreadyProcessedException(
    id: ScheduledChangeId,
    attempted: ScheduleStatus,
) : DomainException(
        errorCode = ScheduledChangeErrorCode.SCHEDULE_ALREADY_PROCESSED,
        message = "이미 처리된 예약이라 $attempted 로 전환할 수 없습니다: 예약 ${id.value}",
    )
