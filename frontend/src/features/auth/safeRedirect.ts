/**
 * 로그인 후 이동 주소 검사 (R-33, FR-039) — 서버 `SafeRedirectResolver`와 같은 규칙.
 *
 * - `/`로 시작하고 `//`·`/\`로 시작하지 않는다.
 * - 제어 문자·`\`가 없다.
 * - URL 디코딩한 값도 같은 조건을 만족한다(바뀌지 않을 때까지, 최대 3번).
 *
 * 통과하지 못하면 `/`.
 */
export const SAFE_REDIRECT_FALLBACK = '/';
const MAX_LENGTH = 2048;
const MAX_DECODE = 3;

function passes(value: string): boolean {
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return false;
  }
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    if (code < 0x20 || code === 0x7f || (code >= 0x80 && code <= 0x9f) || value[i] === '\\') {
      return false;
    }
  }
  return true;
}

export function isSafeRedirect(candidate: string | null | undefined): candidate is string {
  if (!candidate || candidate.length > MAX_LENGTH) {
    return false;
  }
  let value = candidate;
  for (let i = 0; i <= MAX_DECODE; i += 1) {
    if (!passes(value)) {
      return false;
    }
    let decoded: string;
    try {
      decoded = decodeURIComponent(value);
    } catch {
      return false;
    }
    if (decoded === value) {
      return true;
    }
    value = decoded;
  }
  return false;
}

export function safeRedirect(candidate: string | null | undefined): string {
  return isSafeRedirect(candidate) ? candidate : SAFE_REDIRECT_FALLBACK;
}

/**
 * 로그인 화면 주소의 돌아갈 곳. 다른 화면은 `/login?returnTo=…`로 보내고(005·006), 계약 이름은 `redirect`라 둘 다 읽는다.
 */
export function redirectFromSearch(search: string): string {
  const params = new URLSearchParams(search);
  return safeRedirect(params.get('returnTo') ?? params.get('redirect'));
}
