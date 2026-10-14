-- 부하테스트 시드. docs/requirements.md 4.1의 설계 기준 규모를 만든다(기본 인자).
-- 항상 비운 뒤 다시 만든다(결정적이라 같은 입력이면 같은 데이터). 스키마는 앱이 시작할 때 Flyway가 만들어 둔다.
-- Catalog가 소유한 데이터만 만든다. 매장별 진열 설정과 판매 가능 여부는 Store로 이전되어(ADR-0020) 시드에 없다.
--
--   scripts/seed.sh           설계 기준: 상품 200(ACTIVE, 전체 약 500), 한정 판매 30개 × 대상 매장 1,000
--   scripts/seed.sh x2        여유 규모: 상품 400(ACTIVE), 한정 판매 60개 × 대상 매장 2,000
--   scripts/seed.sh fewstores 한정 판매 상품의 대상 매장이 소수(50개)인 경우(분포 민감도 확인)
--   scripts/seed.sh nolimited 한정 판매 없음(대조)
--
-- 규모 인자(psql -v): stores, drinks, foods, mds, old, mdlinks, groups, sched_scope, limited_stores
-- 상품 id는 1부터 연속이고 구성은 [음료][푸드][MD(재고 추적)][단종 누적] 순서다. k6 스크립트가 이 규칙에 기댄다.
--   - id % 20 < 3 인 ACTIVE 상품은 판매 범위가 LIMITED(대상 매장 limited_stores개), 나머지는 ALL
\set ON_ERROR_STOP on
\if :{?stores}   \else \set stores 3000 \endif
\if :{?drinks}   \else \set drinks 100  \endif
\if :{?foods}    \else \set foods 35    \endif
\if :{?mds}      \else \set mds 65      \endif
\if :{?old}      \else \set old 300     \endif
\if :{?mdlinks}  \else \set mdlinks 15  \endif
\if :{?groups}   \else \set groups 30   \endif
\if :{?sched_scope} \else \set sched_scope 100 \endif
\if :{?limited_stores} \else \set limited_stores 1000 \endif

SELECT :drinks + :foods + :mds AS active \gset

BEGIN;

TRUNCATE categories, tags, product_groups, option_groups, options, products, product_target_stores,
         product_tags, product_groups_map, product_option_groups, product_option_overrides,
         scheduled_changes, outbox_events, store_display_settings, store_product_availabilities
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

-- 한정 판매 상품의 대상 매장: 상품마다 서로 다른 구간의 매장 limited_stores개(연속 번호를 매장 수로 돌려 겹치지 않는다)
INSERT INTO product_target_stores (product_id, store_id)
SELECT p.id, ((p.id * 37 + k - 1) % :stores) + 1
FROM products p CROSS JOIN generate_series(1, :limited_stores) k
WHERE p.store_scope = 'LIMITED';

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

-- 대기 예약: 상품마다 기준가 변경 1건(이벤트 없음) + 앞쪽 상품 sched_scope개의 판매 범위 변경(상품 이벤트가 나간다. 대상 매장
-- limited_stores개를 담아 이벤트가 크다). 적용 시각은 10일 뒤라 배치가 건드리지 않는다
-- (배치 부하를 줄 때는 scripts/due-schedules.sh로 적용 시각을 지금으로 당긴다)
INSERT INTO scheduled_changes (target_id, target_kind, field_name, new_value, effective_date, effective_at)
SELECT id, 'PRODUCT', 'basePrice', jsonb_build_object('type', 'basePrice', 'value', base_price + 100),
       current_date + 10, now() + interval '10 days'
FROM products WHERE status = 'ACTIVE'
UNION ALL
SELECT id, 'PRODUCT', 'storeScope',
       jsonb_build_object('type', 'storeScope', 'value',
           jsonb_build_object('kind', 'LIMITED',
               'targetStoreIds', (SELECT jsonb_agg(g) FROM generate_series(1, LEAST(:limited_stores, :stores)) g))),
       current_date + 10, now() + interval '10 days'
FROM products WHERE id <= :sched_scope;

COMMIT;

ANALYZE;

SELECT 'products' AS t, count(*) FROM products
UNION ALL SELECT 'products(ACTIVE)', count(*) FROM products WHERE status = 'ACTIVE'
UNION ALL SELECT 'products(LIMITED)', count(*) FROM products WHERE store_scope = 'LIMITED'
UNION ALL SELECT 'product_target_stores', count(*) FROM product_target_stores
UNION ALL SELECT 'option_groups', count(*) FROM option_groups
UNION ALL SELECT 'product_option_groups', count(*) FROM product_option_groups
UNION ALL SELECT 'scheduled_changes(PENDING)', count(*) FROM scheduled_changes WHERE status = 'PENDING';
