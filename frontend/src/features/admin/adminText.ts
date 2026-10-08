/** 관리자 화면 문구 (014 T039·T054). 서버 오류 문구가 있으면 그것을 먼저 쓴다. */
import { ApiError } from '../../api/client';
import type {
  CaseStatus,
  SuspensionDuration,
  TargetState,
  TargetType,
} from '../../api/types/moderation';

export const ADMIN_TEXT = {
  reportsTitle: '신고 관리',
  tabPending: '대기',
  tabHandled: '처리됨',
  empty: '신고가 없어요',
  loadFailed: '불러오지 못했어요',
  more: '더 보기',
  loading: '불러오는 중…',
  hide: '숨기기',
  reject: '문제없음',
  unhide: '숨김 해제',
  hideReasonLegend: '숨김 사유',
  alreadyHandled: '이미 처리된 신고예요',
  reload: '다시 불러오기',
  onlyMyReport: '내가 혼자 신고한 건은 처리할 수 없어요. 다른 관리자에게 맡겨 주세요',
  noTarget: '대상이 사라져 자동으로 닫힌 신고예요',
  hideConfirm: '이 콘텐츠를 숨길까요? 작성자 외에는 볼 수 없게 돼요',
  rejectConfirm: '문제없음으로 닫을까요? 신고한 사람에게 결과가 알려져요',
  unhideConfirm: '숨김을 해제할까요? 숨기기 전 모습으로 돌아가요',
  hidden: '숨겼어요',
  rejected: '문제없음으로 닫았어요',
  unhidden: '숨김을 해제했어요',
  directTitle: '글 주소로 숨기기',
  directLabel: '글 주소 또는 글 번호',
  directInvalid: '글 주소나 번호를 확인해 주세요',
  memberScreen: '회원 화면',
  automatic: '자동',
  snapshotNote: '신고 당시 내용이에요',
  reportCount: (n: number) => `신고 ${n}건`,
  current: (state: TargetState) => `현재: ${TARGET_STATE_LABELS[state] ?? state}`,
  suspend: '정지',
  liftSuspension: '정지 해제',
  suspendConfirm: '이 회원을 정지할까요? 모든 기기에서 로그아웃돼요',
  liftConfirm: '정지를 해제할까요?',
  suspended: '정지했어요',
  lifted: '정지를 해제했어요',
  suspendReasonLabel: '정지 사유',
  durationLegend: '정지 기간',
  permanent: '영구',
  history: '정지 이력',
  noHistory: '정지 이력이 없어요',
  adminCannotBeSuspended: '관리자는 정지할 수 없어요',
  withdrawnCannotBeSuspended: '탈퇴 신청한 회원은 정지할 수 없어요',
  retryLater: '잠시 후 다시 시도해 주세요',
} as const;

export const TARGET_TYPE_LABELS: Record<TargetType, string> = { POST: '글', COMMENT: '댓글' };

export const CASE_STATUS_LABELS: Record<CaseStatus, string> = {
  PENDING: '대기',
  HIDDEN: '숨김',
  REJECTED: '문제없음',
  CLOSED_NO_TARGET: '대상 없음',
};

export const TARGET_STATE_LABELS: Record<TargetState, string> = {
  PUBLIC: '공개',
  PRIVATE: '비공개',
  TRASHED: '휴지통',
  HIDDEN: '숨김',
  AUTHOR_WITHDRAWN: '작성자 탈퇴',
  GONE: '대상 없음',
  VISIBLE: '보임',
  DELETED: '삭제됨',
  POST_NOT_VISIBLE: '글을 볼 수 없음',
};

export const DURATION_LABELS: Record<SuspensionDuration, string> = {
  P1D: '1일',
  P7D: '7일',
  P30D: '30일',
  PERMANENT: '영구',
};

export const SUSPENSION_REASON_MAX = 200;

/** 오류 → 화면 문구. 서버 문구(마침표 없음)를 우선한다. */
export function adminErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'REPORT_ALREADY_HANDLED') {
      return ADMIN_TEXT.alreadyHandled;
    }
    if (error.status === 400 && error.errors.length > 0) {
      return error.errors[0].message;
    }
    if (error.status >= 500 || error.status === 429) {
      return ADMIN_TEXT.retryLater;
    }
    return error.message || ADMIN_TEXT.retryLater;
  }
  return ADMIN_TEXT.retryLater;
}

/** "글 주소로 숨기기" 칸: `/@handle/posts/123`, 전체 주소, 숫자만 → 글 번호. 아니면 null. */
export function parsePostRef(input: string): number | null {
  const text = input.trim();
  if (/^\d{1,18}$/.test(text)) {
    const id = Number(text);
    return id > 0 ? id : null;
  }
  const match = /\/posts\/(\d{1,18})(?:[/?#]|$)/.exec(text);
  if (!match) {
    return null;
  }
  const id = Number(match[1]);
  return id > 0 ? id : null;
}
