# 팔로우·팔로잉 피드 설계

> 작성일 2026-10-04 · 수정 2026-10-06 (검증 L34·05 확인 2 반영) · 작성 강성찬 · 관련: 팔로우·구독 + 피드(Tier C). 연관: [03 ERD](./03-erd.md) §5(`follow` 자리), [06 공개 범위](./06-visibility.md) §3·§7, [10 목록](./10-post-list.md), [13 탈퇴](./13-delete-withdraw.md) §3, [20 이벤트](./20-domain-events.md) §3-4
> 이미 정해진 것: 카드·9개·[더 보기] 커서 방식(10), 공개 범위 공용 조건(06 R-2, `VisibilityFilter`), 팔로우 대상은 **회원**(20 결정 기록).

---

## 1. 결정 사항

| # | 안건 | 결정 | 이유 |
|---|---|---|---|
| F-1 | 대상 | **회원** (`follow.follower_id → followee_id`) | 공통은 회원 = 블로그 (01 Q5). 나민서님 `blog`는 회원과 1:1이라 `blog.member_id`로 바꿔 쓴다 |
| F-2 | 방식 | **한쪽 방향, 바로 팔로우** (상대의 승인 없음) | 친구(06 §6, 상호·요청 → 수락)와 다른 개념으로 둔다. 둘은 함께 있을 수 있다 |
| F-3 | 공개 | **팔로워·팔로잉 수와 목록 모두 공개** (비회원 포함) | 다른 사람의 팔로우 목록을 보고 새 블로그를 발견할 수 있다 |
| F-4 | 피드 위치 | **별도 페이지 `/feed`** (로그인 필요). 홈(10)은 그대로 | 홈의 구성을 바꾸지 않는다 |
| F-5 | 제한 | **로그인만 하면 팔로우할 수 있다.** 이메일 인증·인원·횟수 제한 없음. 자기 자신은 불가 | 팔로우는 남에게 글을 보여주는 행동이 아니다 (07 L-2의 인증 대상은 글쓰기·댓글·사진). 알림 도배는 알림 쪽에서 막는다 (§8) |
| F-6 | 수 세기 | 그때그때 `COUNT`, 탈퇴 신청한 회원은 빼고 센다 | 회원 행에 숫자 컬럼을 두면 탈퇴 유예·복구 때 어긋나기 쉽다 |
| F-7 | 피드 내용 | 팔로우한 사람의 **공개·발행 글만**, `first_public_at` 최신순 | 06 §3: 공개 목록은 `PUBLIC`만. 친구 공개 규격을 적용해도 피드에는 넣지 않는다 |

---

## 2. 화면

### 2-1. 팔로우 버튼

```
블로그 상단 (10 §5)
(프로필) 김민서 @kim755030                     [팔로우]
백엔드 개발을 공부하고 있어요.
공개 글 24 · 팔로워 12 · 팔로잉 30             ← 숫자를 누르면 목록

글 상세 작성자 영역 (나민서 글 상세 문서)
(프로필) 김민서 @kim755030 · 2026.10.02          [팔로잉 ✓]
```

| 상태 | 표시 | 누르면 |
|---|---|---|
| 비회원 | [팔로우] | 로그인 화면, 로그인 후 원래 페이지로 (07 §6) |
| 팔로우 안 함 | [팔로우] | 팔로우 |
| 팔로우 중 | [팔로잉 ✓] (마우스를 올리거나 초점을 받으면 [언팔로우]) | 언팔로우 (확인 창 없음, 다시 누르면 되돌릴 수 있음) |
| 내 블로그 | 버튼 없음 | |

- 버튼은 누르는 즉시 바뀌고(낙관적 갱신), 실패하면 원래대로 돌리고 "잠시 후 다시 시도해 주세요".
- 상태를 색만으로 구분하지 않는다 (글자와 ✓).

### 2-2. 팔로워·팔로잉 목록 (`/@주소/followers`, `/@주소/following`)

```
김민서님의 팔로워 12
(프로필) 나민서 @na_ms                         [팔로우]
         티스토리형 블로그를 만들고 있어요
(프로필) 강성찬 @eueu2                          [팔로잉 ✓]
         …
[더 보기]
```

| 항목 | 규칙 |
|---|---|
| 누가 보나 | 누구나 (F-3) |
| 항목 | 프로필 이미지, `닉네임 @블로그주소`, 소개 첫 줄, 보는 사람 기준 팔로우 버튼 |
| 순서 | 최근에 팔로우한 순 (`follow.created_at DESC`, 같으면 회원 ID 큰 순) |
| 개수 | 20개씩 [더 보기], 커서 방식 (10 §4와 같은 원리) |
| 제외 | 탈퇴 신청한 회원 (F-6, §6) |
| 없는 블로그·탈퇴 신청한 블로그 | 404 (블로그 페이지와 같다, 13 §3-1) |
| 빈 상태 | "아직 팔로워가 없어요" / "아직 팔로우한 사람이 없어요" |

### 2-3. 팔로잉 피드 (`/feed`)

```
팔로잉 피드
[카드] [카드] [카드]      ← 10 §2와 같은 카드 (작성자 영역 포함)
[카드] [카드] [카드]
[카드] [카드] [카드]
           [더 보기]
```

| 항목 | 규칙 |
|---|---|
| 접근 | 로그인 필요. 비회원은 로그인 화면으로 |
| 메뉴 | 상단 메뉴에 [피드] (로그인한 경우만) |
| 대상 | 내가 팔로우한 사람의 글 중 **공개·발행·휴지통 아님·작성자 탈퇴 신청 아님** (06 R-2a의 공용 조건) |
| 정렬·개수 | 10 문서와 같다: `first_public_at DESC, id DESC`, 9개, 10개를 조회해 마지막 판단, [더 보기] 커서 |
| 언팔로우 | 그 사람의 글은 다음 요청부터 빠진다. 이미 화면에 있는 카드는 그대로 |
| 빈 상태 | 팔로우한 사람이 없으면 "팔로우한 사람이 없어요. 홈에서 읽고 싶은 블로그를 찾아보세요 [홈]" / 있지만 글이 없으면 "팔로우한 사람의 공개 글이 아직 없어요" |
| 뒤로 가기 | 10 L-6과 같이 카드·스크롤 복원 |
| JS 없음 | [더 보기]가 `/feed?cursor=…` 링크로 동작 (SSR 팀원용) |

---

## 3. API

| 요청 | 동작 | 응답 |
|---|---|---|
| `PUT /api/members/{handle}/follow` | 팔로우. 이미 팔로우 중이면 아무것도 바뀌지 않음 | `200 { "following": true, "followerCount": 13 }` |
| `DELETE /api/members/{handle}/follow` | 언팔로우. 팔로우하지 않았으면 아무것도 바뀌지 않음 | `200 { "following": false, "followerCount": 12 }` |
| `GET /api/members/{handle}/followers?cursor=…` | 팔로워 목록 | `{ items: [{ handle, nickname, profileImageUrl, bio, followedByMe }], nextCursor }` |
| `GET /api/members/{handle}/following?cursor=…` | 팔로잉 목록 | 같은 형식 |
| `GET /api/feed?cursor=…` | 팔로잉 피드 | 10 §4-2와 같은 형식 |

| 오류 | 경우 |
|---|---|
| `401` | 비회원의 팔로우·피드 요청 |
| `404` | 없는 블로그, 탈퇴 신청한 블로그 |
| `400 CANNOT_FOLLOW_SELF` | 자기 자신 |

`PUT`/`DELETE`를 쓰는 이유: 같은 요청을 여러 번 보내도 결과가 같다 (버튼 연타·재전송에 안전).

SSR을 쓰는 팀원은 같은 Service를 폼 POST(`_method=PUT`/`DELETE`, Spring `HiddenHttpMethodFilter`)로 호출하고 원래 페이지로 돌아간다 (30 §3 좋아요와 같은 방식).

---

## 4. 저장과 이벤트

```sql
-- 팔로우: 행이 실제로 생겼을 때만 이벤트 (20 EV-4)
INSERT INTO follow (follower_id, followee_id) VALUES (:me, :target)
ON CONFLICT DO NOTHING
RETURNING follower_id;            -- 행이 돌아오면 MemberFollowed 발행

-- 언팔로우
DELETE FROM follow WHERE follower_id = :me AND followee_id = :target
RETURNING follower_id;            -- 행이 돌아오면 MemberUnfollowed 발행
```

| 항목 | 규칙 |
|---|---|
| 동시 요청 | 복합 PK + `ON CONFLICT DO NOTHING` → 행은 하나, 이벤트도 한 번 |
| 대상 확인 | `member`에서 `handle`로 찾고, 탈퇴 신청(`withdrawn_at`)이면 404 |
| 이벤트 | `MemberFollowed(followerId, followeeId, followedAt)`, `MemberUnfollowed(…)` (20 §3-4) |

---

## 5. 쿼리

```sql
-- 팔로잉 피드 (첫 페이지는 커서 조건 없이)
SELECT p.id, p.title, p.excerpt, p.thumbnail_url, p.first_public_at, p.comment_count, p.like_count,
       m.handle, m.nickname, m.profile_image_url
FROM follow f
JOIN post p   ON p.author_id = f.followee_id
JOIN member m ON m.id = p.author_id
WHERE f.follower_id = :me
  AND p.status = 'PUBLISHED' AND p.visibility = 'PUBLIC' AND p.deleted_at IS NULL   -- VisibilityFilter (06 R-2)
  AND m.withdrawn_at IS NULL
  AND (p.first_public_at, p.id) < (:t, :id)
ORDER BY p.first_public_at DESC, p.id DESC
LIMIT 10;

-- 팔로워 수 (탈퇴 신청한 회원 제외)
-- 탈퇴 유예 회원을 "빼는" 모양으로 쓴다. 유예 회원은 적고 ix_member_withdraw_purge(부분 인덱스)로 바로 찾는다
-- (익명 처리된 회원은 order 65에서 팔로우 행이 지워져 여기 나오지 않는다)
SELECT count(*) FROM follow f
WHERE f.followee_id = :target
  AND NOT EXISTS (SELECT 1 FROM member m
                  WHERE m.id = f.follower_id AND m.status = 'WITHDRAWN' AND m.deleted_at IS NULL);
```

| 항목 | 내용 |
|---|---|
| 피드 인덱스 | 팔로우한 사람마다 `ix_post_blog (author_id, first_public_at DESC, id DESC)`를 읽어 합친다. 공통 비기능 기준(글 1만 건)에서는 충분하다 |
| 나중에 | 팔로우가 수천 명인 사용자가 생기면 "피드 행을 미리 만들어 두는 방식"(쓸 때 펼치기)을 검토 |
| 팔로워 수가 버티는 규모 (검증 L34) | 위 쿼리로 팔로워 **1천 명 약 1ms, 1만 명 약 5ms, 10만 명 약 30ms** (회원 10만·탈퇴 유예 2천 합성 데이터, PostgreSQL 16, 2026-10-06 측정 — 다른 문서의 측정은 18 기준이라 절대값은 참고만). 처음 쓴 `JOIN member … withdrawn_at IS NULL` 모양은 회원 표 전체를 읽어 팔로워 1천 명에도 약 22ms였다. 블로그를 열 때마다 실행되므로 **팔로워 1만 명이 넘는 회원이 생기면** `member.follower_count` 카운터 + 매일 보정 배치를 **검토**한다. 도입하면 F-6(숫자 컬럼을 두지 않음) 변경이라 팀에 알린다 |
| 목록 쿼리 수 | 피드 1번, 팔로워·팔로잉 목록 1번(+ 보는 사람의 팔로우 여부를 `ANY(:ids)`로 1번) |

---

## 6. 회원 탈퇴

| 시점 | 처리 |
|---|---|
| 탈퇴 신청 (유예 시작) | 행은 그대로. 그 사람은 다른 사람의 팔로워·팔로잉 목록과 수에서 빠지고(F-6), 그 사람의 글은 피드에서 빠진다(06 R-2a) |
| 복구 | 그대로 돌아온다 (행을 지우지 않았으므로) |
| 30일 뒤 (13 §3-3에 단계 추가) | `DELETE FROM follow WHERE follower_id = :me OR followee_id = :me` |

13 §3-3 표에 **"팔로우 관계 삭제"** 단계를 추가한다 (6번 친구 관계 삭제 다음). 강성찬이 `FollowWithdrawalPurgeStep`으로 구현한다.

---

## 7. 개인 확장 연결

| 확장 | 연결 |
|---|---|
| 나민서 블로그·구독 | 화면 이름은 "구독"이어도 된다. 저장은 `follow`(회원 기준), 블로그 페이지에서는 `blog.member_id`로 대상 회원을 찾는다 |
| 친구 공개 규격 (06 §6) | 친구와 팔로우는 별개. 친구는 `FRIENDS` 글을 볼 수 있고, 팔로우는 공개 글을 피드로 받는다 |
| 강성찬 그룹 | 그룹과 팔로우는 별개 (개인 확장 문서에서) |

---

## 8. 알림과의 관계

| 알림 | 규칙 (상세는 [25 알림](./25-notification.md)) |
|---|---|
| `FOLLOW` 새 팔로워 | 안 읽은 동안 하나로 묶는다 (20 §4). **같은 사람이 팔로우 → 언팔로우 → 팔로우를 반복해도 같은 사람의 `FOLLOW` 알림은 7일에 한 번만** — 팔로우 자체에 제한이 없으므로(F-5) 알림 쪽에서 도배를 막는다 |
| `NEW_POST` 팔로우한 사람의 새 글 | 처음 전체 공개될 때 한 번 (`PostWentPublic`, 20 EV-5) |
| 언팔로우 | 알리지 않는다 |

---

## 9. 공통 완료 기준

| # | 기준 | 확인 방법 |
|---|---|---|
| 1 | 같은 사람을 여러 번(동시에) 팔로우해도 행은 하나이고 `MemberFollowed`는 한 번 발행된다 | 동시 요청 20건 |
| 2 | 자기 자신은 팔로우할 수 없다 | 400 + DB CHECK |
| 3 | 피드에는 팔로우한 사람의 공개·발행 글만 나오고, 비공개·임시·휴지통·탈퇴 신청 작성자의 글은 나오지 않는다 | 06 §8 권한 매트릭스에 "피드" 열 |
| 4 | 피드의 [더 보기]는 10 문서 기준대로 중복·누락이 없다 | |
| 5 | 언팔로우하면 다음 피드 요청부터 그 사람의 글이 빠진다 | |
| 6 | 팔로워·팔로잉 수와 목록은 비회원도 볼 수 있고, 탈퇴 신청한 회원은 빠졌다가 복구하면 돌아온다 | |
| 7 | 탈퇴 30일 뒤 그 회원의 팔로우 관계가 양방향 모두 지워진다 | |

---

## 10. ERD 변경 제안

03 §5의 `follow` 자리를 그대로 확정한다 (Tier C → V2 마이그레이션).

```sql
-- V1 반영됨 (2026-10-07): 이 SQL은 erd/V1__common_schema.sql에 들어 있다. 기록용으로 남긴다.
-- V2__follow.sql
CREATE TABLE follow (
    follower_id bigint      NOT NULL REFERENCES member (id),
    followee_id bigint      NOT NULL REFERENCES member (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT ck_follow_self CHECK (follower_id <> followee_id)
);
-- 팔로워 목록·수 (최근 순)
CREATE INDEX ix_follow_followee ON follow (followee_id, created_at DESC);
-- 팔로잉 목록 (최근 순). 피드·팔로우 여부 확인은 PK(follower_id, followee_id)
CREATE INDEX ix_follow_follower ON follow (follower_id, created_at DESC);
```

---

## 11. 결정 기록 추가분

| 날짜 | 안건 | 결정 |
|---|---|---|
| 2026-10-04 | 팔로우 방식 | 회원 대상, 한쪽 방향, 승인 없이 바로. 친구(06 §6)와 별개 |
| 2026-10-04 | 팔로우 공개 | 팔로워·팔로잉 수와 목록 모두 공개(비회원 포함), 탈퇴 신청한 회원은 제외 |
| 2026-10-04 | 팔로잉 피드 | 별도 페이지 `/feed`(로그인 필요), 팔로우한 사람의 공개 글, 10 문서 카드·정렬·[더 보기] |
| 2026-10-04 | 팔로우 제한 | 로그인만(이메일 인증·인원·횟수 제한 없음), 자기 자신 불가. 알림 도배는 알림 쪽에서(같은 사람 7일 1번) |
| 2026-10-04 | 팔로우 탈퇴 처리 | 유예 중 목록·수·피드에서 제외, 30일 뒤 양방향 삭제 |
| 2026-10-06 | 팔로워 수 쿼리 (검증 L34) | 유예 회원을 `NOT EXISTS`로 빼는 모양. 팔로워 1만 명까지 그때그때 `COUNT`, 넘으면 카운터 컬럼 검토 |
| 2026-10-06 | SSR 팔로우 (검증 05 확인 2) | 폼 POST `_method=PUT`/`DELETE` (30과 같음) |

---

## 12. 다른 담당자와 맞출 것

| 상대 | 맞출 것 | 이 문서의 제안 |
|---|---|---|
| 나민서 (블로그) | "블로그 구독"과 회원 팔로우 | 저장은 `follow`(회원), 블로그 페이지에서 `blog.member_id`로 연결. 화면 문구는 자유 |
| 나민서 (글 상세) | 작성자 영역 팔로우 버튼 | §2-1 상태표. 내 글이면 버튼 없음 |
| 나민서 (권한 매트릭스) | 팔로우 행 | 비회원: 수·목록 보기 / 회원: 팔로우·언팔로우·피드 / 관리자: 일반 회원과 같음 |
| 나민서 (탈퇴) | 13 §3-3에 단계 추가 | "팔로우 관계 양방향 삭제" (§6) |
| 공통 (10 목록) | `/feed` | 10 §2 카드·§4 커서 규칙을 그대로 사용, 상단 메뉴에 [피드] 추가 |
| 화요일 안건 2 | 요청 횟수 제한 | 팔로우는 기능별 제한이 없으므로 공통 IP 제한 기준에 포함되는지 확인 |
