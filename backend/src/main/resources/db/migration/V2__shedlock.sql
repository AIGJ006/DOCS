-- 추가 제안(공통 기반 001 T012): 배치 실행 잠금 테이블. 업무 데이터가 아닌 인프라 테이블이며
-- 002·003·006·014·015 배치가 함께 쓴다 (002 data-model §1-6, 006 R13, README "배치 잠금").
CREATE TABLE shedlock (
    name                   varchar(64) NOT NULL PRIMARY KEY,
    lock_until             timestamptz NOT NULL,
    locked_at              timestamptz NOT NULL,
    locked_by              varchar(255) NOT NULL
);

COMMENT ON TABLE shedlock IS '배치 실행 잠금 (ShedLock JDBC)';
