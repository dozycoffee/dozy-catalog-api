-- 낙관적 잠금은 Product와 OptionGroup에만 쓴다(docs/adr/0013). 다른 테이블의 version은 쓰이지 않아
-- "여기도 낙관적 잠금이 있다"는 오해를 부르므로 제거한다. 이 테이블들의 동시성은 비관적 잠금,
-- 상태 조건 UPDATE, 조건부 upsert로 다룬다(docs/erd.md 동시성 처리).
ALTER TABLE categories DROP COLUMN version;
ALTER TABLE tags DROP COLUMN version;
ALTER TABLE product_groups DROP COLUMN version;
ALTER TABLE scheduled_changes DROP COLUMN version;
ALTER TABLE store_display_settings DROP COLUMN version;
ALTER TABLE store_product_availabilities DROP COLUMN version;
