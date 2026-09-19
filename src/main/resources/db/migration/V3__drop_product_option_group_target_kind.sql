-- 상품-옵션 그룹 연결의 예약은 상품을 대상으로 하고 필드 이름으로 구분한다(docs/adr/0014).
-- target_id 하나로는 (상품, 옵션 그룹)을 표현할 수 없어 PRODUCT_OPTION_GROUP을 뺀다.
-- 아직 이 값으로 저장된 예약은 없다. 제약 이름은 V1에서 PostgreSQL이 붙인 이름을 그대로 쓴다.
ALTER TABLE scheduled_changes
    DROP CONSTRAINT scheduled_changes_target_kind_check,
    ADD CONSTRAINT scheduled_changes_target_kind_check CHECK (target_kind IN ('PRODUCT', 'OPTION_GROUP'));
