package com.dozycoffee.catalog.domain.storeavailability

// 판매 가능 여부를 누가 바꾸는가. 상품의 재고 추적 여부로 정해지며, 출처마다 기본값과
// 변경 경로가 다르다(요구사항 2.4, 2.5).
enum class AvailabilitySource(
    // 판매 가능 여부 row가 없을 때 적용하는 값
    val defaultStockStatus: StockStatus,
) {
    // 재고 추적 상품. 재고관리 서비스 이벤트로만 바뀌며, 매장 재고는 처음에 0이므로 품절로 시작한다.
    INVENTORY(StockStatus.SOLD_OUT),

    // 재고 미추적 상품. 점주가 재료 소진 등으로 직접 바꾸며, 판매중으로 시작한다.
    OWNER(StockStatus.ON_SALE),
    ;

    companion object {
        fun of(tracksInventory: Boolean): AvailabilitySource = if (tracksInventory) INVENTORY else OWNER
    }
}
