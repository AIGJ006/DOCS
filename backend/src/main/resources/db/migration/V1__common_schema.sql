-- 출처: docs/51-erd-unified.md "## ERD 변경 제안" SQL 블록 그대로 (erd/V1__common_schema.sql은 저장소에 없음, README "알려진 누락"). 한 글자도 바꾸지 않는다 (constitution I).
-- 팀 공통 통합 V1: 20개 테이블, 148개 컬럼, 40개 FK. 빈 스키마에 한 번 적용하는 Flyway 기준선.
-- 기준: ERD Cloud "ai blog" 2026-10-07 상태(회원 프로필 컬럼, 신고 사건 종료 일자, 사진 원래 파일 이름 제거 포함). erd/erdcloud-export.sql은 2026-10-06 내보내기. ERD Cloud에 칸이 없는 UNIQUE·CHECK·ON DELETE·인덱스는 이 파일이 기준이다.
-- ERD Cloud DATETIME은 timestamptz로 변환; 현재 시각 기본값은 CURRENT_TIMESTAMP.
-- friendship은 포함하되 FRIENDS 공개 범위 및 친구 알림 종류는 활성화하지 않는다.
-- 신고는 report_case(사건) + report(신고)로 분리(2026-10-07 E1 채택). 동의는 member_agreement(2026-10-07 E2 채택, 버전 포함), 정지는 member_suspension으로 분리(2026-10-07 E3 채택).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 회원
CREATE TABLE member (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    handle                 varchar(39) NOT NULL,
    nickname               varchar(10) NULL,
    nickname_changed_at    timestamptz NULL,
    bio                    varchar(200) NULL,
    role                   varchar(20) NOT NULL DEFAULT 'USER',
    status                 varchar(20) NOT NULL DEFAULT 'ACTIVE',
    default_visibility     varchar(20) NOT NULL DEFAULT 'PUBLIC',
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_at           timestamptz NULL,
    last_active_at         timestamptz NULL,
    last_active_visible    boolean NOT NULL DEFAULT true,
    deleted_at             timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_member_handle UNIQUE (handle),
    CONSTRAINT ck_member_handle CHECK (handle ~ '^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$'),
    CONSTRAINT ck_member_nickname CHECK (nickname ~ '^[가-힣a-zA-Z0-9]{2,10}$' AND nickname ~ '[가-힣a-zA-Z]'),
    CONSTRAINT ck_member_bio CHECK (bio IS NULL OR char_length(bio) <= 200),
    CONSTRAINT ck_member_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_member_withdrawn CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)),
    CONSTRAINT ck_member_deleted CHECK (deleted_at IS NULL OR status = 'WITHDRAWN'),
    CONSTRAINT ck_member_nickname_null CHECK (nickname IS NOT NULL OR deleted_at IS NOT NULL),
    CONSTRAINT ck_member_default_visibility CHECK (default_visibility IN ('PUBLIC', 'PRIVATE'))
);

CREATE UNIQUE INDEX uq_member_nickname ON member (lower(nickname));

CREATE INDEX ix_member_withdraw_purge ON member (withdrawn_at) WHERE status = 'WITHDRAWN' AND deleted_at IS NULL;

CREATE INDEX ix_member_nickname_trgm ON member USING gin (nickname gin_trgm_ops);

CREATE INDEX ix_member_handle_trgm ON member USING gin (handle gin_trgm_ops);

COMMENT ON TABLE member IS '회원';

COMMENT ON COLUMN member.id IS '회원 번호';

COMMENT ON COLUMN member.handle IS '블로그 주소';

COMMENT ON COLUMN member.nickname IS '닉네임';

COMMENT ON COLUMN member.nickname_changed_at IS '닉네임 변경 일자';

COMMENT ON COLUMN member.bio IS '소개';

COMMENT ON COLUMN member.role IS '권한';

COMMENT ON COLUMN member.status IS '회원 상태';

COMMENT ON COLUMN member.default_visibility IS '기본 공개 범위';

COMMENT ON COLUMN member.created_at IS '생성 일자';

COMMENT ON COLUMN member.updated_at IS '수정 일자';

COMMENT ON COLUMN member.withdrawn_at IS '탈퇴 신청 일자';

COMMENT ON COLUMN member.last_active_at IS '최근 활동 일자';

COMMENT ON COLUMN member.last_active_visible IS '최근 활동 공개 여부';

COMMENT ON COLUMN member.deleted_at IS '익명 처리 일자';

-- 사진
CREATE TABLE image (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    uploader_id            bigint NOT NULL,
    storage_key            varchar(255) NOT NULL,
    thumb_storage_key      varchar(255) NULL,
    content_type           varchar(50) NOT NULL,
    size_bytes             integer NOT NULL,
    thumb_size_bytes       integer NULL,
    width                  integer NULL,
    height                 integer NULL,
    status                 varchar(20) NOT NULL DEFAULT 'TEMP',
    purpose                varchar(20) NOT NULL DEFAULT 'POST',
    detached_at            timestamptz NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_image_uploader FOREIGN KEY (uploader_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_image_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_image_thumb_key UNIQUE (thumb_storage_key),
    CONSTRAINT ck_image_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/gif', 'image/webp')),
    CONSTRAINT ck_image_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_image_status CHECK (status IN ('TEMP', 'ATTACHED')),
    CONSTRAINT ck_image_purpose CHECK (purpose IN ('POST', 'PROFILE')),
    CONSTRAINT ck_image_dim CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0)),
    CONSTRAINT ck_image_thumb_size CHECK (thumb_size_bytes IS NULL OR (thumb_size_bytes > 0 AND thumb_size_bytes <= 1048576))
);

CREATE INDEX ix_image_uploader ON image (uploader_id, created_at DESC);

CREATE INDEX ix_image_cleanup_temp ON image (created_at) WHERE status = 'TEMP';

CREATE INDEX ix_image_cleanup_detached ON image (detached_at) WHERE detached_at IS NOT NULL;

-- 회원 테이블은 사진을 가리키지 않는다(2026-10-07 결정). 지금 프로필 사진 = 이 조건의 행 하나, 목록은 이 인덱스로 JOIN
CREATE UNIQUE INDEX uq_image_profile_current ON image (uploader_id) WHERE purpose = 'PROFILE' AND status = 'ATTACHED' AND detached_at IS NULL;

COMMENT ON TABLE image IS '사진';

COMMENT ON COLUMN image.id IS '사진 번호';

COMMENT ON COLUMN image.uploader_id IS '올린 회원 번호';

COMMENT ON COLUMN image.storage_key IS '저장 경로';

COMMENT ON COLUMN image.thumb_storage_key IS '썸네일 경로';

COMMENT ON COLUMN image.content_type IS '파일 형식';

COMMENT ON COLUMN image.size_bytes IS '원본 크기';

COMMENT ON COLUMN image.thumb_size_bytes IS '썸네일 크기';

COMMENT ON COLUMN image.width IS '가로';

COMMENT ON COLUMN image.height IS '세로';

COMMENT ON COLUMN image.status IS '사진 상태';

COMMENT ON COLUMN image.purpose IS '용도';

COMMENT ON COLUMN image.detached_at IS '연결 해제 일자';

COMMENT ON COLUMN image.created_at IS '생성 일자';

-- 로그인 수단
CREATE TABLE auth_identity (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    member_id              bigint NOT NULL,
    provider               varchar(20) NOT NULL,
    provider_user_id       varchar(255) NOT NULL,
    email                  varchar(255) NULL,
    password_hash          varchar(100) NULL,
    email_verified_at      timestamptz NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at          timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_auth_identity_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_auth_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_auth_identity_member UNIQUE (member_id),
    CONSTRAINT ck_auth_provider CHECK (provider IN ('LOCAL', 'GITHUB', 'GOOGLE')),
    CONSTRAINT ck_auth_password CHECK ((provider = 'LOCAL') = (password_hash IS NOT NULL)),
    CONSTRAINT ck_auth_local_email CHECK (provider <> 'LOCAL' OR (email IS NOT NULL AND email = lower(email) AND provider_user_id = email))
);

CREATE INDEX ix_auth_identity_email ON auth_identity (email) WHERE email IS NOT NULL;

COMMENT ON TABLE auth_identity IS '로그인 수단';

COMMENT ON COLUMN auth_identity.id IS '로그인 수단 번호';

COMMENT ON COLUMN auth_identity.member_id IS '회원 번호';

COMMENT ON COLUMN auth_identity.provider IS '로그인 방식';

COMMENT ON COLUMN auth_identity.provider_user_id IS '로그인 식별값';

COMMENT ON COLUMN auth_identity.email IS '이메일';

COMMENT ON COLUMN auth_identity.password_hash IS '비밀번호 해시';

COMMENT ON COLUMN auth_identity.email_verified_at IS '이메일 인증 일자';

COMMENT ON COLUMN auth_identity.created_at IS '생성 일자';

COMMENT ON COLUMN auth_identity.last_login_at IS '마지막 로그인 일자';

-- 회원 동의 (ERD Cloud 2026-10-06: member의 동의 컬럼 3개를 행으로 분리)
CREATE TABLE member_agreement (
    member_id              bigint NOT NULL,
    type                   varchar(20) NOT NULL,
    version                varchar(20) NOT NULL,
    agreed_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, type),
    CONSTRAINT fk_member_agreement_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_member_agreement_type CHECK (type IN ('TERMS', 'PRIVACY', 'AI')),
    CONSTRAINT ck_member_agreement_version CHECK (length(btrim(version)) > 0)
);

COMMENT ON TABLE member_agreement IS '회원 동의';

COMMENT ON COLUMN member_agreement.member_id IS '회원 번호';

COMMENT ON COLUMN member_agreement.type IS '동의 종류';

COMMENT ON COLUMN member_agreement.version IS '동의 버전';

COMMENT ON COLUMN member_agreement.agreed_at IS '동의 일자';

-- 태그
CREATE TABLE tag (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    name                   varchar(30) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_tag_name UNIQUE (name),
    CONSTRAINT ck_tag_name CHECK (name ~ '^[가-힣a-z0-9._+#-]{1,30}$' AND name ~ '[가-힣a-z0-9]')
);

CREATE INDEX ix_tag_name_prefix ON tag (name varchar_pattern_ops);

COMMENT ON TABLE tag IS '태그';

COMMENT ON COLUMN tag.id IS '태그 번호';

COMMENT ON COLUMN tag.name IS '태그 이름';

COMMENT ON COLUMN tag.created_at IS '생성 일자';

-- 글
CREATE TABLE post (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    author_id              bigint NOT NULL,
    title                  varchar(100) NOT NULL DEFAULT '',
    content_md             text NOT NULL DEFAULT '',
    content_html           text NOT NULL DEFAULT '',
    excerpt                varchar(200) NULL,
    thumbnail_url          varchar(500) NULL,
    status                 varchar(20) NOT NULL DEFAULT 'DRAFT',
    visibility             varchar(20) NOT NULL DEFAULT 'PUBLIC',
    view_count             bigint NOT NULL DEFAULT 0,
    like_count             integer NOT NULL DEFAULT 0,
    comment_count          integer NOT NULL DEFAULT 0,
    edit_version           bigint NOT NULL DEFAULT 0,
    render_version         integer NOT NULL DEFAULT 1,
    published_at           timestamptz NULL,
    first_public_at        timestamptz NULL,
    edited_at              timestamptz NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at             timestamptz NULL,
    hidden_at              timestamptz NULL,
    hidden_by              bigint NULL,
    hidden_reason          varchar(30) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_post_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_post_published CHECK (status = 'DRAFT' OR (published_at IS NOT NULL AND length(btrim(title)) > 0)),
    CONSTRAINT ck_post_public_at CHECK (NOT (status = 'PUBLISHED' AND visibility = 'PUBLIC') OR first_public_at IS NOT NULL),
    CONSTRAINT ck_post_edited_at CHECK (edited_at IS NULL OR (published_at IS NOT NULL AND edited_at >= published_at)),
    CONSTRAINT ck_post_content CHECK (char_length(content_md) <= 100000),
    CONSTRAINT ck_post_counts CHECK (view_count >= 0 AND like_count >= 0 AND comment_count >= 0),
    CONSTRAINT fk_post_hidden_by FOREIGN KEY (hidden_by) REFERENCES member (id) ON DELETE RESTRICT
);

CREATE INDEX ix_post_feed ON post (first_public_at DESC, id DESC) WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL;

CREATE INDEX ix_post_blog ON post (author_id, first_public_at DESC, id DESC) WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL;

CREATE INDEX ix_post_manage ON post (author_id, status, updated_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX ix_post_trash ON post (author_id, deleted_at DESC) WHERE deleted_at IS NOT NULL;

CREATE INDEX ix_post_title_trgm ON post USING gin (title gin_trgm_ops);

CREATE INDEX ix_post_content_trgm ON post USING gin (content_md gin_trgm_ops);

COMMENT ON TABLE post IS '글';

COMMENT ON COLUMN post.id IS '글 번호';

COMMENT ON COLUMN post.author_id IS '작성자 번호';

COMMENT ON COLUMN post.title IS '제목';

COMMENT ON COLUMN post.content_md IS '본문 원문';

COMMENT ON COLUMN post.content_html IS '렌더링 본문';

COMMENT ON COLUMN post.excerpt IS '목록 요약';

COMMENT ON COLUMN post.thumbnail_url IS '썸네일 주소';

COMMENT ON COLUMN post.status IS '글 상태';

COMMENT ON COLUMN post.visibility IS '공개 범위';

COMMENT ON COLUMN post.view_count IS '조회 수';

COMMENT ON COLUMN post.like_count IS '좋아요 수';

COMMENT ON COLUMN post.comment_count IS '댓글 수';

COMMENT ON COLUMN post.edit_version IS '편집 버전';

COMMENT ON COLUMN post.render_version IS '렌더링 버전';

COMMENT ON COLUMN post.published_at IS '최초 발행 일자';

COMMENT ON COLUMN post.first_public_at IS '최초 공개 일자';

COMMENT ON COLUMN post.edited_at IS '재발행 일자';

COMMENT ON COLUMN post.created_at IS '생성 일자';

COMMENT ON COLUMN post.updated_at IS '수정 일자';

COMMENT ON COLUMN post.deleted_at IS '삭제 일자';

COMMENT ON COLUMN post.hidden_at IS '숨김 일자';

COMMENT ON COLUMN post.hidden_by IS '숨긴 관리자 번호';

COMMENT ON COLUMN post.hidden_reason IS '숨김 사유';

-- 글 작업본
CREATE TABLE post_draft (
    post_id                bigint NOT NULL,
    title                  varchar(100) NOT NULL DEFAULT '',
    content_md             text NOT NULL DEFAULT '',
    edit_version           bigint NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_draft_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_draft_content CHECK (char_length(content_md) <= 100000)
);

COMMENT ON TABLE post_draft IS '글 작업본';

COMMENT ON COLUMN post_draft.post_id IS '글 번호';

COMMENT ON COLUMN post_draft.title IS '작업 제목';

COMMENT ON COLUMN post_draft.content_md IS '작업 본문';

COMMENT ON COLUMN post_draft.edit_version IS '편집 버전';

COMMENT ON COLUMN post_draft.created_at IS '생성 일자';

COMMENT ON COLUMN post_draft.updated_at IS '수정 일자';

-- 글 좋아요
CREATE TABLE post_like (
    post_id                bigint NOT NULL,
    member_id              bigint NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, member_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT
);

CREATE INDEX ix_post_like_member ON post_like (member_id, created_at DESC);

COMMENT ON TABLE post_like IS '글 좋아요';

COMMENT ON COLUMN post_like.post_id IS '글 번호';

COMMENT ON COLUMN post_like.member_id IS '회원 번호';

COMMENT ON COLUMN post_like.created_at IS '생성 일자';

-- 일별 조회 수
CREATE TABLE post_view_daily (
    post_id                bigint NOT NULL,
    view_date              date NOT NULL,
    views                  integer NOT NULL,
    PRIMARY KEY (post_id, view_date),
    CONSTRAINT fk_post_view_daily_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_view_daily_views CHECK (views > 0)
);

CREATE INDEX ix_post_view_daily_date ON post_view_daily (view_date, post_id) INCLUDE (views);

COMMENT ON TABLE post_view_daily IS '일별 조회 수';

COMMENT ON COLUMN post_view_daily.post_id IS '글 번호';

COMMENT ON COLUMN post_view_daily.view_date IS '조회 일자';

COMMENT ON COLUMN post_view_daily.views IS '조회 수';

-- 글-태그 연결
CREATE TABLE post_tag (
    post_id                bigint NOT NULL,
    tag_id                 bigint NOT NULL,
    position               smallint NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE RESTRICT,
    CONSTRAINT uq_post_tag_position UNIQUE (post_id, position),
    CONSTRAINT ck_post_tag_position CHECK (position >= 0 AND position < 100)
);

CREATE INDEX ix_post_tag_tag ON post_tag (tag_id, post_id);

COMMENT ON TABLE post_tag IS '글-태그 연결';

COMMENT ON COLUMN post_tag.post_id IS '글 번호';

COMMENT ON COLUMN post_tag.tag_id IS '태그 번호';

COMMENT ON COLUMN post_tag.position IS '입력 순서';

-- 글-사진 연결
CREATE TABLE post_image (
    post_id                bigint NOT NULL,
    image_id               bigint NOT NULL,
    PRIMARY KEY (post_id, image_id),
    CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_image_image FOREIGN KEY (image_id) REFERENCES image (id) ON DELETE CASCADE
);

CREATE INDEX ix_post_image_image ON post_image (image_id);

COMMENT ON TABLE post_image IS '글-사진 연결';

COMMENT ON COLUMN post_image.post_id IS '글 번호';

COMMENT ON COLUMN post_image.image_id IS '사진 번호';

-- 댓글
CREATE TABLE comment (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    post_id                bigint NOT NULL,
    author_id              bigint NOT NULL,
    parent_id              bigint NULL,
    reply_to_member_id     bigint NULL,
    content                varchar(1000) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at             timestamptz NULL,
    hidden_at              timestamptz NULL,
    hidden_by              bigint NULL,
    hidden_reason          varchar(30) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_comment_reply_to_member FOREIGN KEY (reply_to_member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_comment_post_id UNIQUE (post_id, id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (post_id, parent_id) REFERENCES comment (post_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_comment_content CHECK (deleted_at IS NOT NULL OR length(btrim(content)) > 0),
    CONSTRAINT ck_comment_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_comment_reply_to CHECK (reply_to_member_id IS NULL OR parent_id IS NOT NULL),
    CONSTRAINT ck_comment_edited CHECK (updated_at >= created_at),
    CONSTRAINT fk_comment_hidden_by FOREIGN KEY (hidden_by) REFERENCES member (id) ON DELETE RESTRICT
);

CREATE INDEX ix_comment_root ON comment (post_id, created_at, id) WHERE parent_id IS NULL;

CREATE INDEX ix_comment_reply ON comment (parent_id, created_at, id) WHERE parent_id IS NOT NULL;

CREATE INDEX ix_comment_author ON comment (author_id);

COMMENT ON TABLE comment IS '댓글';

COMMENT ON COLUMN comment.id IS '댓글 번호';

COMMENT ON COLUMN comment.post_id IS '글 번호';

COMMENT ON COLUMN comment.author_id IS '작성자 번호';

COMMENT ON COLUMN comment.parent_id IS '부모 댓글 번호';

COMMENT ON COLUMN comment.reply_to_member_id IS '답글 대상 회원 번호';

COMMENT ON COLUMN comment.content IS '내용';

COMMENT ON COLUMN comment.created_at IS '생성 일자';

COMMENT ON COLUMN comment.updated_at IS '수정 일자';

COMMENT ON COLUMN comment.deleted_at IS '삭제 일자';

COMMENT ON COLUMN comment.hidden_at IS '숨김 일자';

COMMENT ON COLUMN comment.hidden_by IS '숨긴 관리자 번호';

COMMENT ON COLUMN comment.hidden_reason IS '숨김 사유';

-- 팔로우
CREATE TABLE follow (
    follower_id            bigint NOT NULL,
    followee_id            bigint NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_follow_self CHECK (follower_id <> followee_id)
);

CREATE INDEX ix_follow_followee ON follow (followee_id, created_at DESC);

CREATE INDEX ix_follow_follower ON follow (follower_id, created_at DESC);

COMMENT ON TABLE follow IS '팔로우';

COMMENT ON COLUMN follow.follower_id IS '팔로우하는 회원 번호';

COMMENT ON COLUMN follow.followee_id IS '팔로우받는 회원 번호';

COMMENT ON COLUMN follow.created_at IS '생성 일자';

-- 친구 관계
CREATE TABLE friendship (
    member_a_id            bigint NOT NULL,
    member_b_id            bigint NOT NULL,
    requested_by           bigint NOT NULL,
    status                 varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at            timestamptz NULL,
    PRIMARY KEY (member_a_id, member_b_id),
    CONSTRAINT fk_friendship_member_a FOREIGN KEY (member_a_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_friendship_member_b FOREIGN KEY (member_b_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_friendship_requested_by FOREIGN KEY (requested_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_friendship_order CHECK (member_a_id < member_b_id),
    CONSTRAINT ck_friendship_requester CHECK (requested_by IN (member_a_id, member_b_id)),
    CONSTRAINT ck_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT ck_friendship_accepted CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL))
);

CREATE INDEX ix_friendship_b ON friendship (member_b_id, status);

COMMENT ON TABLE friendship IS '친구 관계';

COMMENT ON COLUMN friendship.member_a_id IS '회원 A 번호';

COMMENT ON COLUMN friendship.member_b_id IS '회원 B 번호';

COMMENT ON COLUMN friendship.requested_by IS '요청 회원 번호';

COMMENT ON COLUMN friendship.status IS '관계 상태';

COMMENT ON COLUMN friendship.created_at IS '생성 일자';

COMMENT ON COLUMN friendship.accepted_at IS '수락 일자';

-- 신고 사건 (ERD Cloud 2026-10-06: 대상 단위 스냅샷·처리 상태를 신고에서 분리)
CREATE TABLE report_case (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    target_type            varchar(20) NOT NULL,
    post_id                bigint NULL,
    comment_id             bigint NULL,
    target_author_id       bigint NOT NULL,
    snapshot_title         varchar(100) NULL,
    snapshot_content       varchar(2000) NULL,
    status                 varchar(20) NOT NULL DEFAULT 'PENDING',
    handled_by             bigint NULL,
    handled_at             timestamptz NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_case_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE SET NULL,
    CONSTRAINT fk_report_case_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE SET NULL,
    CONSTRAINT fk_report_case_target_author FOREIGN KEY (target_author_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_case_handled_by FOREIGN KEY (handled_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_report_case_target CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT ck_report_case_target_ref CHECK ((target_type = 'POST' AND comment_id IS NULL) OR (target_type = 'COMMENT' AND post_id IS NULL)),
    CONSTRAINT ck_report_case_status CHECK (status IN ('PENDING', 'HIDDEN', 'REJECTED', 'CLOSED_NO_TARGET'))
);

CREATE INDEX ix_report_case_pending ON report_case (created_at) WHERE status = 'PENDING';

CREATE INDEX ix_report_case_post ON report_case (post_id) WHERE post_id IS NOT NULL;

CREATE INDEX ix_report_case_comment ON report_case (comment_id) WHERE comment_id IS NOT NULL;

-- E4(2026-10-07): 한 대상에 대기 중인 신고 사건은 하나. 동시에 들어온 첫 신고들이 사건을 둘 만들지 않게 한다
CREATE UNIQUE INDEX uq_report_case_open_post ON report_case (post_id) WHERE status = 'PENDING' AND post_id IS NOT NULL;

CREATE UNIQUE INDEX uq_report_case_open_comment ON report_case (comment_id) WHERE status = 'PENDING' AND comment_id IS NOT NULL;

CREATE INDEX ix_report_case_target_author ON report_case (target_author_id);

COMMENT ON TABLE report_case IS '신고 사건';

COMMENT ON COLUMN report_case.id IS '신고 사건 번호';

COMMENT ON COLUMN report_case.target_type IS '신고 대상 종류';

COMMENT ON COLUMN report_case.post_id IS '신고 글 번호';

COMMENT ON COLUMN report_case.comment_id IS '신고 댓글 번호';

COMMENT ON COLUMN report_case.target_author_id IS '대상 작성자 번호';

COMMENT ON COLUMN report_case.snapshot_title IS '신고 시점 제목';

COMMENT ON COLUMN report_case.snapshot_content IS '신고 시점 내용';

COMMENT ON COLUMN report_case.status IS '처리 상태';

COMMENT ON COLUMN report_case.handled_by IS '처리 관리자 번호';

COMMENT ON COLUMN report_case.handled_at IS '처리 일자';

COMMENT ON COLUMN report_case.created_at IS '생성 일자';

-- 신고 (사건 1개에 신고 1건 이상)
CREATE TABLE report (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    case_id                bigint NOT NULL,
    reporter_id            bigint NOT NULL,
    reason                 varchar(30) NOT NULL,
    detail                 varchar(200) NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_report_case FOREIGN KEY (case_id) REFERENCES report_case (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_report_case_reporter UNIQUE (case_id, reporter_id),
    CONSTRAINT ck_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'SEXUAL', 'PRIVACY', 'COPYRIGHT', 'OTHER')),
    CONSTRAINT ck_report_detail CHECK (reason <> 'OTHER' OR length(btrim(detail)) > 0)
);

CREATE INDEX ix_report_reporter ON report (reporter_id);

COMMENT ON TABLE report IS '신고';

COMMENT ON COLUMN report.id IS '신고 번호';

COMMENT ON COLUMN report.case_id IS '신고 사건 번호';

COMMENT ON COLUMN report.reporter_id IS '신고자 번호';

COMMENT ON COLUMN report.reason IS '신고 사유';

COMMENT ON COLUMN report.detail IS '신고 설명';

COMMENT ON COLUMN report.created_at IS '생성 일자';

-- 회원 정지 이력 (ERD Cloud 2026-10-06: member의 정지 컬럼 2개를 이력으로 분리)
CREATE TABLE member_suspension (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    member_id              bigint NOT NULL,
    reason                 varchar(200) NOT NULL,
    started_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at                timestamptz NULL,
    suspended_by           bigint NOT NULL,
    lifted_at              timestamptz NULL,
    lifted_by              bigint NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_suspension_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_suspension_suspended_by FOREIGN KEY (suspended_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_suspension_lifted_by FOREIGN KEY (lifted_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_member_suspension_period CHECK (ends_at IS NULL OR ends_at > started_at),
    CONSTRAINT ck_member_suspension_lift CHECK (lifted_by IS NULL OR lifted_at IS NOT NULL)
);

CREATE INDEX ix_member_suspension_member ON member_suspension (member_id, started_at DESC);

COMMENT ON TABLE member_suspension IS '회원 정지 이력';

COMMENT ON COLUMN member_suspension.id IS '정지 번호';

COMMENT ON COLUMN member_suspension.member_id IS '정지 회원 번호';

COMMENT ON COLUMN member_suspension.reason IS '정지 사유';

COMMENT ON COLUMN member_suspension.started_at IS '정지 시작 일자';

COMMENT ON COLUMN member_suspension.ends_at IS '정지 종료 일자';

COMMENT ON COLUMN member_suspension.suspended_by IS '정지 관리자 번호';

COMMENT ON COLUMN member_suspension.lifted_at IS '정지 해제 일자';

COMMENT ON COLUMN member_suspension.lifted_by IS '해제 관리자 번호';

-- 알림
CREATE TABLE notification (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    receiver_id            bigint NOT NULL,
    type                   varchar(30) NOT NULL,
    post_id                bigint NULL,
    comment_id             bigint NULL,
    report_id              bigint NULL,
    result                 varchar(20) NULL,
    last_actor_id          bigint NULL,
    actor_count            integer NOT NULL DEFAULT 0,
    group_key              varchar(100) NULL,
    read_at                timestamptz NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_last_actor FOREIGN KEY (last_actor_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST', 'REPORT_RESOLVED', 'CONTENT_HIDDEN')),
    CONSTRAINT ck_notification_group CHECK ((type IN ('LIKE', 'FOLLOW')) = (group_key IS NOT NULL)),
    CONSTRAINT ck_notification_result CHECK ((type = 'REPORT_RESOLVED') = (result IS NOT NULL) AND (result IS NULL OR result IN ('ACTION_TAKEN', 'NO_VIOLATION'))),
    CONSTRAINT ck_notification_count CHECK (actor_count >= 0),
    CONSTRAINT fk_notification_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_notification_unread_group ON notification (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL;

CREATE INDEX ix_notification_list ON notification (receiver_id, updated_at DESC, id DESC);

CREATE INDEX ix_notification_unread ON notification (receiver_id) WHERE read_at IS NULL;

CREATE INDEX ix_notification_post ON notification (post_id) WHERE post_id IS NOT NULL;

CREATE INDEX ix_notification_comment ON notification (comment_id) WHERE comment_id IS NOT NULL;

CREATE INDEX ix_notification_cleanup ON notification (updated_at);

CREATE INDEX ix_notification_last_actor ON notification (last_actor_id) WHERE group_key IS NULL;

COMMENT ON TABLE notification IS '알림';

COMMENT ON COLUMN notification.id IS '알림 번호';

COMMENT ON COLUMN notification.receiver_id IS '받는 회원 번호';

COMMENT ON COLUMN notification.type IS '알림 종류';

COMMENT ON COLUMN notification.post_id IS '관련 글 번호';

COMMENT ON COLUMN notification.comment_id IS '관련 댓글 번호';

COMMENT ON COLUMN notification.report_id IS '관련 신고 번호';

COMMENT ON COLUMN notification.result IS '신고 결과';

COMMENT ON COLUMN notification.last_actor_id IS '마지막 행동 회원 번호';

COMMENT ON COLUMN notification.actor_count IS '묶인 인원 수';

COMMENT ON COLUMN notification.group_key IS '묶음 키';

COMMENT ON COLUMN notification.read_at IS '읽은 일자';

COMMENT ON COLUMN notification.created_at IS '생성 일자';

COMMENT ON COLUMN notification.updated_at IS '수정 일자';

-- 알림에 묶인 사람
CREATE TABLE notification_actor (
    notification_id        bigint NOT NULL,
    actor_id               bigint NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id, actor_id),
    CONSTRAINT fk_notification_actor_notification FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor_actor FOREIGN KEY (actor_id) REFERENCES member (id) ON DELETE RESTRICT
);

CREATE INDEX ix_notification_actor_actor ON notification_actor (actor_id, created_at DESC);

COMMENT ON TABLE notification_actor IS '알림에 묶인 사람';

COMMENT ON COLUMN notification_actor.notification_id IS '알림 번호';

COMMENT ON COLUMN notification_actor.actor_id IS '행동 회원 번호';

COMMENT ON COLUMN notification_actor.created_at IS '생성 일자';

-- 끈 알림 종류
CREATE TABLE notification_mute (
    member_id              bigint NOT NULL,
    type                   varchar(30) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, type),
    CONSTRAINT fk_notification_mute_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_mute_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST'))
);

COMMENT ON TABLE notification_mute IS '끈 알림 종류';

COMMENT ON COLUMN notification_mute.member_id IS '회원 번호';

COMMENT ON COLUMN notification_mute.type IS '끈 알림 종류';

COMMENT ON COLUMN notification_mute.created_at IS '생성 일자';
