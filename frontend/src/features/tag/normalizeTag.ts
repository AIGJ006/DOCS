/**
 * 태그 정규화 — 서버 `TagNormalizer`와 같은 규칙 (008 contracts/normalization.md §1, research R4·R12).
 *
 * ① NFKC → ② 보이지 않는 글자 제거 → ③ 앞뒤 공백 → ④ 맨 앞 `#` → ⑤ 소문자 → ⑥ 공백 묶음 `-` → ⑦ `-` 정리 → ⑧ 형식.
 * ⑨ 금칙어는 화면이 모른다 — 서버 400의 `tags[i]`가 그 칩을 오류 칩으로 만든다.
 *
 * 칩 미리보기·중복 판정·자동완성 검색어·012 검색창 `#태그`가 함께 쓴다. 서버와 같은 예시 표
 * (`backend/src/test/resources/tag/normalization-cases.csv`)로 시험한다.
 */

export type TagReasonCode = 'INVALID_TAG' | 'TAG_TOO_LONG';

export type NormalizeTagResult = { ok: true; name: string } | { ok: false; code: TagReasonCode };

/** 정규화된 이름의 최대 글자 수 (코드 포인트). */
export const TAG_MAX_LENGTH = 30;

const ONLY_ALLOWED = /^[가-힣a-z0-9._+#-]+$/;
const HAS_WORD = /[가-힣a-z0-9]/;

/** 서버 `InvisibleCharacters`와 같은 목록: 폭 0·방향 제어·제어 문자. */
function isInvisible(cp: number): boolean {
  return (
    (cp >= 0x200b && cp <= 0x200f) ||
    (cp >= 0x2060 && cp <= 0x2069) ||
    cp === 0xfeff ||
    (cp >= 0x202a && cp <= 0x202e) ||
    cp <= 0x1f ||
    (cp >= 0x7f && cp <= 0x9f)
  );
}

function stripInvisible(value: string): string {
  let out = '';
  for (const ch of value) {
    if (!isInvisible(ch.codePointAt(0) ?? 0)) {
      out += ch;
    }
  }
  return out;
}

/** ①~⑦. */
function clean(raw: string): string {
  let s = (raw ?? '').normalize('NFKC'); // ①
  s = stripInvisible(s); // ②
  s = s.trim(); // ③
  s = s.replace(/^#+/, '').trim(); // ④
  s = s.toLowerCase(); // ⑤ (JS는 언어 설정과 상관없다)
  s = s.replace(/\s+/gu, '-'); // ⑥
  s = s.replace(/-{2,}/g, '-'); // ⑦
  return s.replace(/^-|-$/g, '');
}

/** 태그 하나를 정규화한다. 실패하면 서버와 같은 이유 코드. */
export function normalizeTag(raw: string): NormalizeTagResult {
  const name = clean(raw);
  if (name === '' || !ONLY_ALLOWED.test(name) || !HAS_WORD.test(name)) {
    return { ok: false, code: 'INVALID_TAG' };
  }
  if ([...name].length > TAG_MAX_LENGTH) {
    return { ok: false, code: 'TAG_TOO_LONG' };
  }
  return { ok: true, name };
}

/** 검색어·주소 값용: 성공하면 이름, 실패하면 null (서버 `normalizeQuery`). */
export function normalizeTagQuery(raw: string): string | null {
  const result = normalizeTag(raw);
  return result.ok ? result.name : null;
}
