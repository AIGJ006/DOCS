# 팀 공용 서버 배포 안내

BuildLOG를 팀 공용 서버에 올리는 방법입니다. 터미널을 거의 쓰지 않고 **GitHub 화면에서 값만 넣으면** 됩니다.

## 어떻게 돌아가나요

1. `main` 브랜치에 코드가 합쳐지면(또는 GitHub **Actions → deploy → Run workflow**를 누르면) GitHub가 도커 이미지를 만듭니다.
2. 만든 이미지와 이 폴더의 파일들을 SSH(비밀번호, 포트 8822)로 서버의 `~/buildlog/` 폴더에 복사합니다.
3. GitHub Secrets 값으로 서버에 `~/buildlog/.env` 파일(권한 600, 본인만 읽기)을 만들고 `deploy.sh`를 실행합니다.
4. 서버의 nginx가 `https://<도메인>.java21.net` → `localhost:<APP_PORT>`로 넘겨 주면, 우리 쪽 edge(nginx 컨테이너)가
   `/blog/…`(사진)는 MinIO로, 나머지는 앱으로 보냅니다.

```
브라우저 ─https→ 서버 nginx(TLS) ─→ 127.0.0.1:<APP_PORT> = edge ─┬─ /blog/… → minio (사진 저장소)
                                                               └─ 그 밖   → app (Spring Boot + 화면)
                                    app → Crowfoot PostgreSQL · redis (서버 안 컨테이너)
```

**Secrets가 아직 비어 있으면** 배포를 건너뛰고 초록색(성공)으로 끝납니다. Actions 실행 화면 위쪽 안내(notice)에 빠진 이름이 나옵니다.

## 1. GitHub Secrets 넣기

GitHub 저장소 → **Settings → Secrets and variables → Actions → New repository secret**에서 하나씩 넣습니다.
값은 이 문서나 코드, 채팅에 **절대 적지 마세요.** (작은따옴표 `'`와 줄바꿈은 값에 쓸 수 없습니다.)

### 꼭 필요한 것 (하나라도 비면 배포를 건너뜀)

| 이름 | 무엇 | 어디서 얻나요 |
|---|---|---|
| `SSH_ADDRESS` | 서버 주소 | 팀원이 준 서버 배포 설명서 |
| `SSH_PORT` | SSH 포트 (`8822`) | 서버 배포 설명서 |
| `SSH_ID` | 서버 로그인 아이디 | 서버 배포 설명서 |
| `SSH_PASSWORD` | 서버 로그인 비밀번호 | 서버 배포 설명서 |
| `DB_ADDRESS` | DB 주소 | Crowfoot에서 발급한 DB 접속 정보 |
| `DB_PORT` | DB 포트 | Crowfoot 접속 정보 (보통 `5432`) |
| `DB_NAME` | DB 이름 | Crowfoot 접속 정보 |
| `DB_USERNAME` | DB 사용자 | Crowfoot 접속 정보 |
| `DB_PASSWORD` | DB 비밀번호 | Crowfoot 접속 정보 |
| `DB_SCHEMA` | 스키마 이름 | Crowfoot 접속 정보 (따로 없으면 `public`) |
| `APP_PORT` | 서버 nginx가 붙을 우리 포트 | 서버 담당 팀원에게 받은 번호 (Secret 대신 **Variables** 탭에 넣어도 됨) |
| `SITE_URL` | 사이트 주소, 끝에 `/` 없이 (예: `https://<도메인>.java21.net`) | 서버 담당 팀원에게 받은 도메인 (Variables 탭도 됨) |

### 앱이 뜨려면 필요한 것 (비어 있으면 배포가 빨간색으로 실패)

| 이름 | 무엇 | 어디서 얻나요 |
|---|---|---|
| `STORAGE_ROOT_USER` | 사진 저장소(MinIO) 관리자 이름 | 직접 정합니다 (영문, 3자 이상) |
| `STORAGE_ROOT_PASSWORD` | 위 관리자 비밀번호 | 직접 정합니다 (8자 이상, 길고 무작위로) |
| `BLOG_IMAGE_STORAGE_ACCESS_KEY` | 앱 전용 저장소 키 | 직접 정합니다 (8자 이상) |
| `BLOG_IMAGE_STORAGE_SECRET_KEY` | 앱 전용 저장소 비밀 키 | 직접 정합니다 (8자 이상, 길고 무작위로) |

> 저장소 값 네 개는 **처음 정한 뒤 바꾸지 마세요.** 관리자 값은 저장소가 처음 만들어질 때 박힙니다.

### 있으면 좋은 것 (비어 있으면 그 기능만 꺼짐)

| 이름 | 무엇 | 어디서 얻나요 | 비어 있으면 |
|---|---|---|---|
| `SPRING_MAIL_HOST` | 메일 서버 (Gmail: `smtp.gmail.com`) | 메일 업체 안내 | 가입 인증·비밀번호 재설정 메일이 안 나감 (앱은 정상) |
| `SPRING_MAIL_PORT` | 메일 포트 (Gmail: `587`) | 메일 업체 안내 | 587 |
| `SPRING_MAIL_USERNAME` | 메일 계정 (Gmail 주소) | 보낼 Gmail 계정 | |
| `SPRING_MAIL_PASSWORD` | 메일 비밀번호 | Google 계정 → 보안 → 2단계 인증 → **앱 비밀번호** | |
| `BLOG_MAIL_FROM` | 보내는 사람 주소 | 보통 비워 둠 (= `SPRING_MAIL_USERNAME`) | |
| `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET` | Google 로그인 | Google Cloud Console → API 및 서비스 → 사용자 인증 정보 | Google 로그인 버튼 숨김 |
| `OAUTH_GITHUB_CLIENT_ID` · `OAUTH_GITHUB_CLIENT_SECRET` | GitHub 로그인 | GitHub → Settings → Developer settings → OAuth Apps | GitHub 로그인 버튼 숨김 |
| `GEMINI_API_KEY` | AI 태그 추천 | Google AI Studio → API 키 | AI 태그 추천이 실패로 답함 (아래 참고) |

> GitHub는 `GITHUB_`로 시작하는 Secret 이름을 받지 않아서 GitHub 로그인 키만 `OAUTH_GITHUB_…`로 넣습니다.

### 선택: Variables 탭 (비밀 아님)

| 이름 | 값 | 뜻 |
|---|---|---|
| `COMPOSE_PROFILES` | 비움 / `ai` / `localdb` / `ai,localdb` | `ai`: 서버 안에 자체 AI(Ollama) 띄움(메모리 약 2~4GB 더 씀). `localdb`: Crowfoot DB 대신 서버 안 PostgreSQL |
| `OLLAMA_ENABLED` | `true` | `ai`를 켰을 때만 넣습니다 |
| `BLOG_AI_TAG_SUGGEST_ENABLED` | `false` | Gemini 키도 `ai`도 없을 때 AI 추천 기능을 끕니다 |

`localdb`를 쓸 때는 Secrets를 `DB_ADDRESS`=`postgres`, `DB_PORT`=`5432`, `DB_SCHEMA`=`public`으로 넣고,
`DB_NAME`·`DB_USERNAME`·`DB_PASSWORD`는 직접 정합니다 (처음 뜰 때 그 값으로 DB가 만들어집니다).

## 2. Crowfoot DB에서 확인할 것 — pg_trgm 확장

DB 첫 설정(V1)이 검색용 확장 `pg_trgm`을 만듭니다 (`CREATE EXTENSION IF NOT EXISTS pg_trgm`). 이 줄은 바꿀 수 없어서
**DB 사용자 권한에 따라** 미리 해 둘 일이 있습니다. 로컬 PostgreSQL 16으로 확인한 결과:

| Crowfoot이 준 DB 사용자가… | 결과 |
|---|---|
| DB 소유자이거나, DB에 `CREATE` 권한이 있음 | 그대로 됩니다. 확장이 우리 스키마(`DB_SCHEMA`) 안에 만들어집니다 |
| 스키마만 가진 일반 사용자 (DB `CREATE` 권한 없음) | **첫 배포가 실패합니다** (`permission denied to create extension "pg_trgm"`) |

두 번째 경우 Crowfoot(또는 DB 관리자)에게 아래 중 **하나**를 부탁하세요.

- 확장을 미리 만들어 주기: `CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;` (우리 스키마에 만들어도 됨)
- 또는 우리 사용자에게 DB 생성 권한 주기: `GRANT CREATE ON DATABASE <DB 이름> TO <DB 사용자>;`

앱은 `<스키마>,public` 순서로 찾기 때문에 확장이 `public`에 있어도 됩니다. 테이블·기록(`flyway_schema_history`)은 전부 `DB_SCHEMA`
스키마에 만들어지고 `public`에는 아무것도 만들지 않습니다. 스키마는 미리 있어야 합니다(앱이 만들지 않음).

## 3. 서버 nginx 설정 (서버 담당 팀원이 하는 일)

서버 배포 설명서의 nginx 설정 파일에서 우리 사이트 부분의 **두 줄**을 바꿉니다.

```nginx
server_name <도메인>.java21.net;          # ① 받은 도메인
location / {
    proxy_pass http://localhost:<APP_PORT>; # ② 받은 포트 (APP_PORT와 같은 번호)
    ...
}
```

그리고 아래가 **없으면 추가**합니다.

```nginx
client_max_body_size 12m;   # 꼭 필요: nginx 기본값은 1MB라 사진(최대 10MB) 업로드가 413으로 막힙니다
proxy_set_header Host $host;                                   # 사진 업로드 서명이 도메인과 맞아야 함
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;   # 댓글·로그인 제한이 실제 접속 주소를 씀
proxy_set_header X-Forwarded-Proto $scheme;                    # 소셜 로그인 돌아오는 주소가 https가 되게
```

(우리 edge는 Host가 `localhost:…`로 오면 `SITE_URL`의 도메인으로 바꿔 끼우지만, 위처럼 넣어 두는 것이 정석입니다.)
우리 포트는 서버 안(127.0.0.1)에서만 열리므로 바깥에서 직접 들어올 수 없습니다.

## 4. 소셜 로그인 주소 추가

Google·GitHub 개발자 콘솔의 "승인된 리디렉션 URI(Authorization callback URL)"에 운영 주소를 **추가**합니다 (로컬 주소는 그대로 둠).

- Google: `https://<도메인>/login/oauth2/code/google`
- GitHub: `https://<도메인>/login/oauth2/code/github`

GitHub OAuth App은 콜백 주소를 하나만 받으므로 운영용 OAuth App을 따로 만드는 것이 편합니다.

## 5. 첫 배포 후 확인할 것

1. GitHub **Actions → deploy** 실행이 초록색인지. 마지막 단계 로그에 `deploy: 완료 — 앱이 응답합니다`가 보이면 성공입니다.
   빨간색이면 그 단계 로그 끝부분(앱 로그 80줄)을 팀 채널에 공유하세요 (비밀값은 찍히지 않습니다).
2. 브라우저로 `https://<도메인>` 접속 → 첫 화면이 뜨는지, 주소창 자물쇠(https)가 있는지.
3. 이메일로 가입 → 인증 메일이 오는지 (스팸함도 확인). 안 오면 메일 Secrets 확인.
4. 글쓰기에서 사진 올리기 → 글에 사진이 보이는지. 413이면 서버 nginx `client_max_body_size`, 403이면 서버 nginx `Host` 줄을 확인.
5. Google·GitHub 로그인 (키를 넣었다면).
6. 검색창에 아무 단어 검색 → 결과 화면이 뜨는지 (pg_trgm 확인).

## 자주 하는 일

- **다시 배포**: Actions → deploy → Run workflow.
- **이전 버전으로 되돌리기**: 되돌릴 커밋을 `main`에 revert로 합치면 그 상태로 다시 배포됩니다.
- **서버에서 상태 보기** (SSH 접속 후): `cd ~/buildlog && docker compose -p buildlog -f docker-compose.prod.yml ps`
- **로그 보기**: `docker compose -p buildlog -f docker-compose.prod.yml logs --tail 100 app`
- **주의**: `docker compose down -v`는 사진·세션 데이터 볼륨(`buildlog-minio-data` 등)을 지웁니다. 쓰지 마세요.

## 이 폴더의 파일

| 파일 | 역할 |
|---|---|
| `docker-compose.prod.yml` | 운영 컨테이너 묶음 (edge·app·redis·minio·minio-init, 선택 ollama·postgres) |
| `nginx.conf.template` | edge 경로 나누기 (`/blog/` → 사진 저장소, 나머지 → 앱) |
| `deploy.sh` | 서버에서 이미지 불러오기 → 저장소 준비 → 실행 → 응답 확인 |
| `../.github/workflows/deploy.yml` | GitHub Actions 배포 작업 |
| `../backend/src/main/resources/application-prod.yml` | 운영 설정 (값은 모두 환경 변수) |
