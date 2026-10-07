# 글 작성·본문 정화 설계

> 작성일 2026-10-02 · 관련 요구사항: C-POST-1(글 작성: "본문의 스크립트는 실행되지 않는다"). 연관: [05 발행](./05-publish.md) §7 ②, [10 목록](./10-post-list.md) §2-1, [11 프로필](./11-profile.md) §3
> 팀 결정: 제목·본문(Markdown 원문 저장), 본문의 스크립트는 실행되지 않는다, **보안 정화 작업 추가.**
> 2026-10-07 회의 반영: 이미지 주소 규칙(23 §2-4 → §6), 남이 올린 사진은 링크로(S-6), CSP 저장소 허용(H3 → §8).

---

## 1. 결정 사항

| # | 안건 | 결정 |
|---|---|---|
| S-1 | Markdown 문법 | CommonMark + GFM(표·취소선·체크리스트·주소 자동 링크). 각주·수식·Mermaid는 개인 확장 |
| S-2 | 본문에 직접 쓴 HTML | **전부 글자로 표시** (`<b>`를 쓰면 `<b>`가 글자로 보임). 그래도 정화는 반드시 거친다 (이중 방어) |
| S-3 | 라이브러리 | **commonmark-java 0.30.0** + GFM 확장, **OWASP Java HTML Sanitizer 20260924.2** (2026-10-02 기준 최신) |
| S-4 | 허용 태그·속성 | §4 허용 목록. 나머지는 전부 제거 |
| S-5 | 링크 | `http`·`https`·`mailto`·상대 경로만. 외부 링크는 새 탭 + `rel="noopener noreferrer nofollow ugc"` |
| S-6 | 이미지 | **우리 저장소 사진 중 글 작성자가 올린 사진만** `<img>`로 표시하고, `src`는 지금의 공개 주소로 바꿔 쓴다 (§6, 23 §2-4). 외부 이미지 주소와 **남이 올린 우리 저장소 사진**은 `[이미지] 대체글` 링크로 바꾼다 (2026-10-07 회의) |
| S-7 | 임베드(iframe) | 공통에서 제외. 개인 확장 시 `youtube-nocookie.com`만 + `sandbox` |
| S-8 | 코드 강조 | 브라우저에서 highlight.js. 강조가 실패해도 코드는 읽힌다 (코드 블록 HTML은 서버가 만든다) |
| S-9 | 본문 제목 단계 | 한 단계씩 낮춘다 (`#` → `h2`, `##` → `h3`, `######` → `h6`) |
| S-10 | 글 제목 | 글자만. NFC 정규화, 보이지 않는 글자·방향 뒤집기 문자·제어 문자 제거 |
| S-11 | 미리보기 | `POST /api/markdown/preview`로 서버 렌더러와 같은 결과 |
| S-12 | 정화 규칙 변경 | `post.render_version` + 기존 글 다시 렌더링 배치 |
| S-13 | 보안 헤더 | CSP 외 (§8) |
| S-14 | 렌더링 부하 제한 | 1초, 목록·인용 중첩 20단계 |

---

## 2. 처리 흐름

```
contentMd (사용자 입력, Markdown)
 → ① 파싱            commonmark-java + GFM 확장 (표·취소선·체크리스트·자동 링크·제목 앵커 h-)
 → ② AST 변환        제목 한 단계 낮추기, 우리 사진 판별·src를 지금 공개 주소로(§6), 외부·남의 사진 → 링크, 중첩 깊이 검사(20단계)
 → ③ HTML 렌더링     escapeHtml(true): 직접 쓴 HTML은 글자로 / sanitizeUrls(true) / 외부 링크 rel·target, 이미지 lazy
 → ④ 정화            OWASP 허용 목록(§4) — 렌더러에 버그가 있어도 여기서 한 번 더 막는다
 → content_html 저장 + render_version 기록
     (①~④ 전체 1초 제한, 넘으면 400 CONTENT_TOO_COMPLEX)
```

| 원칙 | 내용 |
|---|---|
| 정화 위치 | **서버에서만.** 서버는 `contentMd`만 받는다. 브라우저가 만든 HTML은 받지 않는다 |
| 렌더링 시점 | 발행할 때 한 번 (05 §7). 조회할 때는 저장된 `content_html`을 그대로 쓴다 |
| 에디터 | 각자 자유. **WYSIWYG 에디터는 Markdown으로 내보내야 한다.** 글자 색·가운데 정렬처럼 Markdown에 없는 서식은 저장되지 않는다 |
| 한곳에 모음 | `markdown` 패키지의 `ContentRenderer` 하나만 HTML을 만든다. 발행·미리보기·다시 렌더링 배치가 모두 이것을 쓴다 |

---

## 3. 지원 문법

| 문법 | 결과 |
|---|---|
| 제목 `#`~`######` | `h2`~`h6` (S-9), `id="h-제목글자"` (같은 이름은 `-1`, `-2`…) |
| 굵게 `**` / 기울임 `*` / 취소선 `~~` / 인라인 코드 `` ` `` | `strong` / `em` / `del` / `code` |
| 목록, 번호 목록, 인용, 구분선 | `ul`·`ol`·`li`, `blockquote`, `hr` |
| 체크리스트 `- [x]` | `<input type="checkbox" disabled checked>` (항상 읽기 전용) |
| 표 (정렬 `:---`, `---:`) | `table`…, `align="left|center|right"` |
| 코드 블록 ` ```java ` | `<pre><code class="language-java">` (내용은 이스케이프) |
| 링크, 주소 자동 링크 | `a` (§5) |
| 이미지 | 우리 저장소 사진이고 글 작성자가 올린 것이면 `img`, 아니면 링크 (§6) |
| 직접 쓴 HTML | 글자로 표시 (S-2) |

---

## 4. 정화 허용 목록

| 태그 | 허용 속성 |
|---|---|
| `p` `br` `hr` `blockquote` `strong` `em` `del` `ul` `li` `pre` `table` `thead` `tbody` `tr` | — |
| `h2`~`h6` | `id` — `h-` + 글자·숫자·`_`·`-` (1~100자)만 |
| `ol` | `start` — 숫자만 |
| `input` | `type="checkbox"`, `checked`, `disabled`만 |
| `code` | `class` — `language-[a-z0-9+#-]{1,20}`만 |
| `th` `td` | `align` — `left`/`center`/`right`만 |
| `a` | `href`(http·https·mailto·상대 경로), `title`, `target="_blank"`, `rel="noopener noreferrer nofollow ugc"`만 |
| `img` | `src`(**공개 주소 `blog.image.public-base-url` + `/`로 시작할 때만** — ②에서 우리 사진은 이미 공개 주소로 바뀌어 있다), `alt`, `title`, `loading="lazy"`, `decoding="async"` |

목록에 없는 태그(`script` `style` `iframe` `object` `embed` `form` `svg` `math` `div` `span` …)와 속성(`on…`, `style`, `class`·`id`의 다른 값 …)은 **전부 제거**된다.

```java
static final PolicyFactory POLICY = new HtmlPolicyBuilder()
    .allowElements("p", "br", "hr", "blockquote", "h2", "h3", "h4", "h5", "h6", "strong", "em", "del",
                   "ul", "ol", "li", "input", "code", "pre", "table", "thead", "tbody", "tr", "th", "td", "a", "img")
    .allowAttributes("id").matching(Pattern.compile("h-[\\p{L}\\p{N}_-]{1,100}")).onElements("h2", "h3", "h4", "h5", "h6")
    .allowAttributes("start").matching(Pattern.compile("\\d{1,6}")).onElements("ol")
    .allowAttributes("type").matching(Pattern.compile("checkbox")).onElements("input")
    .allowAttributes("checked", "disabled").onElements("input")
    .allowAttributes("class").matching(Pattern.compile("language-[a-z0-9+#-]{1,20}")).onElements("code")
    .allowAttributes("align").matching(Pattern.compile("left|center|right")).onElements("th", "td")
    .allowUrlProtocols("http", "https", "mailto")
    .allowAttributes("href", "title").onElements("a")
    .allowAttributes("target").matching(Pattern.compile("_blank")).onElements("a")
    .allowAttributes("rel").matching(Pattern.compile("noopener noreferrer nofollow ugc")).onElements("a")
    .allowAttributes("src").matching((el, attr, v) -> v.startsWith(PUBLIC_BASE_URL + "/") ? v : null).onElements("img") // blog.image.public-base-url
    .allowAttributes("alt", "title").onElements("img")
    .allowAttributes("loading").matching(Pattern.compile("lazy")).onElements("img")
    .allowAttributes("decoding").matching(Pattern.compile("async")).onElements("img")
    .toFactory();
```

---

## 5. 링크

| 항목 | 규칙 |
|---|---|
| 허용 | `http:`, `https:`, `mailto:`, `/`로 시작하는 상대 경로 |
| 제거 | `javascript:`, `data:`, `vbscript:` 등 나머지 전부. 대소문자 섞기(`JaVaScRiPt:`), 엔티티(`&#106;`, `&colon;`), 퍼센트 인코딩(`%6A…`), 앞 공백, 참조형 링크(`[x]: javascript:…`)로 숨겨도 제거된다 (§9) |
| 외부 링크 | `target="_blank"` + `rel="noopener noreferrer nofollow ugc"` |
| 우리 사이트 링크 | 같은 탭, `rel` 없음 |

- `noopener noreferrer`: 새 탭으로 열린 외부 페이지가 원래 탭을 피싱 페이지로 바꿔치기하는 공격(tabnabbing)을 막는다.
- `nofollow ugc`: 검색 순위를 노린 스팸 링크의 효과를 없앤다.

---

## 6. 이미지

| 항목 | 규칙 |
|---|---|
| 주소 규칙 | 기준은 [23 §2-4](./23-image.md) (2026-10-07 회의에서 12에도 반영). 본문 원문에는 **절대 주소**가 그대로 있고, 원문(`content_md`)은 바꾸지 않는다 |
| 우리 사진 판별 | 주소가 공개 주소 `blog.image.public-base-url` 또는 옛 주소 목록 `blog.image.legacy-base-urls` 중 하나로 시작하고, 나머지가 저장 키 모양(`images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}`)일 때만. `ImageUrls.keyOf(url)` 한 곳에서 판별·키 추출 |
| 글 작성자가 올린 사진 | ②에서 본문의 키를 모아 `SELECT storage_key FROM image WHERE storage_key = ANY(:keys) AND uploader_id = :authorId` 한 번으로 확인한다 (23 §6-1). 미리보기(§7-6)는 로그인한 본인, 다시 렌더링(§7-7)은 글 작성자가 기준 |
| 우리 사진 출력 | `<img src="{공개 주소}/{저장 키}" alt="…" loading="lazy" decoding="async">` — 옛 주소로 쓰인 사진도 **지금의 공개 주소**로 바꿔 쓴다. CSS로 최대 너비 = 본문 폭 |
| 남이 올린 사진 | 우리 저장소 주소라도 글 작성자가 올리지 않았으면 외부 이미지처럼 링크로 바꾼다. 내 글에 연결(`post_image`)하지도 않는다 (05 §7 ⑥) |
| 외부 이미지 | `<img>`로 만들지 않고 **`[이미지] 대체글` 링크**로 바꾼다 (외부 링크 규칙 적용). 대체글이 없으면 주소를 보여준다 |
| 이유 | 외부 이미지는 독자 IP를 외부로 넘기고(추적 픽셀), 원본이 사라지면 깨지고, 부적절한 이미지로 바꿔치기될 수 있다. 남이 올린 사진은 연결하면 원래 주인이 지울 수 없게 되거나 남의 비공개 글 사진이 다시 공개된다 |
| 공개 주소를 바꿀 때 | 23 §2-4 순서 (옛 주소를 `legacy-base-urls`에 추가 → `RENDER_VERSION` 올려 다시 렌더링(§7-7) → CSP에서 옛 주소 제거) |
| 개인 확장 | 배지(shields.io 등)가 필요하면 허용 도메인을 추가한다 (AST 변환·정화·CSP `img-src` 세 곳을 함께) |

---

## 7. 그 밖의 규칙

| # | 항목 | 규칙 |
|---|---|---|
| 7-1 | 임베드 | 공통 제외 (S-7) |
| 7-2 | 코드 강조 | 브라우저에서 highlight.js (`language-xxx` class 사용). 코드 복사 버튼은 김민서 개인 확장 |
| 7-3 | 제목 `id` | `h-` 접두어로 페이지의 다른 요소 이름과 겹치지 않게 한다 (DOM clobbering 방지). 목차 화면은 개인 확장 |
| 7-4 | 글 제목 | NFC → 보이지 않는 글자(U+200B~U+200F, U+2060~U+2069, U+FEFF), 방향 제어 문자(U+202A~U+202E), 제어 문자 제거 → 앞뒤 공백 제거 → 1~100자 검사. 화면에서는 HTML 이스케이프 |
| 7-5 | 렌더링 부하 | 목록·인용 중첩 20단계 초과, 또는 렌더링 1초 초과 → 400 `CONTENT_TOO_COMPLEX` "글 구조가 너무 복잡해요 (목록·인용은 20단계까지)" |
| 7-6 | 미리보기 | `POST /api/markdown/preview {contentMd}` → `{html}`. 입력이 0.5초 멈추면 호출, 사용자당 1분에 60번, 로그인 필요. 에디터 자체 미리보기는 참고용이고 **최종 결과는 서버 렌더러가 기준** |
| 7-7 | 다시 렌더링 | 렌더러·정화 규칙을 바꾸면 `RENDER_VERSION` 상수를 올린다. 배치가 `render_version < 현재`인 발행 글을 100개씩 다시 렌더링한다 (`content_html`·`excerpt` 갱신, `edited_at`·`edit_version`은 그대로) |
| 7-8 | 댓글 | 글자만 (Markdown 없음, 줄바꿈만 유지). 댓글 안건에서 확정 |

---

## 8. 보안 헤더 (2차 방어)

정화를 뚫고 스크립트가 들어와도 브라우저가 실행하지 않게 한다.

```
Content-Security-Policy: default-src 'self'; script-src 'self'; connect-src 'self' {저장소 공개 주소};
                         img-src 'self' {저장소 공개 주소} data: blob:; style-src 'self' 'unsafe-inline';
                         object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
```

`{저장소 공개 주소}`는 `blog.image.public-base-url`의 출처 하나다. CSP와 정화 허용 목록(§4)이 같은 설정값을 쓴다 (2026-10-07 회의 H3).

| 항목 | 이유 |
|---|---|
| `script-src 'self'` | 인라인 스크립트·외부 스크립트 실행 금지. highlight.js 등은 우리 서버에서 제공 |
| `connect-src 'self' {저장소 공개 주소}` | 브라우저 → 저장소 Presigned PUT 직접 업로드 (04 §4-1 ④, 23 §2-2 CORS). REST API는 같은 도메인이라 `'self'` |
| `img-src 'self' {저장소 공개 주소} data: blob:` | 외부 이미지 차단 (S-6과 일치). `data:`는 기본 프로필 아이콘(SVG) 등 우리 화면 요소용, `blob:`은 오프라인 대기 사진 미리보기(`URL.createObjectURL`, 04 §4-3)와 프로필 자르기 미리보기(11 §4-1)용 |
| 소셜 가입 마무리 화면만 | 이 화면의 HTML 응답에만 `img-src`에 `https://lh3.googleusercontent.com https://avatars.githubusercontent.com`을 더한다 (화면별 CSP, 11 §4-2). 다른 화면에는 넣지 않는다. React는 처음 받은 CSP를 화면 이동 때 바꾸지 않으므로, 이 화면은 소셜 로그인 콜백의 리다이렉트로 **전체 페이지로 열고**, 가입을 마치면 전체 페이지 이동으로 넘어간다 |
| `style-src 'unsafe-inline'` | 에디터 라이브러리(Toast UI 등)가 인라인 스타일을 쓴다. 본문의 `style` 속성은 정화 단계에서 이미 제거된다 |
| `frame-ancestors 'none'` | 다른 사이트가 우리 페이지를 iframe에 넣어 클릭을 가로채는 공격 방지 |

---

## 9. 검증 결과 (2026-10-02)

commonmark-java 0.30.0 + OWASP Sanitizer 20260924.2로 §2 파이프라인을 실제로 구현해 실행했다. **52개 테스트 모두 통과.**

**검사 방법:** 결과 HTML에서 실제 태그만 꺼내 ① 태그 이름이 허용 목록에 있는지 ② 속성 이름에 `on…`·`style`이 없는지 ③ `href`·`src` 값을 브라우저처럼 엔티티를 풀고 공백·제어 문자를 지운 뒤 `javascript:`·`vbscript:`·`data:`로 시작하지 않는지 확인한다. 검사기 자체도 위험한 HTML 6개를 모두 잡고, 안전한 HTML 3개를 위험으로 잘못 판정하지 않는 것을 확인했다.

### 9-1. XSS 공격 문자열 (32개 모두 무해화)

| 분류 | 입력 예 | 결과 |
|---|---|---|
| 직접 쓴 태그 | `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>`, `<svg onload=…>`, `<iframe src="javascript:…">`, `<details ontoggle=…>`, `<div style="…">`, `<form action=…>` | 글자로 표시됨 |
| 위험한 링크 주소 | `[클릭](javascript:alert(1))`, `JaVaScRiPt:`, `&#106;avascript:`, `&#x6A;&#x61;…`, `javascript&colon;`, `%6A%61…`, 앞 공백, `<javascript:…>`, 참조형 `[x]: javascript:…`, `vbscript:`, `data:text/html…` | 링크 제거 또는 무해한 주소 |
| 이미지 | `![x](javascript:…)`, `![x](data:image/svg+xml…)`, `![x" onerror="…](CDN 주소)` | 외부·위험 주소는 링크로 바뀌고 정화됨. 대체글의 `onerror`는 `alt` 값 안의 글자로만 남음 |
| 속성 탈출 | `[x](CDN주소" onclick="…)`, `<https://…" onmouseover="…>`, `www.…/"onmouseover="…` | 속성 추가 안 됨 |
| 문맥 탈출 | ` ``` ` 안의 `</code></pre><script>`, `<<script>script>…`, `<math><mtext><table><mglyph><style>…` | 글자로 표시됨 |
| 다른 문법 안 | 표 칸·체크리스트·제목 안의 `<img onerror>`, `<script>` | 글자로 표시됨 |

### 9-2. 정상 문법 (13개)

```html
<h2 id="h-원인">원인</h2>
<h3 id="h-해결-방법">해결 방법</h3>
<p><strong>굵게</strong> <em>기울임</em> <del>취소</del> <code>inline</code></p>
<table><thead><tr><th align="left">이름</th><th align="right">값</th></tr></thead>…</table>
<ul><li><input type="checkbox" disabled="" checked="" /> 완료</li>…</ul>
<pre><code class="language-java">List&lt;String&gt; xs &#61; new ArrayList&lt;&gt;();</code></pre>
<p><a href="/&#64;kim755030/posts/1">내부</a>
   <a rel="noopener noreferrer nofollow ugc" href="https://spring.io" target="_blank">외부</a> …
   <img src="https://cdn.devlog.example/a.webp" alt="업로드" loading="lazy" decoding="async" />
   <a rel="noopener noreferrer nofollow ugc" href="https://img.shields.io/…" target="_blank">[이미지] 외부 배지</a></p>
<p>&lt;b&gt;직접 쓴 HTML&lt;/b&gt;</p>
```

- 내부 링크의 `@`는 정화기가 `&#64;`로 인코딩한다. 브라우저는 `@`로 읽으므로 주소는 정상이다.
- 한글 제목 `id`(`h-원인`)가 유지된다.

### 9-3. 부하 제한과 제목 정리 (7개)

| 테스트 | 결과 |
|---|---|
| 인용 25단계 / 목록 25단계 | 거부 |
| 인용 15단계 | 허용 |
| 본문 10만 자 렌더링 | 14ms |
| 제목의 U+202E(방향 뒤집기), 폭 0 문자 | 제거 |
| NFD 한글 제목 | NFC로 정규화 |

---

## 10. 스키마

[03-erd.md](./03-erd.md)에 반영했다.

| 변경 | 내용 |
|---|---|
| `post.render_version` | `integer NOT NULL DEFAULT 1`. 다시 렌더링할 글 찾기 (§7-7) |

---

## 11. 공통 완료 기준 (C-POST-1)

| # | 기준 |
|---|---|
| 1 | §9-1의 공격 문자열을 본문·제목에 넣고 발행해도 결과 HTML에 실행 가능한 스크립트가 없고, 브라우저 E2E에서 알림창이 뜨지 않는다 |
| 2 | 본문에 직접 쓴 HTML은 글자로 보인다 |
| 3 | §3의 문법이 의도대로 렌더링되고, 본문에 `h1`이 없다 |
| 4 | 외부 링크는 새 탭 + `noopener noreferrer nofollow ugc`, 외부 이미지와 남이 올린 사진은 링크로 바뀐다. 옛 공개 주소로 쓴 우리 사진은 지금 공개 주소의 `img`로 보인다 |
| 5 | 미리보기 결과와 발행 결과가 같다 |
| 6 | 목록·인용이 20단계를 넘거나 렌더링이 1초를 넘으면 발행되지 않는다 |
| 7 | 응답에 §8의 보안 헤더가 있다 (저장소 공개 주소·`blob:`, 소셜 가입 마무리 화면의 사진 호스트 예외 포함) |
| 8 | 정화 규칙을 바꾸고 `RENDER_VERSION`을 올리면 기존 발행 글이 새 규칙으로 다시 렌더링된다 |
