-- Catalog 초기 스키마. 설계 근거: docs/erd.md, docs/adr/0010
-- 규칙: ID는 identity, 금액은 원 단위 BIGINT, 상태값은 VARCHAR + CHECK, 시각은 TIMESTAMPTZ,
--       애그리거트 루트는 낙관적 잠금용 version, 감사 컬럼은 DB 시계(now()).
-- 적용된 마이그레이션은 고치지 않는다. 변경은 새 버전으로 추가한다(docs/architecture/persistence.md).

-- ---------------------------------------------------------------------------
-- 카테고리 (2단계 계층: parent_category_id가 NULL이면 대분류)
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    parent_category_id BIGINT REFERENCES categories (id),
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT categories_not_self_parent CHECK (parent_category_id IS NULL OR parent_category_id <> id)
);
CREATE INDEX categories_parent_idx ON categories (parent_category_id);

-- ---------------------------------------------------------------------------
-- 태그, 상품 그룹
-- ---------------------------------------------------------------------------
CREATE TABLE tags (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE product_groups (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 옵션 그룹과 옵션
-- ---------------------------------------------------------------------------
CREATE TABLE option_groups (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    selection_type VARCHAR(20)  NOT NULL CHECK (selection_type IN ('SINGLE', 'MULTI')),
    required       BOOLEAN      NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 옵션 목록은 항상 통째로 교체되며, 상품별 예외는 option_key로 참조한다(id는 재발급될 수 있음).
CREATE TABLE options (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    option_group_id BIGINT       NOT NULL REFERENCES option_groups (id) ON DELETE CASCADE,
    option_key      VARCHAR(64)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    price           BIGINT       NOT NULL CHECK (price >= 0),
    display_order   INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT options_key_unique_in_group UNIQUE (option_group_id, option_key)
);

-- ---------------------------------------------------------------------------
-- 상품
-- ---------------------------------------------------------------------------
CREATE TABLE products (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku              VARCHAR(64) UNIQUE,
    name             VARCHAR(100) NOT NULL,
    category_id      BIGINT       NOT NULL REFERENCES categories (id),
    description      TEXT,
    image_url        VARCHAR(2048),
    base_price       BIGINT       NOT NULL CHECK (base_price >= 0),
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED')),
    store_scope      VARCHAR(20)  NOT NULL DEFAULT 'ALL' CHECK (store_scope IN ('ALL', 'LIMITED')),
    tracks_inventory BOOLEAN      NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX products_category_idx ON products (category_id);

-- 판매 범위가 LIMITED일 때의 대상 매장. store_id는 Store BC 참조라 FK가 없다.
CREATE TABLE product_target_stores (
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    store_id   BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, store_id)
);

-- 태그·그룹 삭제 시 참조하던 상품에서 자동으로 제거된다(요구사항 1.7, 1.8).
CREATE TABLE product_tags (
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    tag_id     BIGINT      NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, tag_id)
);
CREATE INDEX product_tags_tag_idx ON product_tags (tag_id);

CREATE TABLE product_groups_map (
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    group_id   BIGINT      NOT NULL REFERENCES product_groups (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, group_id)
);
CREATE INDEX product_groups_map_group_idx ON product_groups_map (group_id);

-- 옵션 그룹 삭제는 연결한 상품이 있으면 거부된다(요구사항 1.9) — ON DELETE RESTRICT.
CREATE TABLE product_option_groups (
    product_id      BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    option_group_id BIGINT      NOT NULL REFERENCES option_groups (id),
    display_order   INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, option_group_id)
);
CREATE INDEX product_option_groups_option_group_idx ON product_option_groups (option_group_id);

-- 상품별 옵션 예외는 상품-옵션 그룹 연결에 속한다. 연결이 해제되면 함께 삭제된다.
CREATE TABLE product_option_overrides (
    product_id      BIGINT      NOT NULL,
    option_group_id BIGINT      NOT NULL,
    option_key      VARCHAR(64) NOT NULL,
    override_type   VARCHAR(20) NOT NULL CHECK (override_type IN ('PRICE', 'EXCLUDE')),
    price           BIGINT CHECK (price >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, option_group_id, option_key),
    FOREIGN KEY (product_id, option_group_id)
        REFERENCES product_option_groups (product_id, option_group_id) ON DELETE CASCADE,
    CONSTRAINT product_option_overrides_type_price CHECK (
        (override_type = 'PRICE' AND price IS NOT NULL) OR (override_type = 'EXCLUDE' AND price IS NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 예약 변경
-- ---------------------------------------------------------------------------
-- target_id는 products.id 또는 option_groups.id를 다형 참조하므로 FK가 없다.
-- effective_date는 업무 날짜(업무 시간대 기준), effective_at은 그 날 00시를 업무 시간대로 해석한 순간이다.
-- 배치는 effective_at으로만 대상을 고른다(시간대와 무관).
CREATE TABLE scheduled_changes (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_id      BIGINT      NOT NULL,
    target_kind    VARCHAR(30) NOT NULL CHECK (target_kind IN ('PRODUCT', 'OPTION_GROUP', 'PRODUCT_OPTION_GROUP')),
    field_name     VARCHAR(64) NOT NULL,
    new_value      JSONB       NOT NULL,
    effective_date DATE        NOT NULL,
    effective_at   TIMESTAMPTZ NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPLIED', 'CANCELLED', 'FAILED')),
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 같은 대상·필드의 대기 중 예약은 최대 1건(요구사항 1.4)
CREATE UNIQUE INDEX scheduled_changes_one_pending_per_field
    ON scheduled_changes (target_id, target_kind, field_name) WHERE status = 'PENDING';
-- 배치 대상 조회: 대기 중이고 적용 시각이 지난 예약
CREATE INDEX scheduled_changes_pending_due_idx
    ON scheduled_changes (effective_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- 매장별 진열 설정과 판매 가능 여부 (store_id는 Store BC 참조라 FK가 없다)
-- ---------------------------------------------------------------------------
-- row 부재 = 기본값(노출). 판매 범위에서 빠진 매장의 row는 삭제한다(요구사항 1.5).
CREATE TABLE store_display_settings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id      BIGINT      NOT NULL,
    product_id    BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    display_order INTEGER,
    visibility    VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' CHECK (visibility IN ('VISIBLE', 'HIDDEN')),
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT store_display_settings_store_product UNIQUE (store_id, product_id)
);
CREATE INDEX store_display_settings_product_idx ON store_display_settings (product_id);

-- row 부재 = 출처별 기본값(INVENTORY는 품절, OWNER는 판매중).
-- INVENTORY 출처는 판매 범위에서 빠져도 삭제하지 않는다(요구사항 1.5, 2.4).
CREATE TABLE store_product_availabilities (
    store_id      BIGINT      NOT NULL,
    product_id    BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    source        VARCHAR(20) NOT NULL CHECK (source IN ('INVENTORY', 'OWNER')),
    stock_status  VARCHAR(20) NOT NULL CHECK (stock_status IN ('ON_SALE', 'SOLD_OUT')),
    last_event_at TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (store_id, product_id),
    CONSTRAINT store_product_availabilities_owner_has_no_event CHECK (source = 'INVENTORY' OR last_event_at IS NULL)
);
CREATE INDEX store_product_availabilities_product_idx ON store_product_availabilities (product_id);
