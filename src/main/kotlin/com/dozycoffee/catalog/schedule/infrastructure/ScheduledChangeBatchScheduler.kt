package com.dozycoffee.catalog.schedule.infrastructure

import com.dozycoffee.catalog.schedule.application.ScheduledChangeApplicationBatch
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

// 적용 시각이 지난 대기 예약을 주기적으로 적용한다(요구사항 1.4 / 시나리오 S2 3단계).
// 00시 정각 cron 대신 짧은 간격으로 부른다. 대상은 effectiveAt <= now로 고르므로 00시를 놓쳐도(재시작, 장애)
// 다음 실행에서 따라잡고, 할 일이 없으면 조회 한 번으로 끝난다. 업무 시간대도 신경 쓸 필요가 없다.
// 인스턴스마다 따로 돌아도 배치가 예약마다 SKIP LOCKED로 잠그고 PENDING일 때만 상태를 저장하므로 두 번 적용되지 않는다.
// 간격은 앞 실행이 끝난 뒤부터 잰다. 한 번에 처리하는 건수에는 상한이 있고 남은 예약은 다음 실행에서 이어서 적용한다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "catalog.schedule.batch", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ScheduledChangeBatchScheduler(
    private val batch: ScheduledChangeApplicationBatch,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${catalog.schedule.batch.fixed-delay:PT1M}")
    suspend fun applyDue() {
        try {
            val result = batch.applyDue()
            if (result.applied + result.failed + result.skipped > 0) {
                log.info("예약 적용 배치: 적용 {}건, 실패 {}건, 건너뜀 {}건", result.applied, result.failed, result.skipped)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 대상 조회부터 실패한 경우(DB 장애 등)다. 예약은 대기로 남아 다음 실행에서 다시 고른다.
            log.error("예약 적용 배치를 실행하지 못했습니다. 다음 실행에서 다시 시도합니다", e)
        }
    }
}
