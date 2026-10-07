import { ApiError } from '../../api/client';
import { logout as postLogout } from '../../api/auth';

/**
 * 로그아웃 (FR-041, 07 §7, R-28).
 *
 * ① 002의 미전송 작업 1회 전송(최대 3초, 실패하면 "보내지 못한 작업이 지워져요" 확인)
 * ② 002 `clearMemberDrafts(memberId)` — IndexedDB `draft:{memberId}:*`·`draft-backup:{memberId}:*` 삭제
 * ③ `POST /api/auth/logout` ④ `location.assign('/')`. 테마 설정(016)은 건드리지 않는다.
 *
 * 002 편집기 모듈(`features/editor/localDraftStore.ts`)이 아직 없어서 ①②는 등록 방식으로 둔다.
 * 002가 들어오면 앱 시작 때 `registerLogoutCleanup({ flushPendingWork, clearMemberDrafts })`로 연결한다.
 */
export interface LogoutCleanup {
  /** 미전송 작업을 한 번 보낸다. 모두 보냈으면 true. */
  flushPendingWork?: (memberId: number) => Promise<boolean>;
  /** 그 회원의 브라우저 임시 글·백업을 모두 지운다. */
  clearMemberDrafts?: (memberId: number) => Promise<void>;
}

export const FLUSH_TIMEOUT_MS = 3000;
export const UNSENT_WORK_CONFIRM = '보내지 못한 작업이 지워져요. 그래도 로그아웃할까요?';

let cleanup: LogoutCleanup = {};

export function registerLogoutCleanup(next: LogoutCleanup): void {
  cleanup = { ...cleanup, ...next };
}

/** 테스트 전용. */
export function resetLogoutCleanupForTests(): void {
  cleanup = {};
}

async function flushWithin(
  memberId: number,
  flush: NonNullable<LogoutCleanup['flushPendingWork']>,
) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<boolean>((resolve) => {
    timer = setTimeout(() => resolve(false), FLUSH_TIMEOUT_MS);
  });
  try {
    return await Promise.race([flush(memberId).catch(() => false), timeout]);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 로그아웃한다. 사용자가 미전송 작업 삭제를 취소하면 아무것도 하지 않고 false를 돌려준다.
 * 이미 로그아웃된 세션(401)이어도 홈으로 간다. 그 밖의 서버 오류는 던진다.
 */
export async function logout(memberId: number): Promise<boolean> {
  if (cleanup.flushPendingWork) {
    const sent = await flushWithin(memberId, cleanup.flushPendingWork);
    if (!sent && !window.confirm(UNSENT_WORK_CONFIRM)) {
      return false;
    }
  }
  if (cleanup.clearMemberDrafts) {
    await cleanup.clearMemberDrafts(memberId);
  }
  try {
    await postLogout();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) {
      throw error;
    }
  }
  window.location.assign('/');
  return true;
}
