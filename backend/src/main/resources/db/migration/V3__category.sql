-- 017 2단계 카테고리 (나민서 개인 확장, 01 §2-4, 02 §7 "post에 nullable 컬럼 추가 + category 모듈").
-- 공통 ERD는 바꾸지 않고 새 테이블·nullable 컬럼·인덱스만 더한다(constitution I). data-model §1·§2.

CREATE TABLE category (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    member_id   bigint NOT NULL,
    parent_id   bigint NULL,
    name        varchar(30) NOT NULL,
    name_key    varchar(60) NOT NULL,
    position    integer NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_category_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT ck_category_name CHECK (char_length(name) BETWEEN 1 AND 30),
    CONSTRAINT ck_category_parent_self CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_category_position CHECK (position >= 0)
);

-- 같은 상위(최상위끼리 포함) 안에서 대소문자 무시 이름 유일 (FR-004)
CREATE UNIQUE INDEX uq_category_sibling_name ON category (member_id, COALESCE(parent_id, 0), name_key);

CREATE INDEX ix_category_member ON category (member_id, parent_id, position);

CREATE INDEX ix_category_parent ON category (parent_id) WHERE parent_id IS NOT NULL;

-- 글의 카테고리. 카테고리를 지우면 글은 분류 없음 (FR-011)
ALTER TABLE post ADD COLUMN category_id bigint NULL;

ALTER TABLE post
    ADD CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE SET NULL;

CREATE INDEX ix_post_category ON post (category_id, first_public_at DESC, id DESC)
    WHERE category_id IS NOT NULL AND deleted_at IS NULL;
