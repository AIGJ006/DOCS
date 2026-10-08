-- 공개 주소 변경 후 글 썸네일 주소 바꾸기 (003 T086, quickstart §6 4단계, research R17, FR-031)
--
-- 사용: psql "$DATABASE_URL" -v old=https://옛주소/blog -v new=https://새주소 -f scripts/sql/rebase-image-urls.sql
--   - old·new는 끝에 / 없이 쓴다. old로 "시작하고 바로 뒤가 /"인 post.thumbnail_url만 바꾼다.
--   - 먼저 quickstart §6 1~3단계(새 주소 확인 → 설정 교체·재기동 → 발행 글 다시 렌더링)를 끝낸다.
--   - 본문(content_md·content_html)은 바꾸지 않는다. 본문은 다시 렌더링이 지금 주소로 만든다.
--   - 바뀐 행 수(changed_rows)를 출력한다. 한 트랜잭션이고, 오류가 나면 멈추고 아무것도 바꾸지 않는다.
\set ON_ERROR_STOP on
BEGIN;
WITH changed AS (
    UPDATE post
       SET thumbnail_url = :'new' || substr(thumbnail_url, length(:'old') + 1)
     WHERE left(thumbnail_url, length(:'old') + 1) = :'old' || '/'
    RETURNING id
)
SELECT count(*) AS changed_rows FROM changed;
COMMIT;
