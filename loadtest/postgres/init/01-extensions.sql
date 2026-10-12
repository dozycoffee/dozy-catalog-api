-- 쿼리별 실행 시간을 보려고 pg_stat_statements를 켠다(새 볼륨의 첫 기동에만 실행된다).
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
