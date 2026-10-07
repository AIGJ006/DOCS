/**
 * 이메일로 블로그 주소 칸을 미리 채운다 (07 §3 "이메일로 미리 채움"). 화면용 간단 규칙이다:
 * `@` 앞(`+` 앞까지)을 소문자로, 허용되지 않는 글자는 `_`로, 처음·끝 `_` 제거, 36자까지. 3자 미만이면 빈 값.
 * 중복이면 서버가 가입 때 추천 주소(`details.handleSuggestion`)를 준다 (08 §3).
 */
export function suggestHandleFromEmail(email: string): string {
  const local = email.trim().toLowerCase().split('@')[0]?.split('+')[0] ?? '';
  const body = local
    .replace(/[^a-z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 36)
    .replace(/_+$/g, '');
  return body.length >= 3 ? body : '';
}
