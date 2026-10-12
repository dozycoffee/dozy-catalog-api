-- 부하테스트 시드. docs/requirements.md 4.1의 설계 기준 규모를 만든다(기본 인자).
-- 항상 비운 뒤 다시 만든다(결정적이라 같은 입력이면 같은 데이터). 스키마는 앱이 시작할 때 Flyway가 만들어 둔다.
--
--   scripts/seed.sh           설계 기준: 매장 3,000, 상품 200(ACTIVE)
--   scripts/seed.sh x2        여유 규모: 매장 6,000, 상품 400(ACTIVE)
--
-- 규모 인자(psql -v): stores, drinks, foods, mds, old, mdlinks, groups, sched_name
-- 상품 id는 1부터 연속이고 구성은 [음료][푸드][MD(재고 추적)][단종 누적] 순서다. k6 스크립트가 이 규칙에 기대므로 바꾸면 k6/lib/config.js도 바꾼다.
--   - id % 20 < 3 인 ACTIVE 상품은 판매 범위가 LIMITED(매장의 1/3에만 판매), 나머지는 ALL
\set ON_ERROR_STOP on
\if :{?stores}   \else \set stores 3000 \endif
\if :{?drinks}   \else \set drinks 100  \endif
\if :{?foods}    \else \set foods 35    \endif
\if :{?mds}      \else \set mds 65      \endif
\if :{?old}      \else \set old 300     \endif
\if :{?mdlinks}  \else \set mdlinks 15  \endif
\if :{?groups}   \else \set groups 30   \endif
\if :{?sched_name} \else \set sched_name 100 \endif

SELECT :drinks + :foods + :mds AS active \gset

BEGIN;

TRUNCATE categories, tags, product_groups, option_groups, options, products, product_target_stores,
         product_tags, product_groups_map, product_option_groups, product_option_overrides,
         scheduled_changes, store_display_settings, store_product_availabilities
    RESTART IDENTITY CASCADE;
ALTER SEQUENCE product_sku_seq RESTART WITH 1;

-- 카테고리: 대분류 3개(음료, 푸드, MD) × 소분류 4개
INSERT INTO categories (name) VALUES ('음료'), ('푸드'), ('MD');
INSERT INTO categories (name, parent_category_id)
SELECT t.name || ' ' || g, t.id
FROM categories t CROSS JOIN generate_series(1, 4) g
WHERE t.parent_category_id IS NULL;
CREATE TEMP TABLE child_cat AS
SELECT c.id, p.name AS top, row_number() OVER (PARTITION BY p.id ORDER BY c.id) AS rn
FROM categories c JOIN categories p ON p.id = c.parent_category_id;

-- 상품
INSERT INTO products (sku, name, category_id, description, image_url, base_price, status, store_scope, tracks_inventory)
SELECT 'DZ-' || lpad(nextval('product_sku_seq')::text, 8, '0'),
       'Item ' || g,
       (SELECT cc.id FROM child_cat cc
         WHERE cc.top = CASE WHEN g <= :drinks THEN '음료'
                             WHEN g <= :drinks + :foods THEN '푸드'
                             WHEN g <= :active THEN 'MD'
                             ELSE (ARRAY['음료', '푸드', 'MD'])[1 + g % 3] END
           AND cc.rn = 1 + g % 4),
       '부하테스트 상품 ' || g,
       'https://cdn.example.com/p/' || g || '.png',
       3000 + (g % 20) * 300,
       CASE WHEN g <= :active THEN 'ACTIVE' ELSE 'DISCONTINUED' END,
       CASE WHEN g <= :active AND g % 20 < 3 THEN 'LIMITED' ELSE 'ALL' END,
       (g > :drinks + :foods AND g <= :active)
FROM (SELECT g FROM generate_series(1, :active + :old) g ORDER BY g) s;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM products WHERE id <> split_part(name, ' ', 2)::bigint) THEN
        RAISE EXCEPTION '상품 id가 연속이 아니다. k6가 기대하는 id 규칙이 깨진다';
    END IF;
END $$;

-- 한정 판매 상품은 매장의 1/3에만 판매한다
INSERT INTO product_target_stores (product_id, store_id)
SELECT p.id, s
FROM products p CROSS JOIN generate_series(1, :stores) s
WHERE p.store_scope = 'LIMITED' AND (s + p.id) % 3 = 0;

-- 태그, 상품 그룹
INSERT INTO tags (name) SELECT 'tag-' || g FROM generate_series(1, 10) g;
INSERT INTO product_groups (name) SELECT 'group-' || g FROM generate_series(1, 5) g;
INSERT INTO product_tags (product_id, tag_id) SELECT id, 1 + id % 10 FROM products WHERE status = 'ACTIVE';
INSERT INTO product_groups_map (product_id, group_id) SELECT id, 1 + id % 5 FROM products WHERE status = 'ACTIVE';

-- 옵션 그룹(옵션 4개씩, 앞의 10개 그룹은 필수)
INSERT INTO option_groups (name, selection_type, required)
SELECT 'Group ' || g, CASE WHEN g % 3 = 0 THEN 'MULTI' ELSE 'SINGLE' END, g <= 10
FROM (SELECT g FROM generate_series(1, :groups) g ORDER BY g) s;
INSERT INTO options (option_group_id, option_key, name, price, display_order)
SELECT og.id, 'K' || k, 'Option ' || og.id || '-' || k, (k - 1) * 500, k
FROM option_groups og CROSS JOIN generate_series(1, 4) k;

-- 상품-옵션 그룹 연결: 음료 3개(짝수 id는 4개), 푸드 1개, MD는 앞의 :mdlinks개만 1개
INSERT INTO product_option_groups (product_id, option_group_id, display_order)
SELECT p.id, ((p.id + o.off) % :groups) + 1, o.ord
FROM products p
JOIN (VALUES (0, 1), (7, 2), (13, 3), (19, 4)) AS o(off, ord)
  ON (p.id <= :drinks AND (o.ord <= 3 OR (o.ord = 4 AND p.id % 2 = 0)))
  OR (p.id > :drinks AND p.id <= :drinks + :foods AND o.ord = 1)
  OR (p.id > :drinks + :foods AND p.id <= :drinks + :foods + :mdlinks AND o.ord = 1);

-- 상품별 옵션 예외: 일부는 가격 변경(K2), 일부는 제외(K4, 옵션 4개 중 1개라 선택 가능한 옵션이 남는다)
INSERT INTO product_option_overrides (product_id, option_group_id, option_key, override_type, price)
SELECT product_id, option_group_id, 'K2', 'PRICE', 700 FROM product_option_groups WHERE product_id % 10 = 0 AND display_order = 1
UNION ALL
SELECT product_id, option_group_id, 'K4', 'EXCLUDE', NULL::bigint FROM product_option_groups WHERE product_id % 10 = 5 AND display_order = 1;

-- 매장 개별 설정: 매장이 취급하는 ACTIVE 상품의 30%, 그중 5%는 숨김, 매장의 20%는 진열 순서를 정함
INSERT INTO store_display_settings (store_id, product_id, display_order, visibility)
SELECT store_id, product_id,
       CASE WHEN store_id % 5 = 0 THEN row_number() OVER (PARTITION BY store_id ORDER BY product_id) END,
       CASE WHEN (store_id * 31 + product_id * 17) % 20 = 0 THEN 'HIDDEN' ELSE 'VISIBLE' END
FROM (
    SELECT s AS store_id, p.id AS product_id
    FROM generate_series(1, :stores) s
    JOIN products p ON p.status = 'ACTIVE'
     AND (p.store_scope = 'ALL' OR (s + p.id) % 3 = 0)
     AND (s::bigint * 7919 + p.id * 104729) % 10 < 3
) x;

-- 판매 가능 여부: 재고 추적 상품은 재고 이벤트로(20%는 품절), 재고 미추적 상품은 점주 수동 품절이 5%
INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status, last_event_at)
SELECT s, p.id, 'INVENTORY',
       CASE WHEN (s::bigint * 13 + p.id * 7) % 5 = 0 THEN 'SOLD_OUT' ELSE 'ON_SALE' END,
       now() - ((s::bigint * p.id) % 86400) * interval '1 second'
FROM generate_series(1, :stores) s
JOIN products p ON p.status = 'ACTIVE' AND p.tracks_inventory AND (p.store_scope = 'ALL' OR (s + p.id) % 3 = 0);
INSERT INTO store_product_availabilities (store_id, product_id, source, stock_status, last_event_at)
SELECT s, p.id, 'OWNER', 'SOLD_OUT', NULL
FROM generate_series(1, :stores) s
JOIN products p ON p.status = 'ACTIVE' AND NOT p.tracks_inventory AND (p.store_scope = 'ALL' OR (s + p.id) % 3 = 0)
WHERE (s::bigint * 131 + p.id * 29) % 20 = 0;

-- 대기 예약: 상품마다 기준가 변경 1건 + 앞쪽 상품의 이름 변경. 적용 시각은 10일 뒤라 배치가 건드리지 않는다
-- (배치 부하를 줄 때는 scripts/due-schedules.sh로 적용 시각을 지금으로 당긴다)
INSERT INTO scheduled_changes (target_id, target_kind, field_name, new_value, effective_date, effective_at)
SELECT id, 'PRODUCT', 'basePrice', jsonb_build_object('type', 'basePrice', 'value', base_price + 100),
       current_date + 10, now() + interval '10 days'
FROM products WHERE status = 'ACTIVE'
UNION ALL
SELECT id, 'PRODUCT', 'name', jsonb_build_object('type', 'name', 'value', 'Item ' || id || ' v2'),
       current_date + 10, now() + interval '10 days'
FROM products WHERE id <= :sched_name;

COMMIT;

ANALYZE;

SELECT 'products' AS t, count(*) FROM products
UNION ALL SELECT 'products(ACTIVE)', count(*) FROM products WHERE status = 'ACTIVE'
UNION ALL SELECT 'store_display_settings', count(*) FROM store_display_settings
UNION ALL SELECT 'availabilities(INVENTORY)', count(*) FROM store_product_availabilities WHERE source = 'INVENTORY'
UNION ALL SELECT 'availabilities(OWNER)', count(*) FROM store_product_availabilities WHERE source = 'OWNER'
UNION ALL SELECT 'option_groups', count(*) FROM option_groups
UNION ALL SELECT 'product_option_groups', count(*) FROM product_option_groups
UNION ALL SELECT 'scheduled_changes(PENDING)', count(*) FROM scheduled_changes WHERE status = 'PENDING';
