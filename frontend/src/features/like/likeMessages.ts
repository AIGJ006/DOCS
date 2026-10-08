/**
 * 좋아요 안내 문구 (009 spec US1 #6·US2 #1~#3, FR-013·FR-015, research R10). 끝에 마침표를 붙이지 않는다(README "정해진 것").
 */
export const LIKE_MESSAGES = {
  /** 요청 실패 — 화면은 누르기 전 상태로 되돌린다 */
  failed: '좋아요를 반영하지 못했어요',
  /** 비회원 (뒤에 [로그인] 링크) */
  login: '로그인하고 좋아요를 눌러 보세요',
  /** 이메일 인증 전 회원 */
  verifyEmail: '이메일 인증 후 누를 수 있어요',
  /** 탈퇴 유예 계정의 남은 세션 */
  withdrawn: '탈퇴 신청한 계정이에요',
  /** 정지 계정의 남은 세션 */
  suspended: '정지된 계정이에요',
} as const;

/** 요청 과다(429)일 때 덧붙이는 안내. `Retry-After`(초)가 있으면 그 시간을 알린다. */
export function retryAfterText(seconds: number | null): string {
  return seconds && seconds > 0 ? `${seconds}초 뒤에 다시 눌러 주세요` : '잠시 후 다시 눌러 주세요';
}

/** 화면 안내 종류 */
export type LikeNoticeKind = 'failed' | 'login' | 'verifyEmail' | 'withdrawn' | 'suspended';

export interface LikeNotice {
  kind: LikeNoticeKind;
  /** 429면 다시 누를 수 있을 때까지 남은 초 */
  retryAfter?: number | null;
}

/** 안내 한 줄 */
export function noticeText(notice: LikeNotice): string {
  if (notice.kind === 'failed' && notice.retryAfter !== undefined) {
    return `${LIKE_MESSAGES.failed} (${retryAfterText(notice.retryAfter)})`;
  }
  return LIKE_MESSAGES[notice.kind];
}
