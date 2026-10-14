-- 카테고리 노출 순서(요구사항 1.6, #135). 같은 부모를 가진 카테고리끼리(대분류는 대분류끼리) 비교하는 값이다.
-- 같은 값이면 id 순이다. 앱은 새 카테고리를 형제 맨 뒤(최댓값 + 1)에 넣으므로 기본값 0은 SQL로 직접 행을 넣을 때만 쓰인다.
ALTER TABLE categories ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

-- 기존 행은 형제 안에서 등록 순(id)으로 번호를 매긴다.
UPDATE categories c
SET display_order = ranked.rn
FROM (
    SELECT id, row_number() OVER (PARTITION BY parent_category_id ORDER BY id) AS rn
    FROM categories
) ranked
WHERE c.id = ranked.id;
