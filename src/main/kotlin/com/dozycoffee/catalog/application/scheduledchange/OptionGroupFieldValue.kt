package com.dozycoffee.catalog.application.scheduledchange

import com.dozycoffee.catalog.domain.optiongroup.Option
import com.dozycoffee.catalog.domain.scheduledchange.TargetKind

// 옵션 그룹을 대상으로 하는 예약 값. 이름·선택 방식·필수 여부는 즉시 반영만 하므로 예약 대상이 아니다.
sealed interface OptionGroupFieldValue : ScheduledFieldValue {
    override val targetKind: TargetKind get() = TargetKind.OPTION_GROUP

    // 옵션 목록 전체(순서 포함)의 등록 시점 스냅샷. 이후의 즉시 변경과 무관하게 이 내용으로 교체한다(요구사항 1.9).
    data class Options(
        val options: List<Option>,
    ) : OptionGroupFieldValue {
        override val fieldName: String get() = "options"
    }
}
