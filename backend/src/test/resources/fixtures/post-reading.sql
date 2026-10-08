-- 005 글 읽기 픽스처 (quickstart §1, tasks T004). 빈 DB에 적용한다(통합 테스트는 각 테스트 전에 표를 비운 뒤 적용).
-- 글·회원 번호는 고정하지 않는다 — 테스트는 PostReadingFixture가 제목·주소로 찾는다.
--
-- 회원 A(kim755030): 공개·노출 12개(수정 중 1, 다시 발행 1, PRIVATE→PUBLIC 공개 범위만 바꾼 글 1, 코드 블록 1,
--   제목에 "><script> 1, 썸네일 1) + PRIVATE 3, DRAFT 1, 휴지통 1, 관리자 숨김(PUBLIC) 1. 프로필 사진 있음
-- 회원 B(na_ms): 공개 8개(그중 2개는 first_public_at이 같은 마이크로초 — 홈 9·10번째 경계에 걸림)
-- 회원 C(kang_sc): 탈퇴 신청(WITHDRAWN) + 공개 2개 — 어디에도 안 나옴
-- 회원 D(empty_d): 공개 글 0개
-- 관리자(admin_ops): 숨김 처리자
-- 홈 기준 공개·노출 글 합계 20개 (A 12 + B 8)
-- 시각은 모두 과거 고정값(UTC, 마이크로초 포함) — 테스트가 지금 시각으로 발행하는 새 글은 항상 맨 위다.

INSERT INTO member (handle, nickname, bio, role, status) VALUES
    ('kim755030', '김민서', E'스프링 백엔드를 공부합니다.\n하루에 한 글씩 씁니다.', 'USER', 'ACTIVE');
INSERT INTO member (handle, nickname, bio, role, status) VALUES
    ('na_ms', '나민서', NULL, 'USER', 'ACTIVE');
INSERT INTO member (handle, nickname, bio, role, status, withdrawn_at) VALUES
    ('kang_sc', '강성찬', '탈퇴 신청한 회원', 'USER', 'WITHDRAWN', '2026-10-01 00:00:00+00');
INSERT INTO member (handle, nickname, bio, role, status) VALUES
    ('empty_d', '빈블로그', NULL, 'USER', 'ACTIVE');
INSERT INTO member (handle, nickname, bio, role, status) VALUES
    ('admin_ops', '운영자', NULL, 'ADMIN', 'ACTIVE');

-- 로그인 수단 (이메일 인증 완료). 비밀번호 없는 소셜 수단으로 둔다(ck_auth_password)
INSERT INTO auth_identity (member_id, provider, provider_user_id, email, email_verified_at)
SELECT id, 'GITHUB', 'fixture-' || handle, handle || '@example.com', '2026-08-01 00:00:00+00'
  FROM member WHERE handle IN ('kim755030', 'na_ms', 'kang_sc', 'empty_d', 'admin_ops');

-- 사진: 회원 A 프로필(썸네일 있음), 회원 A 글의 첫 사진(썸네일 키 = {uuid}_thumb.webp, 원본 = storage_key)
INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type, size_bytes, thumb_size_bytes,
                   width, height, status, purpose)
SELECT id, 'profiles/2026/09/a1b2c3d4.png', 'profiles/2026/09/a1b2c3d4_thumb.webp', 'image/png', 204800, 20480,
       800, 800, 'ATTACHED', 'PROFILE'
  FROM member WHERE handle = 'kim755030';
INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type, size_bytes, thumb_size_bytes,
                   width, height, status, purpose)
SELECT id, 'images/2026/09/3f2a9c1e-5b7d-4e2a-9c1f-0a1b2c3d4e5f.png', 'images/2026/09/3f2a9c1e-5b7d-4e2a-9c1f-0a1b2c3d4e5f_thumb.webp', 'image/png', 512000, 40960,
       1600, 900, 'ATTACHED', 'POST'
  FROM member WHERE handle = 'kim755030';

-- ---------------------------------------------------------------------------
-- 회원 A 공개·노출 12개 (최신순: 다시 발행, 코드, 수정 중, XSS 제목, 썸네일, 공개 전환, A07~A12)
-- ---------------------------------------------------------------------------
INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, view_count, like_count,
                  comment_count, edit_version, published_at, first_public_at, edited_at, created_at, updated_at)
SELECT id, 'JPA N+1 정리', '다시 발행한 본문', '<h2 id="n1">N+1</h2><p>다시 발행한 본문</p>',
       '지연 로딩으로 연관 엔티티를 조회할 때 생기는 N+1 문제를 정리했다', 'PUBLISHED', 'PUBLIC', 12345, 12, 3, 2,
       '2026-09-28 10:00:00.000001+00', '2026-09-28 10:00:00.000001+00', '2026-10-03 05:03:00+00',
       '2026-09-28 09:00:00+00', '2026-10-03 05:03:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, view_count, like_count,
                  comment_count, edit_version, published_at, first_public_at, created_at, updated_at)
SELECT id, '코드 블록이 있는 글', E'```java\nint x = 1;\n```',
       E'<pre><code class="language-java">int x = 1;\n</code></pre>', '코드 블록만 있는 글', 'PUBLISHED', 'PUBLIC',
       5, 0, 0, 1, '2026-09-26 10:00:00.000002+00', '2026-09-26 10:00:00.000002+00',
       '2026-09-26 09:00:00+00', '2026-09-26 10:00:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, view_count, like_count,
                  comment_count, edit_version, published_at, first_public_at, created_at, updated_at)
SELECT id, '수정 중인 글', '마지막 발행본', '<p>마지막 발행본</p>', '마지막 발행본', 'PUBLISHED', 'PUBLIC', 7, 1, 0, 1,
       '2026-09-25 10:00:00.000003+00', '2026-09-25 10:00:00.000003+00', '2026-09-25 09:00:00+00',
       '2026-09-25 10:00:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, view_count, like_count,
                  comment_count, edit_version, published_at, first_public_at, created_at, updated_at)
SELECT id, '제목 "><script>alert(1)</script>', '본문', '<p>본문</p>', '요약 "><img src=x onerror=alert(1)>',
       'PUBLISHED', 'PUBLIC', 0, 0, 0, 1, '2026-09-23 10:00:00.000004+00', '2026-09-23 10:00:00.000004+00',
       '2026-09-23 09:00:00+00', '2026-09-23 10:00:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, thumbnail_url, status, visibility,
                  view_count, like_count, comment_count, edit_version, published_at, first_public_at, created_at,
                  updated_at)
SELECT id, '사진이 있는 글', '![첫 사진](images/2026/09/3f2a9c1e-5b7d-4e2a-9c1f-0a1b2c3d4e5f.png)',
       '<p><img src="http://localhost:9000/blog/images/2026/09/3f2a9c1e-5b7d-4e2a-9c1f-0a1b2c3d4e5f.png" alt="첫 사진"></p>', '사진 한 장',
       'http://localhost:9000/blog/images/2026/09/3f2a9c1e-5b7d-4e2a-9c1f-0a1b2c3d4e5f_thumb.webp', 'PUBLISHED', 'PUBLIC', 0, 0, 0, 1,
       '2026-09-22 10:00:00.000005+00', '2026-09-22 10:00:00.000005+00', '2026-09-22 09:00:00+00',
       '2026-09-22 10:00:00+00'
  FROM member WHERE handle = 'kim755030';

-- PRIVATE로 발행했다가 공개 범위만 PUBLIC으로 바꾼 글: published_at < first_public_at, edited_at NULL
INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, view_count, like_count,
                  comment_count, edit_version, published_at, first_public_at, created_at, updated_at)
SELECT id, '나중에 공개한 글', '본문', '<p>본문</p>', '비공개로 발행했다가 공개로 바꾼 글', 'PUBLISHED', 'PUBLIC', 0, 0, 0, 1,
       '2026-09-01 10:00:00+00', '2026-09-19 10:00:00.654321+00', '2026-09-01 09:00:00+00',
       '2026-09-19 10:00:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, edit_version,
                  published_at, first_public_at, created_at, updated_at)
SELECT m.id, v.title, '본문', '<p>본문</p>', v.excerpt, 'PUBLISHED', 'PUBLIC', 1, v.at, v.at, v.at, v.at
  FROM member m,
       (VALUES ('A07 글', '일곱 번째', TIMESTAMPTZ '2026-09-18 10:00:00.000007+00'),
               ('A08 글', '여덟 번째', TIMESTAMPTZ '2026-09-16 10:00:00.000008+00'),
               ('A09 글', '아홉 번째', TIMESTAMPTZ '2026-09-15 10:00:00.000009+00'),
               ('A10 글', '열 번째', TIMESTAMPTZ '2026-09-13 10:00:00.000010+00'),
               ('A11 글', '열한 번째', TIMESTAMPTZ '2026-09-12 10:00:00.000011+00'),
               ('A12 글', NULL, TIMESTAMPTZ '2026-09-10 10:00:00.000012+00')) AS v(title, excerpt, at)
 WHERE m.handle = 'kim755030';

-- 수정 중인 글의 작업본 (마지막 저장 2026-10-03 14:03 KST)
INSERT INTO post_draft (post_id, title, content_md, edit_version, created_at, updated_at)
SELECT p.id, '고치는 중인 제목', '고치는 중인 본문', 2, '2026-10-03 05:00:00+00', '2026-10-03 05:03:00+00'
  FROM post p WHERE p.title = '수정 중인 글';

-- 다시 발행한 글의 태그 (입력 순서 position: spring, jpa, 성능 — 태그 번호 순서와 다르게)
INSERT INTO tag (name) VALUES ('jpa');
INSERT INTO tag (name) VALUES ('성능');
INSERT INTO tag (name) VALUES ('spring');
INSERT INTO post_tag (post_id, tag_id, position)
SELECT p.id, t.id, CASE t.name WHEN 'spring' THEN 0 WHEN 'jpa' THEN 1 ELSE 2 END
  FROM post p, tag t
 WHERE p.title = 'JPA N+1 정리' AND t.name IN ('spring', 'jpa', '성능');

-- ---------------------------------------------------------------------------
-- 회원 A의 목록에 나오지 않는 글 (모두 공개 글보다 최신 시각이라 노출 조건이 빠지면 맨 위에 나온다)
-- ---------------------------------------------------------------------------
INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, edit_version,
                  published_at, first_public_at, created_at, updated_at)
SELECT m.id, v.title, '비공개 본문', '<p>비공개 본문</p>', '비공개 요약', 'PUBLISHED', 'PRIVATE', 1, v.at, NULL, v.at, v.at
  FROM member m,
       (VALUES ('비공개 글 1', TIMESTAMPTZ '2026-09-30 10:00:00+00'),
               ('비공개 글 2', TIMESTAMPTZ '2026-09-29 10:00:00+00'),
               ('비공개 글 3', TIMESTAMPTZ '2026-09-27 11:00:00+00')) AS v(title, at)
 WHERE m.handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, status, visibility, edit_version, created_at,
                  updated_at)
SELECT id, '임시글', '쓰는 중', '', 'DRAFT', 'PUBLIC', 0, '2026-10-02 00:00:00+00', '2026-10-02 00:00:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, edit_version,
                  published_at, first_public_at, created_at, updated_at, deleted_at)
SELECT id, '휴지통 글', '본문', '<p>본문</p>', '휴지통 요약', 'PUBLISHED', 'PUBLIC', 1, '2026-09-30 11:00:00+00',
       '2026-09-30 11:00:00+00', '2026-09-30 11:00:00+00', '2026-10-01 00:00:00+00', '2026-10-01 00:00:00+00'
  FROM member WHERE handle = 'kim755030';

INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, edit_version,
                  published_at, first_public_at, created_at, updated_at, hidden_at, hidden_by, hidden_reason)
SELECT a.id, '숨겨진 글', '본문', '<p>숨겨진 본문</p>', '숨김 요약', 'PUBLISHED', 'PUBLIC', 1, '2026-09-30 12:00:00+00',
       '2026-09-30 12:00:00+00', '2026-09-30 12:00:00+00', '2026-10-01 00:00:00+00', '2026-10-01 00:00:00+00',
       o.id, 'SPAM'
  FROM member a, member o
 WHERE a.handle = 'kim755030' AND o.handle = 'admin_ops';

-- ---------------------------------------------------------------------------
-- 회원 B 공개 8개 (B 같은 시각 1·2는 first_public_at이 같은 마이크로초)
-- ---------------------------------------------------------------------------
INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, like_count,
                  comment_count, edit_version, published_at, first_public_at, created_at, updated_at)
SELECT m.id, v.title, '본문', '<p>본문</p>', v.title || ' 요약', 'PUBLISHED', 'PUBLIC', 0, 0, 1, v.at, v.at, v.at, v.at
  FROM member m,
       (VALUES ('B1 글', TIMESTAMPTZ '2026-09-27 10:00:00.500000+00'),
               ('B2 글', TIMESTAMPTZ '2026-09-24 10:00:00.500000+00'),
               ('B3 글', TIMESTAMPTZ '2026-09-21 10:00:00.500000+00'),
               ('B 같은 시각 2', TIMESTAMPTZ '2026-09-20 10:00:00.123456+00'),
               ('B 같은 시각 1', TIMESTAMPTZ '2026-09-20 10:00:00.123456+00'),
               ('B4 글', TIMESTAMPTZ '2026-09-17 10:00:00.500000+00'),
               ('B5 글', TIMESTAMPTZ '2026-09-14 10:00:00.500000+00'),
               ('B6 글', TIMESTAMPTZ '2026-09-11 10:00:00.500000+00')) AS v(title, at)
 WHERE m.handle = 'na_ms';

-- ---------------------------------------------------------------------------
-- 회원 C(탈퇴 신청) 공개 2개 — 목록·상세 모두 안 나옴
-- ---------------------------------------------------------------------------
INSERT INTO post (author_id, title, content_md, content_html, excerpt, status, visibility, edit_version,
                  published_at, first_public_at, created_at, updated_at)
SELECT m.id, v.title, '본문', '<p>본문</p>', '탈퇴 회원 요약', 'PUBLISHED', 'PUBLIC', 1, v.at, v.at, v.at, v.at
  FROM member m,
       (VALUES ('C1 글', TIMESTAMPTZ '2026-09-29 12:00:00+00'),
               ('C2 글', TIMESTAMPTZ '2026-09-22 12:00:00+00')) AS v(title, at)
 WHERE m.handle = 'kang_sc';
