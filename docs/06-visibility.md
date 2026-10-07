# 공개 범위 설계

> 작성일 2026-10-02 · 관련 요구사항: C-POST-4(공개 범위). 연관: C-READ-1·2, C-BLOG-1, C-OWN-1, [05 발행·수정](./05-publish.md)
> 핵심 목표: **친구나 나만 보려고 쓴 글이 밖으로 새지 않는다.**

---

## 1. 결정 사항

| # | 안건 | 결정 | 이유 |
|---|---|---|---|
| V-1 | 친구 공개를 공통에 넣을지 | **친구 관계(`friendship`, V1)와 친구 맺기(요청·수락)는 공통, 친구 공개(`FRIENDS`) 범위만 선택 구현** (2026-10-07 회의 M1). 켜는 사람은 §6-1 규격을 그대로 따른다 | 공통 범위는 작게 유지하면서, 구현하는 사람끼리는 ERD·규칙이 같아서 비교할 수 있다. 친구 맺기는 최근 활동 표시(§6-4)에도 쓰여 모두에게 필요하다 |
| V-2 | 친구 관계 모델 | **상호 친구** (요청 → 수락) | "친구"의 뜻 그대로 양방향. 팔로우(Tier C)를 먼저 만들 필요가 없다 |
| V-3 | 값의 이름 | **`PUBLIC` / `FRIENDS` / `PRIVATE`** (`PROTECTED`는 쓰지 않음) | `PROTECTED`는 "일부에게 공개"로 읽혀서 나만 보기와 헷갈린다 |
| V-4 | 기본 공개 범위 | **`member.default_visibility` 추가, 기본값 `PUBLIC`** | 새 글은 이 값으로 시작하고 글마다 바꿀 수 있다 (강 FRIEND-01) |
| V-5 | 권한이 없을 때 응답 | **404** | 403이면 "글이 있긴 하다"는 사실이 드러난다 |
| V-6 | 공개 범위 변경 시 "수정됨" 표시 | **남기지 않는다** (`edited_at` 그대로) | 내용이 바뀐 것이 아니다 |
| V-7 | 비공개로 바꿀 때 댓글·좋아요 | **지우지 않고 글과 함께 숨긴다.** 다시 공개하면 돌아온다 | 공개 범위는 언제든 다시 바꿀 수 있다 |
| V-8 | 내 블로그 페이지에 내 비공개 글 표시 | **표시하지 않는다.** 내 글 관리에서만 보인다 | 블로그 페이지는 "남에게 보이는 모습" 그대로 유지한다 |

공통 구현 범위는 `PUBLIC`과 `PRIVATE`이다. `FRIENDS`는 §6-1 규격을 적용한 사람(이하 "`FRIENDS` 적용자")만 쓴다. 친구 맺기(§6-2)와 친구에게 보이는 최근 활동(§6-4)은 모두가 구현한다.

---

## 2. 공개 범위 값

| 값 | 의미 | 누가 읽을 수 있나 | 공통 여부 |
|---|---|---|---|
| `PUBLIC` | 전체 공개 | 누구나 (비회원 포함) | 공통 |
| `FRIENDS` | 친구 공개 | 작성자 + 수락된 친구 | 규격 (선택 구현, §6-1) |
| `PRIVATE` | 나만 보기 | 작성자 | 공통 |

- 공개 범위는 **발행된 글(`PUBLISHED`)에만 의미가 있다.** 임시글은 공개 범위와 상관없이 작성자만 본다. 임시글에 저장된 값은 "발행할 때 쓸 공개 범위"다.
- 삭제된 글(`deleted_at IS NOT NULL`)은 공개 범위와 상관없이 아무에게도 보이지 않는다.
- 관리자가 숨긴 글(`hidden_at IS NOT NULL`)은 공개 범위와 상관없이 **어떤 목록에도 나오지 않는다**(작성자 본인이 보는 블로그 목록 포함). 상세는 작성자 본인에게만 안내와 함께 보이고, 작성자는 내 글 관리에서도 본다 ([43 §4-1](./43-report-hide.md), 2026-10-07 회의 H1).
- 운영자도 남의 `PRIVATE`·`FRIENDS` 글을 볼 수 없다 (김민서 §6 권한 매트릭스). 신고 처리는 신고 시점 스냅샷만 본다 ([43 §3](./43-report-hide.md)).

---

## 3. 노출 규칙

| 노출되는 곳 | `PUBLIC` | `FRIENDS` (규격) | `PRIVATE` |
|---|:-:|:-:|:-:|
| 글 상세 | 누구나 | 작성자·친구, 그 외 404 | 작성자만, 그 외 404 |
| 홈 최신 글 / 인기 글 | ✅ | ❌ | ❌ |
| 개인 블로그 글 목록 | ✅ | 친구가 볼 때만 | ❌ (작성자도 내 글 관리에서만, V-8) |
| 블로그 글 수 | ✅ | 친구가 볼 때만 셈 | ❌ |
| 태그별 목록, 태그 글 수, 인기 태그 | ✅ | ❌ | ❌ |
| 검색, AI 추천, 관련 글 | ✅ | ❌ | ❌ |
| sitemap, RSS | ✅ | ❌ | ❌ |
| 검색 엔진 | 수집 허용 | `noindex` + 404 | 404 |
| 링크 미리보기(OG) | 제목·요약·썸네일 | 공통 문구 (아래) | 공통 문구 (아래) |
| 댓글·좋아요 보기·쓰기 | 글을 읽을 수 있는 사람만 | 〃 | 〃 |
| 응답 캐시 | 허용 | `Cache-Control: private, no-store` | 〃 |
| 사진 | 주소를 알면 볼 수 있음 ([04 §6](./04-draft-and-image.md) 결정 2) | 〃 | 〃 |
| 내 글 관리 | 작성자에게 모두 보임, 공개 범위 배지(🌐 / 👥 / 🔒) 표시 | | |

### 3-1. 볼 수 없는 글의 링크 미리보기

권한이 없는 사람이 글 주소를 열면 **404 상태 코드와 함께 공통 안내 페이지**를 보여준다. 메신저·SNS가 가져가는 미리보기(OG)도 같은 페이지에서 나온다.

```html
<meta property="og:title" content="볼 수 없는 글이에요">
<meta property="og:description" content="친구 공개·비공개 글이거나 삭제된 글입니다.">
<meta name="robots" content="noindex">
```

**없는 글에도 똑같은 문구를 쓴다.** 그래서 미리보기만 보고는 "친구 공개 글이 있다"는 사실을 알 수 없다. 강성찬 BASE-13의 "링크 미리보기에는 제목 대신 친구 공개 글만 표시"를 V-5(존재를 드러내지 않음)와 충돌하지 않게 다듬은 것이다.

---

## 4. 공개 범위 바꾸기

```
PATCH /api/posts/{postId}/visibility
{ "visibility": "PRIVATE" }
```

| 응답 | 의미 |
|---|---|
| `200` | `{ visibility, firstPublicAt }` |
| `400` `INVALID_VISIBILITY` | 모르는 값, 또는 `FRIENDS` 규격을 적용하지 않았는데 `FRIENDS`를 보냄 |
| `404` | 없는 글, 남의 글, 삭제된 글 |

| 규칙 | 내용 |
|---|---|
| 다시 발행 | 필요 없다. 작업본(`post_draft`)과 `edit_version`에 영향이 없어서, 수정 중인 작업본이 있어도 바로 바꿀 수 있다 |
| `edited_at` | 바뀌지 않는다 (V-6) |
| `first_public_at` | 처음으로 `PUBLISHED` + `PUBLIC`이 되는 순간에만 기록하고 이후 유지 ([05 §3](./05-publish.md)). 공개 범위를 껐다 켜서 글을 목록 맨 위로 올릴 수 없다 |
| 댓글·좋아요 | 지우지 않는다. 글을 읽을 수 없게 된 사람에게는 함께 숨겨진다 (V-7) |
| 임시글 | 값만 저장한다 (발행할 때 쓸 공개 범위) |
| 처리 | 행 잠금(`SELECT … FOR UPDATE`) 후 변경. 같은 값으로 다시 보내면 아무것도 바뀌지 않고 200 |
| 이벤트 | 커밋 후 `PostVisibilityChanged(postId, from, to)` 발행 → 검색 색인·sitemap 갱신, 강성찬 "첫 공개 응원" 알림 등을 리스너로 붙인다 |
| 여러 글 한 번에 바꾸기 | 개인 확장 (강 OPEN-01) |

```sql
UPDATE post
SET visibility      = :to,
    first_public_at = CASE WHEN first_public_at IS NULL AND status = 'PUBLISHED' AND :to = 'PUBLIC'
                           THEN now() ELSE first_public_at END,
    updated_at      = now()
WHERE id = :postId AND author_id = :me AND deleted_at IS NULL;
```

---

## 5. 기본 공개 범위

| 항목 | 규칙 |
|---|---|
| 저장 | `member.default_visibility` (기본값 `PUBLIC`) |
| 적용 | [새 글]로 만든 임시글의 `visibility` 초기값. 발행 설정에서 글마다 바꿀 수 있다 |
| 변경 | 설정 화면에서 본인만 |
| 친구가 없는데 기본값이 `FRIENDS`일 때 (`FRIENDS` 적용자) | 발행 설정에 안내를 띄운다: "아직 친구가 없어서 지금은 나만 볼 수 있어요. [친구 초대] [전체 공개로 바꾸기]" — 아무도 못 보는 글을 모르고 발행하는 일을 막는다 |

---

## 6. 친구 관계(공통)와 친구 공개 규격(선택 구현)

친구 관계 테이블(`friendship`)과 `ix_friendship_b`는 **공통 V1에 들어 있다** (2026-10-07 회의 M1). 친구 맺기(요청·수락)는 공통 기능이다. **친구 공개(`FRIENDS`) 범위를 켜는 사람만** 아래 마이그레이션 하나(`V{n}__friends.sql`)를 더한다. 공통 테이블은 CHECK 교체와 인덱스 추가만 한다 ([03 E-10](./03-erd.md)).

### 6-1. 스키마

```sql
-- 공개 범위 값에 FRIENDS 추가 (CHECK 교체만)
ALTER TABLE post   DROP CONSTRAINT ck_post_visibility;
ALTER TABLE post   ADD  CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'));
ALTER TABLE member DROP CONSTRAINT ck_member_default_visibility;
ALTER TABLE member ADD  CONSTRAINT ck_member_default_visibility
    CHECK (default_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'));

-- 친구가 보는 블로그 목록 (PUBLIC + FRIENDS). 숨김 조건은 공개 목록 인덱스(ix_post_blog)와 같고,
-- 정렬은 published_at (FRIENDS 글은 first_public_at이 NULL, §6-3)
CREATE INDEX ix_post_blog_friends ON post (author_id, published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND visibility IN ('PUBLIC', 'FRIENDS') AND deleted_at IS NULL AND hidden_at IS NULL;
```

친구 관계는 두 회원 id를 항상 (작은 쪽, 큰 쪽) 순서로 저장해 한 쌍에 행 하나만 둔다(V1 `ck_friendship_order`). A가 B에게, B가 A에게 동시에 요청해도 행이 하나뿐이라 중복이 생기지 않는다 (두 번째 요청은 `ON CONFLICT`로 처리 → 상대가 이미 보낸 요청이면 바로 수락).

### 6-2. 친구 기능 (공통)

친구 맺기는 `FRIENDS` 적용 여부와 상관없이 **모두가 구현한다** (2026-10-07 회의). 친구 알림은 선택 기능이다(기본 비활성, M1).

| 기능 | 규칙 |
|---|---|
| 친구 요청 | 상대 블로그에서 [친구 요청]. 자기 자신에게는 불가. 이미 친구거나 요청 중이면 아무것도 바뀌지 않음 |
| 받은 요청 | 설정의 "받은 친구 요청" 목록에서 [수락] / [거절]. 친구 알림을 켠 사람은 알림에서도 처리 |
| 거절 | **행을 삭제한다. 요청한 사람에게 알리지 않는다** (강성찬 원칙: 거절은 상대에게 드러나지 않음) |
| 요청 취소 / 친구 끊기 | 행 삭제. 상대에게 알리지 않음 |
| 친구 목록 | 본인만 볼 수 있다 |
| 친구를 끊으면 | 그 순간부터 서로의 최근 활동이 보이지 않고(§6-4), `FRIENDS` 적용자의 경우 상대는 내 `FRIENDS` 글을 볼 수 없다 (상세 404, 목록에서 사라짐) |
| 탈퇴하면 | 친구 관계 행을 삭제한다 (모두에게 항상 실행, [13 §3-3](./13-delete-withdraw.md) 6번) |

### 6-3. 읽기 판정과 목록

```sql
-- 친구인지: 순서를 맞춰서 한 행만 확인
SELECT EXISTS (
  SELECT 1 FROM friendship
  WHERE member_a_id = LEAST(:author, :viewer) AND member_b_id = GREATEST(:author, :viewer)
    AND status = 'ACCEPTED');
```

| 목록 | 조건 | 정렬 |
|---|---|---|
| 친구가 보는 개인 블로그 (`FRIENDS` 적용자) | `visibility IN ('PUBLIC', 'FRIENDS')` + R-2a 공용 조건 | `published_at DESC, id DESC` (친구 공개 글에는 `first_public_at`이 없음), `ix_post_blog_friends` |
| 그 밖의 모든 공개 목록 | `visibility = 'PUBLIC'` + R-2a 공용 조건 (공통과 같음) | `first_public_at DESC, id DESC` |

`FRIENDS` → `PUBLIC`으로 바꾸면 그 순간이 `first_public_at`이 되어 홈 목록 맨 위에 나온다 (강성찬 "공개 전환"과 같은 효과).

### 6-4. 친구에게 보이는 최근 활동 (공통)

수락된 친구(`status = 'ACCEPTED'`)끼리는 서로의 최근 활동을 대략적으로 볼 수 있다 (2026-10-07 회의 "마지막 로그인·최근 활동 표시").

| 항목 | 규칙 |
|---|---|
| 데이터 | `member.last_active_at`. 로그인한 요청이 오면 갱신하되 한 시간에 한 번 정도로 줄인다(Redis로 간격 조절, [02 §2](./02-architecture.md)) |
| 표시 | 정확한 시각을 보이지 않고 **"오늘" / "어제" / "N일 전"(2~6일) / "1주 이상"** 네 가지로만 보인다 |
| 보는 사람 | **친구(ACCEPTED)만.** 비회원·친구가 아닌 회원·요청 중(PENDING)인 상대에게는 보이지 않고, API 응답에도 넣지 않는다 |
| 숨기기 | `member.last_active_visible = false`(설정 화면, 기본 공개)면 친구에게도 보이지 않는다. **숨긴 사람은 다른 친구의 최근 활동도 볼 수 없다** (서로 같은 조건) |
| 표시 판정 | 보는 사람과 대상이 친구 + 대상 `last_active_visible = true` + 보는 사람 `last_active_visible = true` + 대상 `last_active_at IS NOT NULL` |
| 탈퇴·익명 처리 | 익명 처리 때 `last_active_at`을 비운다 ([13 문서](./13-delete-withdraw.md)) |
| 안내 | 개인정보 처리방침에 "친구에게 최근 활동 시점 표시"를 적는다 (H9) |

---

## 7. 구현 원칙: 새는 곳을 없앤다

| # | 원칙 | 방법 |
|---|---|---|
| R-1 | 글 하나를 읽을 수 있는지는 **한곳에서만** 판단한다 | `PostAccessPolicy.canRead(post, viewer)`. 컨트롤러·화면(React)에서 직접 판단하지 않는다 |
| R-2 | 목록 조회는 **공개 범위 조건을 포함한 공용 메서드로만** 한다 | `PostQueryRepository`에 "공개 목록용 조건"(`VisibilityFilter.forViewer(viewer, author)`)을 두고 모든 목록 쿼리가 이것을 쓴다. 목록마다 `WHERE`를 직접 쓰지 않는다 |
| R-2a | 공용 조건에는 **삭제·숨김·탈퇴 조건도 들어 있다** (2026-10-07 회의 H1) | `deleted_at IS NULL`(휴지통 글 제외) + **`hidden_at IS NULL`(관리자 숨김 제외, [43 문서](./43-report-hide.md))** + 작성자의 `withdrawn_at IS NULL`(탈퇴 신청한 작성자의 글 제외). `canRead`도 같은 순서로 먼저 확인하되, **숨긴 글은 작성자 본인에게만 안내와 함께 보인다(43 §4-1) — 이 예외는 상세 `canRead`와 내 글 관리에만 있고 목록 조건에는 없다(V-8)** ([13 문서](./13-delete-withdraw.md)) |
| R-2b | 공용 조건은 **공개 목록 인덱스 조건과 같다** | `ix_post_feed`·`ix_post_blog`(V1)의 WHERE(`status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL`)를 모두 포함한다. 조건이 하나라도 빠지면 PostgreSQL이 인덱스를 쓰지 못한다. 통합 테스트(Testcontainers)에서 홈·블로그 목록 대표 쿼리의 EXPLAIN에 두 인덱스가 나오는지 확인한다 |
| R-3 | 공개 범위별 규칙은 **추가만으로 확장**한다 | 값마다 `VisibilityRule`(읽기 판정 + 목록 조건)을 Bean으로 등록. 공통은 `PUBLIC`·`PRIVATE` 규칙만 있고, `FRIENDS` 적용자는 `FriendsVisibilityRule`을 추가한다. 강성찬의 `GROUP`·`LINK`도 같은 방식 |
| R-4 | 권한 없음은 항상 404 | `PostNotFoundException` 하나로 통일. "없음"과 "권한 없음"을 구분하는 응답·로그 메시지를 사용자에게 보내지 않는다 |
| R-5 | `PUBLIC`이 아닌 글의 응답은 캐시하지 않는다 | `Cache-Control: private, no-store`, CDN 캐시 키에 포함하지 않음 |

```java
public interface VisibilityRule {
    Visibility visibility();
    boolean canRead(Post post, Viewer viewer);          // 작성자 여부·삭제·상태는 정책이 먼저 확인
    Predicate listCondition(Viewer viewer, Long authorId); // 목록 쿼리에 붙일 조건 (블로그 목록 등)
}
```

---

## 8. 공통 완료 기준 (C-POST-4)

**권한 매트릭스 자동 테스트:** 공개 범위(`PUBLIC` / `PRIVATE`, `FRIENDS` 적용자는 `FRIENDS` 추가) × 보는 사람(비회원 / 다른 회원 / 친구 / 작성자) × 노출되는 곳(상세 / 홈 / 블로그 목록 / 블로그 글 수 / 태그 / 검색 / sitemap / 댓글)의 모든 조합을 테스트한다.

| # | 기준 |
|---|---|
| 1 | `PRIVATE` 글은 작성자만 상세를 볼 수 있고, 그 외에는 404다 |
| 2 | `PUBLIC`이 아닌 글은 홈·블로그 목록(남이 볼 때)·태그·검색·sitemap 어디에도 나오지 않고, 글 수에도 세지 않는다 |
| 3 | 볼 수 없는 글과 없는 글의 응답(상태 코드·본문·미리보기)이 같다 |
| 4 | 공개 범위를 바꿔도 `edited_at`·작업본·댓글·좋아요는 그대로다 |
| 5 | 공개 → 비공개 → 공개로 바꿔도 `first_public_at`은 처음 값이다 |
| 6 | 비공개로 바꾸면 그 글의 댓글·좋아요도 다른 사람에게 보이지 않는다 |
| 7 | 새 글의 공개 범위는 `member.default_visibility`로 시작한다 |
| 8 | 관리자가 숨긴 글은 작성자 본인이 볼 때도 어떤 목록에도 나오지 않고, 상세는 작성자 본인에게만 안내와 함께 보인다 (R-2a) |
| 9 | 최근 활동은 친구(ACCEPTED)에게만 "오늘·어제·N일 전·1주 이상"으로 보이고, 숨긴 사람은 남의 것도 볼 수 없다 (§6-4) |
