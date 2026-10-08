/**
 * 기본 아바타 색·글자 (FR-051, R-34). 색 번호 = 블로그 주소 UTF-8 바이트의 FNV-1a 32비트 해시 mod 8.
 *
 * 8색 값은 `styles/tokens.css`의 `--avatar-1..8`(016 data-model §2-3)에 있다. 모두 흰 글자(`--color-on-fill`)와 대비 4.5:1
 * 이상이고(`tokenContrast.test.ts`), 원 자체가 배경이라 라이트·다크 화면 어디서나 같은 대비다.
 */
export const AVATAR_COLORS = [
  'var(--avatar-1)',
  'var(--avatar-2)',
  'var(--avatar-3)',
  'var(--avatar-4)',
  'var(--avatar-5)',
  'var(--avatar-6)',
  'var(--avatar-7)',
  'var(--avatar-8)',
] as const;

/** 닉네임·주소가 없을 때(탈퇴한 회원 등)의 원 색. */
export const AVATAR_FALLBACK_COLOR = 'var(--color-text-muted)';

export function fnv1a32(value: string): number {
  let hash = 0x811c9dc5;
  for (const byte of new TextEncoder().encode(value)) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash >>> 0;
}

export function avatarColorIndex(handle: string): number {
  return fnv1a32(handle) % AVATAR_COLORS.length;
}

/** 닉네임 첫 글자(코드 포인트 하나). 영문은 대문자. */
export function avatarInitial(nickname: string | null | undefined): string {
  const first = [...(nickname ?? '').trim()][0];
  return first ? first.toLocaleUpperCase('en-US') : '';
}
