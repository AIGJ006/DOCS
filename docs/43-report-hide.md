# 신고·관리자 숨김 설계

> 작성일 2026-10-03 · 담당: 나민서 · 상태: **초안 (항목 확인 중)**
> 관련 요구사항: 신고·관리자 숨김 (Tier C). 연관: [03 ERD](./03-erd.md) §5 `report` 자리, [05 발행](./05-publish.md) §8, [06 공개 범위](./06-visibility.md), [40 글 상세](./40-post-detail.md), [41 내 글 관리](./41-manage-posts.md), [42 권한](./42-permission-matrix.md)
> 2026-10-06 수정: 강성찬 20(이벤트)·21(댓글)·25(알림), 김민서 30(좋아요)·32(트렌딩) 문서와 맞춤. 이벤트 이름·필드는 20을 따르고, 숨긴 댓글 문구는 21을 따른다.
> 이미 결정된 것: 관리자는 **숨김만** 할 수 있고 내용 수정·삭제는 못 한다 (05 §8). 관리자도 남의 비공개 글은 못 본다 (06 §2). 42 문서의 신고·숨김·정지 권한(인증 전 신고 불가, 자기 것 신고 400, 같은 대상 재신고 200, 관리자 전용 주소는 404, 정지 회원 로그인 차단).

---

## 1. 결정 사항

| # | 안건 | 결정 | 이유 |
|---|---|---|---|
| H-1 | 신고 대상 | **글, 댓글** | 공통 범위는 콘텐츠만. 회원(닉네임·소개) 신고는 개인 확장 |
| H-2 | 신고 사유 | **스팸·광고 / 욕설·혐오 / 음란·선정 / 개인정보 노출 / 저작권 침해 / 기타(직접 입력)** | 흔한 플랫폼 기준. 기타만 200자 설명 필수 |
| H-3 | 처리 단위 | **대상 하나에 쌓인 신고를 한 번에 처리** (같은 글에 신고 10건 → 관리자 판단 1번) | 같은 대상을 여러 번 판단하지 않는다 |
| H-4 | 자동 숨김 | **없음. 관리자만 숨긴다** | 여러 계정으로 몰려 신고해 멀쩡한 글을 내리는 악용을 막는다 |
| H-5 | 관리자가 내용을 보는 방법 | **신고할 때의 내용을 복사(스냅샷)해 둔다** | 06 §2 "관리자도 남의 비공개 글을 볼 수 없다"를 지키면서, 신고 뒤 글이 비공개로 바뀌어도 판단할 수 있다 |
| H-6 | 숨김의 효과 | 작성자 외에는 **비공개 글과 똑같이 취급** (상세 404, 목록·검색·태그·글 수에서 빠짐). 작성자에게는 안내와 함께 보임 (42 결정) | 06의 "새지 않게" 규칙을 그대로 쓴다 |
| H-7 | 숨긴 댓글 표시 | 다른 사람에게는 **"운영 정책에 따라 숨겨진 댓글이에요"**, 댓글 작성자에게는 원문 + **"숨겨졌어요 (나만 보여요)"** ([21 댓글](./21-comment.md) §3-1과 같은 문구). 답글은 그대로 보임. 숨긴 댓글은 수정·답글 불가 (21 §9) | 삭제(“삭제된 댓글이에요”)와 문구를 다르게 해서 구분된다 |
| H-8 | 회원 정지 | **로그인 차단** (42 P-7). 기간: **1일 / 7일 / 30일 / 영구**, 사유 필수 | 단계적으로 대응할 수 있다 |
| H-9 | 신고자 익명 | 작성자는 **누가 신고했는지 알 수 없다** | 보복 방지 |
| H-10 | 처리 결과 알림 | **신고자에게** 처리 결과(`ACTION_TAKEN` 조치함 / `NO_VIOLATION` 문제없음, 대상 내용·작성자는 넣지 않음), **작성자에게** "운영 정책에 따라 숨겨졌어요(사유)". 숨김 해제는 알림 없음 | 신고한 사람은 처리됐는지, 숨겨진 사람은 이유를 알아야 한다. 알림 종류·문구는 [25 알림](./25-notification.md) (`REPORT_RESOLVED`, `CONTENT_HIDDEN`) |
| H-11 | 이의 제기 | 공통에서 제외 (개인 확장) | 1차 범위를 작게 유지 |
| H-12 | 관리자끼리 | 관리자는 다른 관리자를 정지할 수 없고, 자기 글·댓글을 숨기거나 자기 신고를 처리할 수 없다 | 권한 남용·이해 충돌 방지 |
| H-13 | 신고 남용 방지 | 사용자당 **1분 5건, 하루 50건** (Redis 카운터), 넘으면 429 | 한 계정의 대량 신고를 막는다 |

---

## 2. 신고하기

```
글 상세 [신고] 또는 댓글 [신고]       ← 42 §11: 작성자가 아닌 회원·관리자에게만 보임
 → 신고 창
     사유  (●) 스팸·광고  ( ) 욕설·혐오  ( ) 음란·선정
           ( ) 개인정보 노출  ( ) 저작권 침해  ( ) 기타
     설명  [                         ] (기타만 필수, 200자)
     [신고하기] [취소]
 → "신고가 접수됐어요. 검토 후 처리할게요"
```

```
POST /api/reports
{ "targetType": "POST", "targetId": 42, "reason": "SPAM", "detail": null }
```

| 응답 | 의미 |
|---|---|
| `200` | 접수됨. **이미 같은 대상을 신고했으면 새로 만들지 않고 200** (42 §8) |
| `400` `CANNOT_REPORT_OWN` / `REPORT_DETAIL_REQUIRED` | 자기 것 / 기타인데 설명 없음 |
| `401` / `403 EMAIL_NOT_VERIFIED` | 42 §3 |
| `404` | 대상이 없거나 내가 볼 수 없음 (비공개·숨김·휴지통 포함) |
| `429` | H-13 |

| 처리 | 내용 |
|---|---|
| 스냅샷 | 글: 제목 + 본문 Markdown 앞 2,000자 / 댓글: 내용 전체. 작성자 ID·블로그 주소·공개 범위도 함께 (H-5) |
| 중복 | `UNIQUE(reporter_id, target_type, target_id)` + `ON CONFLICT DO NOTHING` |
| 이벤트 | 없음 (관리자는 신고 관리 화면에서 확인. 관리자 알림은 개인 확장) |

---

## 3. 관리자 화면

**주소:** `/admin/reports` (비회원 401, 로그인한 일반 회원 404, 42 P-10)

```
신고 관리   [대기 12]  [처리됨]
──────────────────────────────────────────────────────────────
글 · "무료 코인 받는 법"  @spam_bot            신고 7건 · 최근 10분 전
   스팸·광고 6, 기타 1                                    [보기]
댓글 · "XX 같은 글 쓰지 마"  @kim755030          신고 2건 · 최근 1시간 전
   욕설·혐오 2                                            [보기]
```

**[보기] — 대상 하나의 처리 화면**

```
대상   글 #42  /@spam_bot/posts/42   (현재: 공개)
스냅샷  제목 "무료 코인 받는 법" / 본문 앞 2,000자 …     ← 신고 시점 내용
신고   7건 (스팸·광고 6, 기타 1) — 기타: "피싱 링크가 있어요"
작성자  @spam_bot · 가입 10월 1일 · 이전에 숨겨진 콘텐츠 0건 · 정지 이력 없음

처리   (●) 숨기기  사유 [스팸·광고 ▾]
       ( ) 문제없음 (신고 반려)
       ☐ 작성자 정지  기간 [7일 ▾]  사유 [            ]
       [처리하기]
```

| 규칙 | 내용 |
|---|---|
| 목록 | 대상별로 묶어서, **대기 중인 신고가 있는 대상**을 신고 수 많은 순 → 최근 순 |
| 보이는 내용 | **스냅샷만.** 현재 글이 비공개·휴지통이어도 원문을 열어 볼 수 없다 (06 §2) |
| 현재 상태 표시 | 공개 / 비공개 / 휴지통 / 이미 숨김 / 작성자 탈퇴 — 대상이 이미 사라졌으면 "대상 없음"으로 자동 종료 (§5) |
| 처리 | 숨기기 또는 반려. **그 대상의 대기 중 신고가 모두 같은 결과로 닫힌다** (H-3) |
| 정지 | 숨기기와 함께 또는 따로. 기간·사유 필수 (H-8) |
| 기록 | 누가·언제·무엇을 했는지 남는다 (`handled_by`, `handled_at`, 숨김 대상의 `hidden_by`) |

---

## 4. 숨김과 해제

### 4-1. 숨긴 글

| 보는 사람 | 결과 |
|---|---|
| 작성자 외 모두 | 비공개 글과 같음: 상세 404, 홈·블로그·태그·검색·sitemap·글 수에서 빠짐 (06 §3과 같은 규칙) |
| 작성자 | 글 상세에 "운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요" (40 §2-1), 내 글 관리에 [숨김] 배지 (41 §3-2) |
| 관리자 | 신고 처리 화면의 스냅샷 |

| 항목 | 규칙 |
|---|---|
| 작성자가 할 수 있는 것 | 수정·다시 발행·공개 범위 변경·삭제는 그대로 가능. **숨김은 유지**된다 (다시 발행해도 풀리지 않음) |
| 댓글·좋아요 | **지우지 않고 글과 함께 숨긴다** (06 V-7, 휴지통과 같음 — [30 좋아요](./30-like.md) 요청). `like_count`도 그대로, 해제하면 원래대로 |
| 트렌딩·검색 | 공용 조건에서 빠지므로 자동 제외 (32·33) |
| 목록 정렬 위치 | 해제하면 원래 위치 (`first_public_at` 그대로) |
| 구현 | **06 §7 R-2a 공용 조건과 `canRead`에 `hidden_at IS NULL`(작성자 제외)을 추가**한다 → 기존 결정 변경 (아래) |

### 4-2. 숨긴 댓글 (H-7)

| 보는 사람 | 표시 |
|---|---|
| 다른 사람 | 작성자·내용 대신 "운영 정책에 따라 숨겨진 댓글이에요". 답글은 그대로 |
| 댓글 작성자 | 원문 + "숨겨졌어요 (나만 보여요)" (21 §3-1). [수정]·[답글] 버튼 없음, [삭제]는 가능 |
| 글 주인 | 다른 사람과 같음 (원문을 볼 수 없다) |

| 비교 | 삭제된 댓글 | 숨긴 댓글 | 탈퇴한 사용자의 댓글 |
|---|---|---|---|
| 문구 | "삭제된 댓글이에요" | "운영 정책에 따라 숨겨진 댓글이에요" | "탈퇴한 사용자의 댓글이에요" (13) |
| 남는 조건 | 답글이 있을 때만 | 항상 (해제할 수 있어야 함) | 유예 중에는 행 그대로, 30일 뒤 답글이 있을 때만 (21 §11) |
| `comment_count` | 빠짐 | 빠짐 | 유예 중에는 **포함**, 30일 뒤 정리할 때 빠짐 (21 CM-11) |

### 4-3. 해제

| 항목 | 규칙 |
|---|---|
| 누가 | 관리자 (처리됨 탭에서 [숨김 해제]) |
| 결과 | `hidden_at` 등을 비우고 원래대로. 댓글 수 다시 더함 |
| 알림 | 없음 (`ContentUnhidden`은 발행하지만 공통 알림 종류에는 없음, 25) |

---

## 5. 대상이 바뀌거나 사라질 때

| 상황 | 처리 |
|---|---|
| 신고 대기 중에 작성자가 글을 비공개로 바꿈 | 신고는 그대로 대기, 관리자는 스냅샷으로 판단 |
| 신고 대기 중에 글이 휴지통 → 완전 삭제 | 완전 삭제 시 `report`는 `status = CLOSED_NO_TARGET`로 바뀌고 스냅샷은 30일 뒤 지운다 |
| 작성자 탈퇴 유예 | 대기 유지. 30일 뒤 익명 처리 시 그 회원 콘텐츠 신고는 `CLOSED_NO_TARGET` |
| 신고자 탈퇴 | 신고는 남기되 `reporter_id`는 익명 껍데기 회원을 그대로 가리킨다 (13 §3-3) |

---

## 6. 회원 정지 (H-8)

| 항목 | 규칙 |
|---|---|
| 하는 곳 | 신고 처리 화면 또는 회원 화면(`/admin/members/{handle}`) |
| 기간 | 1일 / 7일 / 30일 / 영구, 사유 필수(200자) |
| 즉시 효과 | `status = SUSPENDED`, 모든 세션 삭제 (42 P-7). 콘텐츠는 그대로 보임 (숨김은 따로) |
| 로그인 시 | "정지된 계정이에요 (11월 1일 14:00까지). 사유: 스팸·광고" (42 §4) |
| 자동 해제 | 로그인을 시도할 때 `suspended_until`이 지났으면 `ACTIVE`로 바꾸고 로그인시킨다. 배치 없이 처리 |
| 수동 해제 | 관리자 [정지 해제] |
| 제한 | 관리자는 관리자를 정지할 수 없다 (H-12) |

---

## 7. 이벤트 ([20 도메인 이벤트](./20-domain-events.md) §3-5를 따른다)

| 이벤트 | 담는 값 (20) | 받는 사람 (25) |
|---|---|---|
| `ReportResolved` | `reportId, reporterId, targetType, targetId, result, resolvedAt` — **신고 한 건(신고자 한 명)마다 하나**. `result`: `ACTION_TAKEN`(숨김) / `NO_VIOLATION`(반려) | 그 신고자: `REPORT_RESOLVED` |
| `ContentHidden` | `targetType, targetId, ownerId, postId, hiddenAt` — 댓글이면 `postId`는 그 댓글의 글. **신고자 ID는 넣지 않는다** (H-9) | 작성자: `CONTENT_HIDDEN` (사유는 알림을 그릴 때 `hidden_reason`에서 읽는다) |
| `ContentUnhidden` | `targetType, targetId, ownerId, postId, unhiddenAt` | 없음 (검색 색인 등만 구독) |
| `MemberSuspended` | `memberId, until, reason` | 알림 없음 (로그인할 수 없으므로 로그인 화면에서 안내). **20 목록에 추가 요청** |

- 한 대상의 대기 신고 N건을 숨김으로 처리하면 `ReportResolved` N개 + `ContentHidden` 1개가 발행된다 (20 §3-5).
- `report.status` ↔ `result` 대응: `HIDDEN` → `ACTION_TAKEN`, `REJECTED` → `NO_VIOLATION`. `CLOSED_NO_TARGET`은 이벤트를 만들지 않는다.
- 모두 **커밋 후** 발행한다 (20 EV 규칙).

---

## 8. 공통 완료 기준

| # | 기준 |
|---|---|
| 1 | 글·댓글을 신고하면 접수되고, 같은 대상을 다시 신고해도 1건이다 |
| 2 | 자기 글·댓글, 볼 수 없는 대상, 인증 전 회원의 신고는 거부된다 (42) |
| 3 | 관리자 화면은 관리자만 열 수 있고, 일반 회원은 404다 |
| 4 | 관리자는 신고 시점 스냅샷만 볼 수 있고, 현재 비공개인 원문은 볼 수 없다 |
| 5 | 숨긴 글은 작성자 외에는 비공개 글과 똑같이 어디에도 나오지 않고, 작성자에게는 안내와 함께 보인다 |
| 6 | 숨긴 댓글은 "운영 정책에 따라 숨겨진 댓글이에요"로, 삭제된 댓글과 다른 문구로 보이고, 작성자에게만 원문 + "숨겨졌어요 (나만 보여요)"가 보인다 |
| 11 | 숨김 처리하면 신고자마다 `ReportResolved` 1개, 작성자에게 `ContentHidden` 1개가 발행되고, `ContentHidden`에 신고자 ID가 없다 |
| 12 | 숨긴 글의 좋아요·댓글 행은 지워지지 않고, 해제하면 수가 원래대로다 |
| 7 | 한 대상의 대기 중 신고는 한 번의 처리로 모두 닫힌다 |
| 8 | 정지하면 모든 세션이 끊기고, 기간이 지나면 로그인할 때 자동으로 풀린다 |
| 9 | 관리자도 글·댓글 내용을 바꾸거나 지울 수 없다 |
| 10 | 작성자는 누가 신고했는지 알 수 없다 |

---

## ERD 변경 제안

```sql
-- V1 반영됨 (2026-10-07): 이 SQL은 erd/V1__common_schema.sql에 들어 있다. 기록용으로 남긴다.
-- 03 §5의 report 자리를 확정 (target_type을 UNIQUE에 포함)
CREATE TABLE report (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id      bigint        NOT NULL REFERENCES member (id),
    target_type      varchar(20)   NOT NULL,           -- POST | COMMENT
    target_id        bigint        NOT NULL,           -- 다형 참조라 FK 없음
    target_author_id bigint        NOT NULL REFERENCES member (id),
    reason           varchar(30)   NOT NULL,
    detail           varchar(200),
    snapshot_title   varchar(100),
    snapshot_content varchar(2000),
    status           varchar(20)   NOT NULL DEFAULT 'PENDING',
    handled_by       bigint        REFERENCES member (id),
    handled_at       timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_report UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT ck_report_target CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT ck_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'SEXUAL', 'PRIVACY', 'COPYRIGHT', 'OTHER')),
    CONSTRAINT ck_report_detail CHECK (reason <> 'OTHER' OR length(btrim(detail)) > 0),
    CONSTRAINT ck_report_status CHECK (status IN ('PENDING', 'HIDDEN', 'REJECTED', 'CLOSED_NO_TARGET')),
    CONSTRAINT ck_report_self   CHECK (reporter_id <> target_author_id)
);
CREATE INDEX ix_report_pending ON report (target_type, target_id) WHERE status = 'PENDING';

-- 숨김 (post, comment에 컬럼 추가)
ALTER TABLE post    ADD COLUMN hidden_at timestamptz, ADD COLUMN hidden_by bigint REFERENCES member (id),
                    ADD COLUMN hidden_reason varchar(30);
ALTER TABLE comment ADD COLUMN hidden_at timestamptz, ADD COLUMN hidden_by bigint REFERENCES member (id),
                    ADD COLUMN hidden_reason varchar(30);

-- 정지 (member에 컬럼 추가, status는 이미 SUSPENDED 있음)
ALTER TABLE member  ADD COLUMN suspended_until timestamptz,      -- 영구 정지는 null
                    ADD COLUMN suspended_reason varchar(200);
```

| 변경 | 이유 |
|---|---|
| `report.target_author_id` | 자기 것 신고를 DB에서도 막고(CHECK), 작성자 탈퇴 처리 때 찾기 쉽게 |
| `report.snapshot_*` | H-5 |
| 숨김은 `post`·`comment`의 컬럼 | 숨김 여부를 목록 쿼리 조건 하나로 거를 수 있다 |
| 공통 인덱스 영향 | `ix_post_feed`, `ix_post_blog`의 `WHERE`에 `hidden_at IS NULL` 추가 필요 |
| `comment`의 숨김 컬럼 3개 | 21 ERD는 `hidden_at`만 적고 "나민서 설계를 따름"이라 했으므로 위 세 컬럼(`hidden_at`, `hidden_by`, `hidden_reason`)으로 확정 |
| `report` 확정 | 25 `notification.report_id`에 `REFERENCES report (id)` FK를 걸 수 있다 (`ON DELETE SET NULL` 제안) |

## 결정 기록 추가분

| 날짜 | 안건 | 결정 |
|---|---|---|
| 2026-10-03 | 신고 대상·사유·처리 단위 | H-1, H-2, H-3 |
| 2026-10-03 | 자동 숨김 | H-4 |
| 2026-10-03 | 관리자 열람 | 신고 시점 스냅샷만 (H-5) |
| 2026-10-03 | 숨김 효과·표시 | H-6, H-7 |
| 2026-10-03 | 정지 기간·해제 | H-8, §6 |
| 2026-10-03 | 처리 결과 알림 | 신고자·작성자 모두 (H-10) |
| 2026-10-03 | 숨긴 댓글 | 별도 문구, 댓글 수에서 제외, 작성자에게만 원문 (H-7) |
| 2026-10-03 | 대상이 사라진 신고 | `CLOSED_NO_TARGET`으로 자동 종료, 스냅샷 30일 뒤 삭제 |
| 2026-10-06 | 신고·숨김 이벤트 | 20 §3-5를 따름 (`ReportResolved` 신고마다, `ACTION_TAKEN`/`NO_VIOLATION`, `ContentHidden.ownerId·postId`). `ReportCreated`는 공통에서 뺌 |
| 2026-10-06 | 숨긴 댓글 작성자 문구 | "숨겨졌어요 (나만 보여요)" (21과 통일), 수정·답글 불가 |
| 2026-10-06 | 숨긴 글의 좋아요 | 보존하고 함께 숨김, 숨김 해제 알림 없음 |
| 2026-10-06 | 알림의 신고 FK (회의 O2) | 별도 문서(46번) 없이 통합 ERD(V1)의 `fk_notification_report`로 처리. 46번 문서는 만들지 않는다 |

## 기존 결정 변경 (PR 설명에 따로 적음)

| 무엇을 | 왜 |
|---|---|
| **06 §7 R-2a 공용 조건과 `canRead`에 "숨기지 않은 글(`hidden_at IS NULL`, 작성자 본인 제외)" 추가** | 숨긴 글이 목록·검색에 새지 않게. 공용 조건 한곳만 고치면 모든 목록에 적용된다 |
| **03 §5 `report`의 `UQ(reporter, target)` → `UQ(reporter_id, target_type, target_id)`** | 글 42와 댓글 42가 같은 ID일 수 있다 |
| **03 `ix_post_feed`, `ix_post_blog` 조건에 `hidden_at IS NULL` 추가** | 위와 같은 이유 |

## 다른 담당자와 맞출 것

| 상대 | 내용 | 상태 |
|---|---|---|
| 강성찬 (이벤트) | §7을 20 §3-5에 맞춤 | 반영함 |
| 강성찬 (이벤트) | `MemberSuspended(memberId, until, reason)`를 20 목록에 추가 | 요청 |
| 강성찬 (이벤트) | `ReportResolved.targetType`의 `MEMBER` — 공통 신고 대상은 글·댓글뿐(H-1)이라 빼거나 "개인 확장"으로 표시 | 요청 |
| 강성찬 (댓글) | 숨긴 댓글 문구·수정·답글 불가(H-7), `comment_count` 규칙(§4-2) | 반영함 |
| 강성찬 (댓글) | `comment`에 `hidden_by`, `hidden_reason`도 추가 (21 ERD §15 2번) | 요청 |
| 강성찬 (알림) | `notification.report_id` → `report(id)` FK (`ON DELETE SET NULL`) | 통합 ERD(V1)에 반영됨 (`fk_notification_report`). 25:249·316의 "확정되면 FK 추가" 문장을 "V1 반영"으로 고쳐 주세요 (회의 O2) |
| 김민서 (좋아요) | 숨긴 글의 좋아요 보존 (§4-1) | 반영함 |
| 김민서 (트렌딩) | 숨긴 댓글 컬럼은 `comment.hidden_at` → 32 T-4 "작성자 외 댓글 작성자 수"에서 `hidden_at IS NULL`도 빼기 | 요청 |
| 전원 | 공용 조건(06 R-2a)에 `hidden_at IS NULL`을 넣는 것에 동의 (30·31·32·33·22·24 모두 이를 전제로 작성됨) | 화요일 |
