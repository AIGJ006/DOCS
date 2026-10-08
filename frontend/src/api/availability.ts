import { apiGet } from './client';

/** 블로그 주소 사용 가능 확인 결과 (contracts `HandleAvailability`). */
export interface HandleAvailability {
  available: boolean;
  reason:
    | 'HANDLE_INVALID_FORMAT'
    | 'HANDLE_RESERVED'
    | 'HANDLE_BANNED_WORD'
    | 'HANDLE_DUPLICATE'
    | 'HANDLE_PREFIX_MISMATCH'
    | null;
  suggestion: string | null;
}

/** 닉네임 사용 가능 확인 결과 (contracts `NicknameAvailability`). */
export interface NicknameAvailability {
  available: boolean;
  code:
    | 'NICKNAME_INVALID_FORMAT'
    | 'NICKNAME_LETTER_REQUIRED'
    | 'NICKNAME_RESERVED'
    | 'NICKNAME_BANNED_WORD'
    | 'NICKNAME_DUPLICATE'
    | null;
}

/** 접두어(`go-`·`gi-`) 포함 전체 주소를 보낸다. 같은 IP 1분 30번. */
export function checkHandle(handle: string, signal?: AbortSignal): Promise<HandleAvailability> {
  return apiGet<HandleAvailability>(
    `/api/handles/availability?handle=${encodeURIComponent(handle)}`,
    { signal, notFoundScreen: false },
  );
}

/** 로그인한 회원이면 서버가 자기 자신을 중복에서 뺀다. */
export function checkNickname(
  nickname: string,
  signal?: AbortSignal,
): Promise<NicknameAvailability> {
  return apiGet<NicknameAvailability>(
    `/api/nicknames/availability?nickname=${encodeURIComponent(nickname)}`,
    { signal, notFoundScreen: false },
  );
}
