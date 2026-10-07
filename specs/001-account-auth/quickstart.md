# Quickstart: 계정·인증 검증 시나리오

**Feature**: `001-account-auth` | **Plan**: [plan.md](./plan.md) | **API**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **데이터**: [data-model.md](./data-model.md)

이 문서는 기능이 끝까지 동작하는지 확인하는 실행 안내다. 구현 코드·마이그레이션·테스트 본문은 넣지 않는다.

## 1. 사전 조건

| 항목 | 내용 |
|---|---|
| 도구 | Docker + Docker Compose v2, JDK 21, Node.js LTS(화면 확인 시), `curl`, `jq` |
| 저장소 상태 | 공통 시작 템플릿(O9)의 Flyway V1 기준선(= [51](../../docs/51-erd-unified.md)의 SQL 블록)이 들어 있다. 이 기능은 마이그레이션을 추가하지 않는다 |
| 함께 필요한 기능 | 003 이미지 업로드(presign·complete — 프로필 사진·인증 전 거부 확인), 005 블로그 첫 응답(`/@{handle}` 301 확인). 없으면 해당 단계만 건너뛴다 |
| 환경 변수 (`.env`, 저장소에 커밋하지 않음) | `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET`(소셜 수동 확인 때만), `SPRING_MAIL_HOST=mailpit`·`SPRING_MAIL_PORT=1025`, `BLOG_AGREEMENT_TERMS_VERSION=2026-10-07`, `BLOG_AGREEMENT_PRIVACY_VERSION=2026-10-07`, MinIO 키 |
| 정책 파일 | `backend/src/main/resources/policy/` 아래 예약어·금칙어·예외 단어·흔한 비밀번호 목록(테스트용 일부 목록이면 충분) |

## 2. 기동

```bash
docker compose up -d                      # app + postgres + redis + minio (+ 개발용 mailpit)
docker compose ps                         # 모두 running/healthy
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/agreements/current   # 200
open http://localhost:8025                # Mailpit 웹 화면 (메일 확인)
```

이후 예시는 아래 셸 변수를 쓴다. 상태를 바꾸는 요청에는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다(로그인 뒤 토큰이 바뀌므로 매번 다시 읽는다).

```bash
BASE=http://localhost:8080
new_jar() { mktemp; }
xsrf()   { awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
csrf()   { curl -s -c "$1" -b "$1" "$BASE/api/auth/csrf" -o /dev/null; }
mail_token() { # 받는 사람($1)의 가장 최근 메일에서 token= 값을 꺼낸다 (Mailpit API)
  id=$(curl -s "http://localhost:8025/api/v1/search?query=to:$1" | jq -r '.messages[0].ID')
  curl -s "http://localhost:8025/api/v1/message/$id" | jq -r '.Text' | grep -o 'token=[A-Za-z0-9_-]*' | head -1 | cut -d= -f2; }
```

## 3. 자동 테스트

```bash
cd backend
./mvnw test                                   # 단위: HandlePolicy·HandleSuggester(08 §3 예시 12개)·NicknamePolicy(09 예시)·
                                              #       BioPolicy·PasswordPolicy·BannedWordFilter·LastActiveBucket
./mvnw verify                                 # 통합: Testcontainers PostgreSQL + Redis (Docker 필요)
./mvnw verify -Dit.test='*Friendship*,*LastActive*'   # 일부만
```

기대 결과: 모두 통과. 실패 0. 단위 테스트의 예시 결과가 원문 표와 100% 같다(SC-007).

## 4. 수동 검증 시나리오 (curl)

### 4-1. 이메일 가입 → 인증 전 거부 → 인증 → 허용 → 로그아웃 (US1, SC-002)

```bash
A=$(new_jar); csrf $A
curl -s -b $A -c $A -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"email":" Kim755030@Naver.com ","handle":"kim755030","password":"Blog#2026a","passwordConfirm":"Blog#2026a",
       "nickname":"김민서","agreements":{"termsVersion":"2026-10-07","privacyVersion":"2026-10-07"}}' \
  "$BASE/api/auth/signup" | jq                                         # 201, emailVerified=false
curl -s -b $A -c $A -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"contentType":"image/webp","size":20000,"thumbSize":0}' "$BASE/api/images/presign" | jq .code
                                                                       # "EMAIL_NOT_VERIFIED" (403, 003 API)
TOKEN=$(mail_token kim755030@naver.com)
curl -s -b $A -c $A -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d "{\"token\":\"$TOKEN\"}" "$BASE/api/auth/email-verification/confirm" | jq   # {"verified":true}
# 같은 토큰 다시 → 400 LINK_EXPIRED (SC-006)
curl -s -b $A -c $A -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"contentType":"image/webp","size":20000,"thumbSize":0}' "$BASE/api/images/presign" -o /dev/null -w '%{http_code}\n'   # 2xx
curl -s -b $A -c $A -X POST -H "X-XSRF-TOKEN: $(xsrf $A)" "$BASE/api/auth/logout" -w '%{http_code}\n'               # 204
curl -s -b $A -c $A -H "X-XSRF-TOKEN: $(xsrf $A)" "$BASE/api/me" | jq .code                                          # "LOGIN_REQUIRED"
```

확인할 DB 상태(선택): `member_agreement`에 TERMS·PRIVACY 2행(버전 `2026-10-07`), `auth_identity.provider_user_id = 'kim755030@naver.com'`.

### 4-2. 중복 가입·동시 가입·검증 오류 (US1 #2, US3 #4, SC-001)

```bash
# 같은 이메일(대소문자·공백만 다름) → 400, errors[].code = EMAIL_ALREADY_REGISTERED
# 같은 주소 kim755030로 서로 다른 이메일 20건 동시 → 201 1건, 나머지 400 HANDLE_DUPLICATE + details.handleSuggestion
seq 20 | xargs -P20 -I{} sh -c 'J=$(mktemp); curl -s -c $J -b $J '"$BASE"'/api/auth/csrf -o /dev/null;
  curl -s -b $J -H "X-XSRF-TOKEN: $(awk "\$6==\"XSRF-TOKEN\"{print \$7}" $J)" -H "Content-Type: application/json" \
  -d "{\"email\":\"race{}@x.com\",\"handle\":\"racehandle\",\"password\":\"Blog#2026a\",\"passwordConfirm\":\"Blog#2026a\",\"nickname\":\"경주{}\",\"agreements\":{\"termsVersion\":\"2026-10-07\",\"privacyVersion\":\"2026-10-07\"}}" \
  -o /dev/null -w "%{http_code}\n" '"$BASE"'/api/auth/signup' | sort | uniq -c        # 1 201, 19 400
```

비밀번호 `abc12345`(특수문자 없음)·`Abcdefg1!Abcdefg1`(17자)·`Kim755030!x`(이메일 앞부분 포함)·`Password1!`(흔한 목록) → 각각 400과 해당 `PASSWORD_*` 코드.

### 4-3. 블로그 주소·닉네임 사용 가능 확인 (US3, SC-007)

```bash
for h in kim755030 admin go-admin Kim755030 kim-min ab; do
  echo -n "$h → "; curl -s "$BASE/api/handles/availability?handle=$h" | jq -c; done
# kim755030 → available:false, HANDLE_DUPLICATE, suggestion kim755030_2 / admin → HANDLE_RESERVED, admin_2
# go-admin → HANDLE_RESERVED / Kim755030·kim-min·ab → HANDLE_INVALID_FORMAT
for n in ㅋㅋ '김 민서' 12345 관리자김 admin123 시1발 sh1t 시발점 KIM김민서; do
  echo -n "$n → "; curl -s -G --data-urlencode "nickname=$n" "$BASE/api/nicknames/availability" | jq -c; done
# 시발점만 available:true (금칙어 예외), 나머지는 09 §3 코드. 금칙어 응답에 단어가 들어 있지 않다
# 같은 IP에서 31번째 요청 → 429 TOO_MANY_REQUESTS + Retry-After
```

### 4-4. 로그인 보안 (US5, SC-003·004)

```bash
B=$(new_jar); csrf $B
for i in 1 2 3 4 5; do curl -s -b $B -c $B -H "X-XSRF-TOKEN: $(xsrf $B)" \
  --data-urlencode email=kim755030@naver.com --data-urlencode password=Wrong#123a "$BASE/api/auth/login" | jq -c '{code,message}'; done
# 5번 모두 401 INVALID_CREDENTIALS "이메일 또는 비밀번호가 올바르지 않아요"
curl -s -b $B -c $B -H "X-XSRF-TOKEN: $(xsrf $B)" --data-urlencode email=kim755030@naver.com \
  --data-urlencode password='Blog#2026a' "$BASE/api/auth/login" -i | grep -E '^HTTP|Retry-After'   # 429 + Retry-After
# 가입하지 않은 이메일로 같은 6번 → 상태·문구가 위와 100% 같다
# redirect=//evil.com 또는 https://evil.com 으로 로그인 성공 → redirectTo = "/"
```

잠금을 풀고 다음 시나리오로 가려면 15분 기다리거나 `docker compose exec redis sh -c "redis-cli --scan --pattern 'auth:login-fail:*' | xargs -r redis-cli del"`.

위조 IP 헤더(SC-010)는 로컬 Docker 네트워크가 사설 대역(기본 신뢰 대역)이라 curl로 재현하지 않고 `TrustedProxyIntegrationTest`로 확인한다: 신뢰 대역 밖에서 `X-Forwarded-For`를 바꿔 21번 보내면 21번째가 429.

### 4-5. 비밀번호 찾기·재설정·변경 (US4, SC-004·005)

```bash
C=$(new_jar); csrf $C
for e in kim755030@naver.com nobody@nowhere.dev; do curl -s -b $C -c $C -H "X-XSRF-TOKEN: $(xsrf $C)" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$e\"}" "$BASE/api/auth/password-reset" -w ' %{http_code}\n'; done
# 두 줄 모두 202 {"message":"가입된 이메일이면 안내 메일을 보냈어요"} — Mailpit에는 kim755030@naver.com 메일만
# 다른 쿠키 저장소 D로 로그인해 둔 뒤, 메일 토큰으로 새 비밀번호 저장 → 204
# D로 GET /api/me → 401 (모든 세션 삭제). 같은 토큰 재사용 → 400 LINK_EXPIRED
# 로그인 상태에서 POST /api/me/password (현재 비밀번호 맞음) → 204, 다른 쿠키 저장소의 세션은 401, 지금 세션은 200,
# Mailpit에 "비밀번호가 변경됐어요" 메일. 소셜 계정으로 같은 요청 → 400 PASSWORD_NOT_SUPPORTED
```

### 4-6. 프로필·설정 (US6, US8 #1·#2, SC-008)

```bash
# (A로 다시 로그인한 뒤)
curl -s -b $A -c $A -X PATCH -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d "{\"nickname\":\"민서킴\",\"bio\":\"$(printf 'a%.0s' {1..201})\",\"memberId\":999}" "$BASE/api/me/profile" | jq -c '.errors'
# [{"field":"bio","code":"BIO_TOO_LONG",...}] — GET /api/me/profile 의 nickname은 여전히 김민서 (아무것도 저장 안 됨)
curl -s -b $A -c $A -X PATCH -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"nickname":"민서킴","bio":"<script>alert(1)</script>"}' "$BASE/api/me/profile" | jq -c '{nickname,bio,nicknameChangeAvailableAt}'
# 200, bio는 원문 그대로 저장(화면에서는 글자로 보임), nicknameChangeAvailableAt = 지금 + 30일
curl -s -b $A -c $A -X PATCH -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"nickname":"민서킴2","bio":"바뀌면 안 됨"}' "$BASE/api/me/profile" | jq .code   # "NICKNAME_CHANGE_TOO_SOON" (409), bio 그대로
curl -s -b $A -c $A "$BASE/api/me/settings" | jq -c '.previousLogin'   # 첫 로그인이면 null, 두 번째 로그인부터 직전 로그인 일자·방식
```

프로필 사진(003 필요): presign `{purpose: PROFILE}` → 업로드 → complete → `PATCH /api/me/profile {profileImageId}` → 200. 다른 회원이 올린 ID·글용 사진 ID → 400 `INVALID_PROFILE_IMAGE`. 새 사진으로 바꾸면 이전 행의 `detached_at`이 채워진다(DB 확인).

### 4-7. 친구·최근 활동 (US7, US8, SC-009)

```bash
# 회원 A(kim755030)와 B(새로 가입, handle=friendb) 준비, 각각 쿠키 저장소 $A $B
curl -s -b $A -c $A -X PUT -H "X-XSRF-TOKEN: $(xsrf $A)" "$BASE/api/members/friendb/friend" | jq -c   # {"status":"REQUEST_SENT"}
curl -s -b $B -c $B "$BASE/api/members/kim755030/friend" | jq -c                                         # {"status":"REQUEST_RECEIVED"} — lastActive 키 없음
curl -s -b $B -c $B -X PUT -H "X-XSRF-TOKEN: $(xsrf $B)" "$BASE/api/members/kim755030/friend" | jq -c   # {"status":"FRIENDS","lastActive":{"bucket":"TODAY"}}
curl -s -b $A -c $A -X PATCH -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"lastActiveVisible":false}' "$BASE/api/me/settings" -o /dev/null
curl -s -b $A -c $A "$BASE/api/members/friendb/friend" | jq -c    # {"status":"FRIENDS"} — A가 끄면 A도 못 봄
curl -s -b $B -c $B "$BASE/api/members/kim755030/friend" | jq -c  # {"status":"FRIENDS"} — B도 A 것을 못 봄
curl -s -b $A -c $A -X PUT -H "X-XSRF-TOKEN: $(xsrf $A)" "$BASE/api/members/kim755030/friend" | jq .code   # "CANNOT_FRIEND_SELF"
curl -s -b $B -c $B -X DELETE -H "X-XSRF-TOKEN: $(xsrf $B)" "$BASE/api/members/kim755030/friend" | jq -c   # {"status":"NONE"}
curl -s "$BASE/api/me/friends" | jq .code                                                                     # 비회원 → "LOGIN_REQUIRED"
```

16가지 조합(비회원·친구 아님·요청 중·친구 × 대상 켬/끔 × 보는 사람 켬/끔)은 `LastActiveVisibilityMatrixTest`가 확인한다. 응답 JSON 어디에도 `lastActiveAt` 같은 시각 값이 없어야 한다.

### 4-8. 약관 재동의 (US5 #6, SC-011)

```bash
BLOG_AGREEMENT_TERMS_VERSION=2026-11-01 docker compose up -d app     # 현재 버전 올리기
# A로 로그인 → 응답 reagreementRequired: true
curl -s -b $A -c $A "$BASE/api/me/settings" | jq .code               # "REAGREEMENT_REQUIRED" (403)
curl -s -b $A -c $A -X PUT -H "X-XSRF-TOKEN: $(xsrf $A)" -H 'Content-Type: application/json' \
  -d '{"termsVersion":"2026-11-01","privacyVersion":"2026-10-07"}' "$BASE/api/me/agreements" -w '%{http_code}\n'   # 204
curl -s -b $A -c $A "$BASE/api/me/settings" -o /dev/null -w '%{http_code}\n'   # 200
```

### 4-9. Redis 장애 (FR-040, Edge Case H8)

```bash
docker compose pause redis
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/api/agreements/current"   # 200 (읽기 계속)
curl -s -b $A "$BASE/api/me" | jq .code                                   # "LOGIN_REQUIRED" (세션을 읽지 못함 → 비로그인)
# 로그인 시도 → 503 TEMPORARILY_UNAVAILABLE "잠시 후 다시 시도해 주세요"
# 인증 링크 확인·비밀번호 찾기 → 503. 사용 가능 확인 31번 → 제한 없이 200 (카운터 통과)
docker compose unpause redis
```

### 4-10. 블로그 주소 대문자 접속 (US3 #7, 005 필요)

```bash
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "$BASE/@Kim755030"   # 301 http://localhost:8080/@kim755030
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/@nosuchhandle"                 # 404
```

### 4-11. 소셜 로그인 (US2) — 수동, 실제 Google·GitHub 테스트 계정 필요

1. 브라우저에서 `/login` → [Google로 계속하기] → 인증 → **전체 페이지** `/signup/social`이 열린다. 개발자 도구에서 이 응답의 `Content-Security-Policy` `img-src`에 `https://lh3.googleusercontent.com https://avatars.githubusercontent.com`이 있고, 다른 화면(`/settings`) 응답에는 없다.
2. 닉네임이 소셜 이름으로 미리 채워지고(규칙에 안 맞으면 빈칸 + "닉네임을 입력해 주세요"), 주소는 `go-` 고정 + 본문 수정 가능, "프로필 사진 사용"이 기본 체크.
3. 같은 이메일로 이메일 가입한 계정이 있으면 "이 이메일로 가입한 계정이 이미 있어요" + [기존 계정으로 로그인]·[새 계정 만들기].
4. [가입 완료] → 계정 생성·로그인, 사진이 5초 안에 복사되어 연결(실패하면 기본 이미지 + 안내). DB에서 `image.purpose = 'PROFILE'`, `status = 'ATTACHED'` 1행, 소셜 사진 주소는 어떤 테이블에도 없다(SC-012).
5. 10분 넘게 머문 뒤 [가입 완료] → 410 `SOCIAL_SIGNUP_EXPIRED`, 계정 없음.
6. 로그아웃 → 같은 계정으로 다시 로그인 → 마무리 화면 없이 같은 계정. GitHub 쪽 로그인 이름을 바꿔도 같은 계정.

자동 테스트는 `SocialSignupIntegrationTest`가 가짜 OAuth2 사용자 정보로 2~6을 확인한다.

### 4-12. 화면 확인 (375px)

`/signup`, `/login`, `/signup/social`, `/settings`를 375px 폭에서 열어 가로 스크롤이 없는지, 비밀번호 규칙이 글자 + ✓로 표시되는지, 주소 칸에 대문자·`-`를 입력하면 소문자로 바뀌거나 입력되지 않는지, 한글 자판 상태 입력이 영문으로 들어가는지 확인한다. 로그아웃 뒤 개발자 도구 IndexedDB에 `draft:{memberId}:*` 키가 남아 있지 않고 테마 설정은 남아 있다.

## 5. Acceptance Scenario ↔ 테스트 매핑

| User Story | 시나리오 | 통합 테스트 클래스 (backend/src/test/java/com/team/blog/account/integration) |
|---|---|---|
| US1 이메일 가입·인증·로그아웃 | #1~#7 | `EmailSignupIntegrationTest`, `EmailVerificationIntegrationTest`, `AccountStatusGuardIntegrationTest`(#3) |
| US2 소셜 가입·로그인 | #1~#6 | `SocialSignupIntegrationTest` (+ 4-11 수동) |
| US3 블로그 주소·닉네임 | #1~#10 | `HandleSuggesterTest`·`NicknamePolicyTest`(단위), `ConcurrencyIntegrationTest`(#4·#10), 005 `PageShellIntegrationTest`(#7) |
| US4 비밀번호 찾기·변경 | #1~#6 | `PasswordResetIntegrationTest`, `PasswordChangeIntegrationTest` |
| US5 로그인 보안 | #1~#8 | `LoginSecurityIntegrationTest`, `ReagreementIntegrationTest`(#6), `TrustedProxyIntegrationTest`(#7) |
| US6 프로필·설정 | #1~#7 | `ProfileUpdateIntegrationTest` |
| US7 친구 | #1~#6 | `FriendshipIntegrationTest`, `ConcurrencyIntegrationTest`(#3) |
| US8 직전 로그인·최근 활동 | #1~#6 | `LoginSecurityIntegrationTest`(#1·#2), `LastActiveVisibilityMatrixTest`(#3~#6) |
| Edge: Redis 장애 | FR-040 | `RedisOutageIntegrationTest` |
