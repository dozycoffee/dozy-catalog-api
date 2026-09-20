package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.common.BusinessTimeZone
import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.product.domain.optiongroup.OptionGroupId
import com.dozycoffee.catalog.product.domain.product.ProductId
import com.dozycoffee.catalog.schedule.application.command.CancelScheduledChangeCommand
import com.dozycoffee.catalog.schedule.application.command.RegisterScheduledChangeCommand
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import com.dozycoffee.catalog.schedule.domain.TargetKind
import com.dozycoffee.catalog.schedule.domain.exception.NoPendingScheduleException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

// 예약의 등록·취소·조회(요구사항 1.4 / 시나리오 S2). 적용은 ScheduledChangeApplicationBatch가 맡는다.
// 적용일이 내일 이후인지는 도메인(NewScheduledChange.of)이 지키고, 여기서는 오늘 날짜를
// Clock과 업무 시간대로 구해 넘긴다(도메인은 시계를 모른다, ADR-0010).
@Service
class ScheduledChangeApplicationService(
    private val scheduledChangeRepository: ScheduledChangeRepository,
    private val clock: Clock,
    private val businessTimeZone: BusinessTimeZone,
    private val transactionRunner: TransactionRunner,
) {
    // 같은 대상·같은 필드의 대기 예약은 최대 1건이다. 기존 예약을 잠근 채 취소하고 새로 등록해,
    // 두 요청이 겹쳐도 대기 예약이 둘이 되지 않는다(DB 부분 UNIQUE로 이중 보장).
    // 적용일 검증을 먼저 해, 거부되는 요청이 기존 예약을 건드리지 않게 한다.
    suspend fun register(command: RegisterScheduledChangeCommand): ScheduledChange =
        transactionRunner.inTransaction {
            val newScheduledChange =
                ScheduledChange.NewScheduledChange.of(
                    targetId = command.targetId,
                    newValue = command.newValue,
                    effectiveDate = command.effectiveDate,
                    today = today(),
                    businessZone = businessTimeZone.zoneId,
                )
            val replaced =
                scheduledChangeRepository.findPendingByTargetForUpdate(
                    targetId = newScheduledChange.targetId,
                    targetKind = newScheduledChange.targetKind,
                    fieldName = newScheduledChange.fieldName,
                )
            if (replaced != null) {
                replaced.cancel()
                scheduledChangeRepository.save(replaced)
            }
            scheduledChangeRepository.insert(newScheduledChange)
        }

    // 대기 예약이 없으면 거부한다. 취소하는 사이 배치가 먼저 적용·실패로 기록했으면 대기 예약이 더는 없으므로
    // 같은 이유로 거부된다(요구사항 1.4 / S2 2b, 2c). 읽은 뒤에 처리된 드문 경우는 Repository가
    // 상태 조건 UPDATE로 잡아 ScheduleAlreadyProcessedException(409)으로 거부한다.
    suspend fun cancel(command: CancelScheduledChangeCommand): ScheduledChange =
        transactionRunner.inTransaction {
            val pending =
                scheduledChangeRepository.findPendingByTargetForUpdate(
                    targetId = command.targetId,
                    targetKind = command.targetKind,
                    fieldName = command.fieldName,
                ) ?: throw NoPendingScheduleException(command.targetId, command.targetKind, command.fieldName)
            pending.cancel()
            scheduledChangeRepository.save(pending)
        }

    // 상품·옵션 그룹 조회 화면이 필드별 현재 값 옆에 예약 값과 적용 날짜를 함께 보여 주기 위한 조회다(요구사항 1.4).
    // 현재 값과 합치는 일은 presentation이 한다.
    suspend fun findPendingForProduct(productId: ProductId): List<ScheduledChange> = findPending(productId.value, TargetKind.PRODUCT)

    suspend fun findPendingForOptionGroup(optionGroupId: OptionGroupId): List<ScheduledChange> =
        findPending(optionGroupId.value, TargetKind.OPTION_GROUP)

    private suspend fun findPending(
        targetId: Long,
        targetKind: TargetKind,
    ): List<ScheduledChange> = transactionRunner.inTransaction { scheduledChangeRepository.findAllPendingByTarget(targetId, targetKind) }

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), businessTimeZone.zoneId)
}
