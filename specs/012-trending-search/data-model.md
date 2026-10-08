# Data Model: 트렌딩·검색

**Feature**: 012-trending-search | **Date**: 2026-10-08 | 스키마 변경 없음(V1), 새로 저장하는 데이터 없음

## 1. 읽는 테이블·인덱스 (V1)

| 테이블 | 컬럼 | 쓰는 곳 | 인덱스 |
|---|---|---|---|
| `post` | `id, author_id, title, content_md, excerpt, thumbnail_url, status, visibility, like_count, view_count, comment_count, first_public_at, edited_at, deleted_at, hidden_at` | 트렌딩 계산·카드·검색·sitemap | `ix_post_feed`, `ix_post_blog`, `ix_post_title_trgm`, `ix_post_content_trgm` |
| `member` | `id, handle, nickname, bio, withdrawn_at, deleted_at` | 공용 조건(`m.withdrawn_at IS NULL`)·카드 작성자·사람 검색 | `uq_member_handle`, `ix_member_nickname_trgm`, `ix_member_handle_trgm` |
| `image` | `uploader_id, purpose, status, detached_at, storage_key, thumb_storage_key` | 프로필 사진(005 카드와 같은 술어) | `uq_image_profile_current` |
| `comment` | `post_id, author_id, deleted_at, hidden_at` | 남의 댓글 작성자 수 | `uq_comment_post_id (post_id, id)` |
| `post_tag`·`tag` | `post_id, tag_id` / `id, name` | 태그 검색 | `ix_post_tag_tag`, `uq_tag_name`, `ix_tag_name_prefix` |

노출 조건은 모두 004 `VisibilityFilter.forViewer(Viewer.anonymous(), authorId)`:
`p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL AND p.hidden_at IS NULL AND m.withdrawn_at IS NULL [AND p.author_id = :authorId]`.

## 2. Redis 키

| 키 | 타입 | 값 | TTL | 쓰는 곳 |
|---|---|---|---|---|
| `trending:current` | String | 최신 스냅샷 ID `yyyyMMddHHmm`(UTC) | 없음 | 읽기 시작점 |
| `trending:{snapshotId}` | List | 글 번호(순위 순, 최대 100) | 1,800초 | `LRANGE` |
| `trending:{snapshotId}:count` | String | 글 수(0 가능) | 1,800초 | 만료·빈 순위 구분 |
| `ratelimit:search:{visitorKey}` | 001 `RateLimiter` | 1분 창 횟수 | 1분 | 글·사람 검색 합산 |

`visitorKey` = 009 `VisitorKeyResolver`의 `m:{회원 번호}` / `v:{vid 쿠키}` / `h:{SHA-256(IP + UA + 하루 비밀값)}`.

## 3. 값 객체 (discovery.application)

```java
/** 정규화한 검색어 (R6). words는 1~5개, 각 2 코드 포인트 이상. */
public record SearchQuery(List<SearchWord> words, boolean hasTwoCharWord) {
    public SearchWord longest() { … }          // 같으면 앞 단어
    public String fingerprint() { … }          // SHA-256(words joined by \u0000) 앞 16자
}
public record SearchWord(String text, int length) {
    public boolean inContent() { return length >= 3; }
}

public enum SearchSort { RELEVANCE, LATEST }

/** 관련도순 단계. LATEST는 ANY 하나. */
public enum SearchStage { TITLE, TITLE_OR_TAG, ANYWHERE, ANY }

public record Snippet(String text, List<int[]> marks) {}     // marks: [start, end) UTF-16 단위

public record PeopleQuery(String token) {}                  // 공백·맨 앞 @ 제거, 2 코드 포인트 이상
```

## 4. 커서

| 목록 | `ListScope` 값 | 키 `k` | 비고 |
|---|---|---|---|
| 트렌딩 | `trending` | `[snapshotId, position]` | 스냅샷이 만료되면 410 |
| 글 검색 | `search:{relevance\|latest}:{blogOwnerId\|-}:{fingerprint}` | `[stage, first_public_at 마이크로초, id]` | 검색어·정렬·블로그가 다르면 400 `INVALID_CURSOR` |
| 사람 검색 | — | — | 최대 20명, 커서 없음 |

001 `CursorCodec`(불투명 Base64URL, 최대 512자).

## 5. 응답 모델

```java
/** 트렌딩: 005 PostCardPage와 같은 모양. */
public record CursorPage<PostCardView>(List<PostCardView> items, String nextCursor) {}

public record PostSearchPage(
        List<PostSearchItem> items,
        String nextCursor,
        String notice) {}                       // "TWO_CHAR_TITLE_TAG_ONLY" 또는 null

public record PostSearchItem(PostCardView card, Snippet snippet) {}   // JSON은 카드 필드 + snippet (평평하게)

public record PeopleSearchResult(List<PersonItem> items) {}

public record PersonItem(String handle, String nickname, String profileImageUrl, String bioFirstLine) {}
```

## 6. 이유 코드 (`DiscoveryReasonCode`, 제안)

| 코드 | 상태 | 메시지 | 언제 |
|---|---|---|---|
| `SNAPSHOT_EXPIRED` | 410 | 순위가 새로 바뀌었어요 | 커서의 트렌딩 스냅샷이 사라짐 |
| `SEARCH_QUERY_TOO_SHORT` | 400 | 두 글자 이상 입력해 주세요 | 정리 뒤 남는 단어가 없음(글), 덩어리가 2글자 미만(사람) |

공통: 400 `INVALID_CURSOR`, 404 `NOT_FOUND`(블로그 안 검색), 429 `TOO_MANY_REQUESTS`, 403 `ACCOUNT_WITHDRAWN`(001 게이트).

## 7. 설정값

| 접두어 | 클래스 | 키 |
|---|---|---|
| `blog.trending` | `TrendingProperties` | `window`(7d), `refresh-cron`, `snapshot-size`(100), `snapshot-ttl`(30m), `per-author`(3), `weight-like`(3), `weight-commenter`(2), `weight-view`(0.1), `offset-hours`(2), `gravity`(1.5), `read-chunk`(18) |
| `blog.search` | `SearchProperties` | `max-length`(50), `max-words`(5), `recent-window`(3000), `snippet-radius`(40), `people-limit`(20), `rate-limit.limit`(30)·`window`(1m) |

## 8. 트렌딩 점수

```text
score = (3 × like_count + 2 × commenters + 0.1 × view_count) / (경과 시간(시간) + 2)^1.5
대상: 공용 조건 ∧ first_public_at > now − 7일 ∧ (like_count > 0 ∨ commenters > 0)
작성자당 3개 → score DESC, first_public_at DESC, id DESC → 상위 100
commenters = count(DISTINCT 댓글 작성자) − 글 작성자, 삭제·숨김 댓글 제외
```
