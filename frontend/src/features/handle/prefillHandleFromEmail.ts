/**
 * 이메일로 블로그 주소 미리 채우기 (FR-017, 08 §3 ①~⑧, R-15) — 서버 `HandleSuggester.bodyFromEmail`과 같은 규칙.
 *
 * ① @ 앞부분 ② + 뒤 버림 ③ 소문자 ④ `.` `-` → `_` ⑤ `[a-z0-9_]` 외 제거 ⑥ 연속 `_` 하나로·처음·끝 `_` 제거
 * ⑦ 30자로 자름(끝 `_` 제거) ⑧ 3자 미만 → `user_` + 6자리 난수.
 * ⑨ 가입 수단 접두어는 화면이 고정 글자로 붙이고, ⑩ 중복 번호는 서버 사용 가능 확인의 `suggestion`으로 바꿔 채운다.
 */
export const PREFILL_MAX_LENGTH = 30;
const MIN_LENGTH = 3;

/** ①~⑦. 3자 미만이면 null(→ ⑧). */
export function bodyFromEmail(email: string | null | undefined): string | null {
  if (email == null) {
    return null;
  }
  let local = email.trim();
  const at = local.indexOf('@');
  if (at >= 0) {
    local = local.slice(0, at);
  }
  const plus = local.indexOf('+');
  if (plus >= 0) {
    local = local.slice(0, plus);
  }
  let value = local
    .toLowerCase()
    .replace(/[.-]/g, '_')
    .replace(/[^a-z0-9_]/g, '')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
  if (value.length > PREFILL_MAX_LENGTH) {
    value = value.slice(0, PREFILL_MAX_LENGTH).replace(/_+$/, '');
  }
  return value.length < MIN_LENGTH ? null : value;
}

function randomDigits(): string {
  const values = new Uint32Array(1);
  crypto.getRandomValues(values);
  return String((values[0] ?? 0) % 1_000_000).padStart(6, '0');
}

/** ①~⑧. 이메일이 비어 있으면 빈 값. */
export function prefillHandleFromEmail(email: string): string {
  if (email.trim() === '') {
    return '';
  }
  return bodyFromEmail(email) ?? `user_${randomDigits()}`;
}
