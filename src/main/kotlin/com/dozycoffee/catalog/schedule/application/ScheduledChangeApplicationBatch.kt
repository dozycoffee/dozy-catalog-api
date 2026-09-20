package com.dozycoffee.catalog.schedule.application

import com.dozycoffee.catalog.common.TransactionRunner
import com.dozycoffee.catalog.core.DomainException
import com.dozycoffee.catalog.schedule.domain.ScheduledChange
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeId
import com.dozycoffee.catalog.schedule.domain.ScheduledChangeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

// 적용 시각이 지난 대기 예약을 적용한다(요구사항 1.4 / 시나리오 S2 3단계).
// 00시를 놓친 예약도 effectiveAt <= now면 함께 골라지므로, 이 배치는 주기적으로 여러 번 불려도 된다.
// 트리거(@Scheduled 등)는 두지 않는다 — 다중 인스턴스에서 스케줄러 자체의 중복 실행을 어떻게 막을지가
// 아직 미정이기 때문이다(docs/architecture/README.md 미정 사항). 호출만 하면 동작한다.
//
// 예약마다 트랜잭션을 따로 연다. 한 건의 실패가 다른 예약의 적용을 되돌리거나 배치를 멈추지 않게 하기 위해서다.
// - 적용 성공: 적용과 APPLIED 기록이 같은 트랜잭션에서 함께 커밋된다. "적용됐는데 대기로 남는" 상태가 생기지 않는다.
// - 규칙 위반(DomainException): 적용 트랜잭션이 통째로 롤백되어 대상 값이 그대로 남은 뒤,
//   별도 트랜잭션에서 FAILED만 기록한다. 기록을 같은 트랜잭션에서 하면 롤백에 함께 쓸려 나간다(요구사항 1.4).
// - 그 밖의 예외(DB 장애 등): 규칙 위반이 아니므로 실패로 확정하지 않고 대기로 남겨 다음 실행에서 다시 시도한다.
@Service
class ScheduledChangeApplicationBatch(
    private val scheduledChangeRepository: ScheduledChangeRepository,
    private val applier: ScheduledChangeApplier,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun applyDue(limit: Int = DEFAULT_LIMIT): ScheduledChangeBatchResult {
        require(limit > 0) { "한 번에 처리할 예약 건수는 1 이상이어야 합니다: $limit" }
        val now = clock.instant()
        // 대상 조회는 짧은 트랜잭션에서 끝낸다. 실제 적용은 예약마다 다시 잠그므로,
        // 여기서 잡은 목록이 그 사이 다른 워커에게 넘어가도 두 번 적용되지 않는다.
        val due = transactionRunner.inTransaction { scheduledChangeRepository.findDueForApplication(now, limit) }

        var applied = 0
        var failed = 0
        var skipped = 0
        due.forEach { scheduledChange ->
            when (applyOne(scheduledChange)) {
                Outcome.APPLIED -> applied++
                Outcome.FAILED -> failed++
                Outcome.SKIPPED -> skipped++
            }
        }
        return ScheduledChangeBatchResult(applied = applied, failed = failed, skipped = skipped)
    }

    private suspend fun applyOne(due: ScheduledChange): Outcome =
        try {
            transactionRunner.inTransaction {
                val scheduledChange =
                    scheduledChangeRepository.findPendingByIdForUpdateSkipLocked(due.id)
                        ?: return@inTransaction Outcome.SKIPPED
                applier.apply(scheduledChange)
                scheduledChange.apply()
                scheduledChangeRepository.save(scheduledChange)
                Outcome.APPLIED
            }
        } catch (e: DomainException) {
            log.info("예약 적용에 실패해 실패로 기록합니다: 예약 {}, {}", due.id.value, e.message)
            recordFailure(due.id)
        } catch (e: Exception) {
            // 규칙 위반이 아니므로 대기로 남긴다. 다음 실행에서 다시 고른다.
            log.error("예약 적용 중 오류가 나 이 예약을 건너뜁니다: 예약 {}", due.id.value, e)
            Outcome.SKIPPED
        }

    private suspend fun recordFailure(id: ScheduledChangeId): Outcome =
        transactionRunner.inTransaction {
            // 적용 트랜잭션이 롤백되며 잠금이 풀렸으므로 여전히 대기 중인지 다시 확인한다.
            val scheduledChange =
                scheduledChangeRepository.findPendingByIdForUpdateSkipLocked(id)
                    ?: return@inTransaction Outcome.SKIPPED
            scheduledChange.fail()
            scheduledChangeRepository.save(scheduledChange)
            Outcome.FAILED
        }

    private enum class Outcome {
        APPLIED,
        FAILED,
        SKIPPED,
    }

    companion object {
        // 한 번에 처리할 예약 건수 상한. 한 실행이 지나치게 길어지지 않게 하고, 남은 예약은 다음 실행에서 고른다.
        const val DEFAULT_LIMIT: Int = 100
    }
}

// 배치 한 번의 처리 결과. skipped는 다른 워커가 이미 가져갔거나 오류로 다음 실행에 미룬 건수다.
data class ScheduledChangeBatchResult(
    val applied: Int,
    val failed: Int,
    val skipped: Int,
)
