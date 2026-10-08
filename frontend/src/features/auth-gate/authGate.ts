import { ApiError } from '../../api/client';

/** 거부 안내 종류 (004 FR-029, 42 §6). */
export type AuthPromptKind = 'login' | 'verify-email' | 'restore' | 'suspended';

/**
 * 서버 거부를 안내 종류로 바꾼다. 401 → 로그인, 403 `EMAIL_NOT_VERIFIED`·`ACCOUNT_WITHDRAWN`·`ACCOUNT_SUSPENDED` → 각 안내.
 * 그 밖(404·400·다른 403 등)은 `null` — 호출한 화면이 처리한다.
 */
export function authPromptFor(error: unknown): AuthPromptKind | null {
  if (!(error instanceof ApiError)) {
    return null;
  }
  if (error.status === 401) {
    return 'login';
  }
  if (error.status !== 403) {
    return null;
  }
  switch (error.code) {
    case 'EMAIL_NOT_VERIFIED':
      return 'verify-email';
    case 'ACCOUNT_WITHDRAWN':
      return 'restore';
    case 'ACCOUNT_SUSPENDED':
      return 'suspended';
    default:
      return null;
  }
}

/**
 * 로그인 화면 주소. 지금 경로(쿼리 포함)를 `returnTo`로 붙여 로그인 뒤 그 페이지로 돌아오게 한다. 누르려던 행동은 기억하지 않는다 —
 * 돌아와서 다시 누른다(H6, 자동 실행 없음).
 */
export function loginPathFor(returnTo: string): string {
  return `/login?returnTo=${encodeURIComponent(returnTo)}`;
}

/** 탈퇴 유예 계정 복구 화면 (001). */
export const RESTORE_PATH = '/restore';
