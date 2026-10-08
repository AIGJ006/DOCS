import { apiGet, apiPost } from './client';

/** 탈퇴 안내 숫자 (015 contracts `WithdrawalPreview`). 숫자는 화면을 연 순간 값이다. */
export interface WithdrawalPreview {
  handle: string;
  /** 내 글 수 (휴지통·임시 포함 전체) */
  postCount: number;
  /** 남의 글에 쓴 댓글 수 */
  commentCount: number;
  /** 내 글이 받은 좋아요 합 */
  receivedLikeCount: number;
  /** 지금 신청하면 복구 기한 (ISO-8601 UTC) */
  restoreDeadline: string;
  /** 본인 확인 방법: 이메일 가입은 비밀번호, 소셜 가입은 "탈퇴" 입력 */
  verification: 'PASSWORD' | 'CONFIRM_TEXT';
}

/** 탈퇴 신청 본문. 본인 확인 방법에 맞는 칸만 보낸다. 사유 칸은 없다(FR-004). */
export interface WithdrawRequest {
  confirmed: boolean;
  password?: string;
  confirmText?: string;
}

export interface WithdrawResult {
  restoreDeadline: string;
}

export function getWithdrawalPreview(): Promise<WithdrawalPreview> {
  return apiGet<WithdrawalPreview>('/api/me/withdrawal');
}

/** 성공하면 서버가 이 기기 세션까지 모두 끊는다. */
export function withdraw(body: WithdrawRequest): Promise<WithdrawResult> {
  return apiPost<WithdrawResult>('/api/me/withdraw', body);
}

/** 탈퇴 유예 중 복구. 이미 활동 중이면 변화 없이 `ACTIVE`. */
export function restore(): Promise<{ status: 'ACTIVE' }> {
  return apiPost<{ status: 'ACTIVE' }>('/api/me/restore');
}
