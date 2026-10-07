# Quickstart: 006-manage-delete 검증 시나리오

**Feature**: `006-manage-delete` | **Date**: 2026-10-07

이 문서는 기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드와 마이그레이션은 넣지 않는다. API 형식은 [contracts/openapi.yaml](./contracts/openapi.yaml), 이벤트·배치는 [contracts/events.md](./contracts/events.md), 테이블·상태 전이는 [data-model.md](./data-model.md)를 본다.

## 0. 사전 조건

- Docker와 Docker Compose v2, JDK 21, Node.js LTS가 필요하다.
- 공통 시작 템플릿(O9)이 있어야 한다: `docker-compose.yml`(app + PostgreSQL + Redis + MinIO), Flyway V1(51 통합 ERD).
- 선행 기능이 필요하다.
  - 001: 로그인 세션·CSRF
  - 002: 새 글·자동 저장·발행
  - 004: `PostAccessPolicy`·`VisibilityFilter`
- 아래 기능은 있으면 함께 확인한다. 없으면 해당 단계를 건너뛴다.
  - 007 댓글
  - 009 좋아요
  - 003 사진
  - 014 신고
- 관련 설정(`application.yml`, 기본값):
  ```yaml
  blog:
    manage.page-size: 20
    post.trash:
      retention: P30D
      purge-cron: "0 30 3 * * *"
      purge-batch-size: 100
  ```

## 1. 기동

```bash
docker compose up -d postgres redis minio
./mvnw -pl backend spring-boot:run          # 또는 docker compose up -d app
(cd frontend && npm ci && npm run dev)      # 화면 확인용
```

예상 결과:

- `GET http://localhost:8080/actuator/health`가 `UP`이다.
- Flyway 로그에 이 기능의 새 마이그레이션이 없다(스키마 변경 없음).

## 2. 자동 테스트 (기본 검증 경로)

Testcontainers가 PostgreSQL과 Redis를 띄운다. H2는 쓰지 않는다(헌법 VIII).

```bash
./mvnw -pl backend verify -Dit.test='ManagePostApiIT,PostTrashApiIT,PostPurgeIT,TrashPurgeJobIT,TrashConcurrencyIT,TrashedPostPermissionMatrixIT'
```

| 테스트 | 확인하는 것 (spec) | 기대 결과 |
|---|---|---|
| `ManagePostApiIT` | US3 전체, FR-001~012, SC-005·006·007 | 기본 탭은 drafts이고 `counts = {3, 24, 1}`이다. 요청에 `authorId=다른회원`을 넣어도 본인 글만 나온다. 발행 글 25개는 20개 + [더 보기] 5개로 나오고 중복이 0이다. `visibility=private` 필터가 맞게 걸린다. 응답 JSON에 `contentMd`·`contentHtml` 키가 없다. 회원 1명이 글 1만 건을 가진 시드에서 응답이 300ms 이내다. EXPLAIN에 `ix_post_manage`·`ix_post_trash`가 나온다 |
| `PostTrashApiIT` | US1·US2·US4-1·2, FR-017~029 | 공개 글 삭제는 200 `{trashed, purgeAt = deletedAt+30d}`이다. 빈 임시글 삭제는 `{purged:true}`이고 행이 없다. 남의 글과 없는 글은 404이며 요청 전후 DB 행이 같다. 다시 삭제하면 200이고 `purgeAt`이 같다. 휴지통 글에 대한 자동 저장·발행·공개 범위 변경은 404다. 복구하면 status·visibility·`first_public_at`·`updated_at`·`hidden_at`·댓글 수·좋아요 수·태그가 삭제 전과 같다. 휴지통에 없는 글의 복구·영구 삭제는 404다. 인증 전 회원은 200, 탈퇴 유예 회원은 403 `ACCOUNT_WITHDRAWN`, 비회원은 401 `LOGIN_REQUIRED`다 |
| `PostPurgeIT` | US4-1·4·5·6·7, FR-031~035 | 영구 삭제 뒤 `comment`·`post_like`·`post_tag`·`post_image`·`post_draft`·`post_view_daily`·`notification`에 그 글의 행이 0건이다. `tag` 행은 남는다. 그 글에서만 쓰던 사진은 `detached_at`이 채워지고, 공유 사진은 NULL 그대로다. PENDING 신고 사건(글·댓글 대상)은 `CLOSED_NO_TARGET`, `handled_at` 있음, `handled_by` NULL이고 `post_id`·`comment_id`는 NULL이다. 새 글의 id가 지운 id와 겹치지 않는다 |
| `TrashPurgeJobIT` | US4-3, FR-030, SC-003 | `deleted_at`이 31일 전·29일 전인 글로 배치를 한 번 실행하면 31일 전 글만 사라진다. 250개를 시드하면 100개씩 묶어 모두 처리된다. 실행 중 복구된 글은 남는다. 서버 두 개를 흉내 내 동시에 실행해도 ShedLock 덕분에 한 번만 처리된다 |
| `TrashConcurrencyIT` | FR-036, 엣지 케이스 | 같은 글에 삭제·복구·영구 삭제를 각 10개 스레드로 동시에 보내도 최종 상태가 하나로 정해지고 예외 500이 0건이다. `PostTrashed` 발행 수는 실제 상태 변화 수와 같다 |
| `TrashedPostPermissionMatrixIT` | US1-1, FR-021·039, SC-001 | "휴지통 글" 상태를 비회원·다른 회원·작성자·관리자가 볼 때 상세·홈·블로그·블로그 글 수·태그·검색·sitemap·댓글·좋아요가 모두 404 또는 미포함이다. 작성자에게는 관리 목록의 휴지통 탭에만 보인다 |
| 자동 저장 경합 (`PostTrashApiIT#trash_flushesPendingAutosave`) | US1-7, FR-024 | Redis에 version 13 버퍼가 있는 상태에서 삭제하면 DB(`post` 또는 `post_draft`)에 version 13 내용이 있고, 커밋 후 Redis 키가 없다. Redis를 멈춘 상태에서 삭제해도 200이다(경고 로그) |

## 3. 수동 확인 (curl)

로그인과 CSRF 토큰 받기는 001 계약을 따른다. 아래는 쿠키 저장 방식(M17 확정 전 가정)으로 쓴 예시다.

- `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더
- 로그인 요청 경로는 001 contracts에 있다

```bash
B=http://localhost:8080; J=cookies.txt
# (001) 작성자 A로 로그인해 $J에 SESSION·XSRF-TOKEN 저장
X=$(awk '$6=="XSRF-TOKEN"{print $7}' $J)

# 1) 관리 목록 첫 페이지 — counts 포함, 본문 없음
curl -s -b $J "$B/api/me/posts?tab=published" | jq '{n: (.items|length), counts, nextCursor, keys: (.items[0]|keys)}'
#   기대: counts 객체 있음, keys에 contentMd/contentHtml 없음

# 2) 공개 글 42를 휴지통으로
curl -s -b $J -X DELETE -H "X-XSRF-TOKEN: $X" "$B/api/posts/42" | jq
#   기대: {"trashed": true, "purgeAt": "<삭제 시각 + 30일>"}
curl -s -o /dev/null -w '%{http_code}\n' -b $J "$B/api/posts/42"            # 상세(005 API) → 404 (작성자에게도)
curl -s "$B/api/posts" | jq '[.items[].id] | index(42)'                      # 홈 → null
curl -s -b $J "$B/api/me/posts?tab=trash" | jq '.items[0] | {id, status, deletedAt, purgeAt}'

# 3) 다시 삭제 → 변화 없음
curl -s -b $J -X DELETE -H "X-XSRF-TOKEN: $X" "$B/api/posts/42" | jq .purgeAt  # 2)와 같은 값

# 4) 복구 → 원래 위치
curl -s -b $J -X POST -H "X-XSRF-TOKEN: $X" "$B/api/posts/42/restore" | jq   # {"restored":true,"status":"PUBLISHED","visibility":"PUBLIC"}
curl -s "$B/api/posts" | jq '[.items[].id] | index(42)'                      # 삭제 전과 같은 위치

# 5) 영구 삭제 — 휴지통에 없으면 404, 넣은 뒤에는 200
curl -s -o /dev/null -w '%{http_code}\n' -b $J -X DELETE -H "X-XSRF-TOKEN: $X" "$B/api/posts/42/permanent"   # 404
curl -s -b $J -X DELETE -H "X-XSRF-TOKEN: $X" "$B/api/posts/42" >/dev/null
curl -s -b $J -X DELETE -H "X-XSRF-TOKEN: $X" "$B/api/posts/42/permanent" | jq                           # {"purged": true}

# 6) 다른 회원 B의 세션(cookiesB.txt)으로 A의 글 43 삭제 시도 → 404, 글은 그대로
XB=$(awk '$6=="XSRF-TOKEN"{print $7}' cookiesB.txt)
curl -s -b cookiesB.txt -X DELETE -H "X-XSRF-TOKEN: $XB" "$B/api/posts/43" | jq .code   # "NOT_FOUND"

# 7) 잘못된 커서 → 400
curl -s -b $J "$B/api/me/posts?tab=drafts&cursor=abc" | jq .code            # "INVALID_CURSOR"
```

DB에서 확인하기 (5번 뒤):

```bash
docker compose exec postgres psql -U blog -d blog -c "
  SELECT (SELECT count(*) FROM post WHERE id=42) post,
         (SELECT count(*) FROM comment WHERE post_id=42) comments,
         (SELECT count(*) FROM post_like WHERE post_id=42) likes,
         (SELECT count(*) FROM post_tag WHERE post_id=42) tags,
         (SELECT count(*) FROM report_case WHERE status='PENDING' AND post_id IS NULL AND target_type='POST') open_orphans;"
# 기대: 모두 0
```

30일 배치 수동 실행 (개발 프로필):

```bash
docker compose exec postgres psql -U blog -d blog -c "UPDATE post SET deleted_at = now() - interval '31 days' WHERE id = 44;"
# 개발 프로필에서 purge-cron을 잠시 '*/10 * * * * *'로 바꿔 기동하거나 TrashPurgeJobIT로 확인
docker compose exec postgres psql -U blog -d blog -c "SELECT count(*) FROM post WHERE id = 44;"   # 기대: 0
```

## 4. 화면 확인 (375px·데스크톱)

1. 로그아웃 상태로 `/manage/posts?tab=trash`를 연다 → 로그인 화면으로 간다 → 로그인하면 같은 주소로 돌아온다.
2. 탭 머리에 `임시글 3 · 발행 글 24 · 휴지통 1`이 보이고 기본 탭은 [임시글]이다.
3. 발행 글 [삭제]를 누르면 "휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요"가 뜬다 → [휴지통으로]를 누르면 그 줄만 빠지고 휴지통 수가 +1 된다.
4. 휴지통 탭에서 "(발행 글이었음) · 삭제 10월 2일 · N일 뒤 완전 삭제"가 보인다 → [복구]를 누르면(확인창 없음) "복구했어요 [발행 글 탭에서 보기]"가 뜬다.
5. 다른 탭에서 이미 휴지통으로 옮긴 글의 [삭제]·[복구]를 누르면 그 줄 아래에 이유가 보이고 목록을 다시 불러온다.
6. 375px 폭에서 가로 스크롤이 없다.
