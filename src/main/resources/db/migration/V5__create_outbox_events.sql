-- 서비스 간 이벤트의 트랜잭셔널 아웃박스(docs/adr/0021). 업무 변경과 같은 트랜잭션에서 쓰고, 릴레이가 id 순서로 Kafka에 보낸다.
-- payload는 Kafka 값으로 나가는 JSON 그대로다. JSONB는 키 순서와 공백을 바꾸므로 TEXT로 둔다(재발행해도 같은 바이트).
CREATE TABLE outbox_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       UUID         NOT NULL UNIQUE,
    topic          VARCHAR(255) NOT NULL,
    message_key    VARCHAR(255) NOT NULL,
    payload        TEXT         NOT NULL,
    traceparent    VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    quarantined_at TIMESTAMPTZ,
    last_error     TEXT,
    CONSTRAINT outbox_events_attempts_check CHECK (attempts >= 0),
    -- 전송 완료와 격리는 동시에 일어나지 않는다
    CONSTRAINT outbox_events_published_or_quarantined CHECK (published_at IS NULL OR quarantined_at IS NULL)
);
-- 릴레이 대상 조회: 아직 안 보냈고 격리되지 않은 행을 id 순서로
CREATE INDEX outbox_events_pending_idx ON outbox_events (id) WHERE published_at IS NULL AND quarantined_at IS NULL;
-- 정리 배치 대상 조회: 전송이 끝난 지 오래된 행
CREATE INDEX outbox_events_published_idx ON outbox_events (published_at) WHERE published_at IS NOT NULL;
