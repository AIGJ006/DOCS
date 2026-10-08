-- 004 US8 (선택 구현) 친구 공개 FRIENDS 적용 (data-model §5, research R-14).
-- 공통 빌드는 이 파일을 읽지 않는다. friends 프로필(application-friends.yml)이 Flyway locations에 classpath:db/friends를 더할 때만 적용된다.
-- 공통 V1 CHECK는 PUBLIC·PRIVATE만 허용하므로 FRIENDS를 더하고, 친구가 보는 블로그 목록(published_at DESC, id DESC)용 부분 인덱스를 만든다.
ALTER TABLE post DROP CONSTRAINT ck_post_visibility;
ALTER TABLE post ADD CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'));

ALTER TABLE member DROP CONSTRAINT ck_member_default_visibility;
ALTER TABLE member ADD CONSTRAINT ck_member_default_visibility CHECK (default_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'));

CREATE INDEX ix_post_blog_friends ON post (author_id, published_at DESC, id DESC) WHERE status = 'PUBLISHED' AND visibility IN ('PUBLIC', 'FRIENDS') AND deleted_at IS NULL AND hidden_at IS NULL;
