-- SKU는 등록 시점에 시스템이 부여한다(요구사항 1.2). 상품 ID와 분리된 전용 시퀀스에서 뽑아,
-- 내부 식별자가 외부 시스템(POS·재고관리)에 새지 않게 한다. 형식(DZ-00000123)은 애플리케이션이 만든다.
CREATE SEQUENCE product_sku_seq AS BIGINT START WITH 1 INCREMENT BY 1;
