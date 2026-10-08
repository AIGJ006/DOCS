/**
 * 내 글 관리·삭제 확인창과 안내 문구 (006 T014). 문구는 spec FR 그대로이며 끝 마침표를 붙이지 않는다.
 */
import type { ConfirmOptions } from '../../components/ConfirmDialog';

/** [삭제] (FR-018) */
export const TRASH_CONFIRM: ConfirmOptions = {
  message: '휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요',
  confirmLabel: '휴지통으로',
  cancelLabel: '취소',
};

/** [영구 삭제] (FR-029) */
export const PURGE_CONFIRM: ConfirmOptions = {
  message: '영구 삭제하면 되돌릴 수 없어요. 댓글·좋아요도 함께 지워져요',
  confirmLabel: '영구 삭제',
  cancelLabel: '취소',
};

/** 비공개 → 공개 (FR-015, 004 공개 범위 변경) */
export const MAKE_PUBLIC_CONFIRM: ConfirmOptions = {
  message: '모든 사람이 볼 수 있게 돼요',
  confirmLabel: '공개로 바꾸기',
  cancelLabel: '취소',
};

/** [변경 취소] (FR-016) */
export const DISCARD_CONFIRM: ConfirmOptions = {
  message: '수정 중인 내용을 버리고 발행된 글로 되돌릴까요?',
  confirmLabel: '변경 취소',
  cancelLabel: '계속 수정',
};

/** 빈 임시글을 바로 지웠을 때 (FR-020) */
export const PURGED_EMPTY_TOAST = '빈 글이라 바로 삭제했어요';
/** 휴지통으로 옮겼을 때 (상세 화면 등) */
export const TRASHED_TOAST = '휴지통으로 옮겼어요';
/** 복구 (FR-027) */
export const RESTORED_TOAST = '복구했어요';
/** 영구 삭제 */
export const PURGED_TOAST = '영구 삭제했어요';
/** 휴지통 탭 위 안내 (FR-011) */
export const TRASH_NOTICE = '휴지통의 글은 30일 뒤 자동으로 완전히 삭제돼요';
/** 이미 다른 곳에서 처리된 글 (FR-013, 404 NOT_FOUND) */
export const ALREADY_HANDLED = '이미 처리된 글이에요. 목록을 다시 불러왔어요';
/** 서버 문구가 없을 때 */
export const ACTION_FAILED = '처리하지 못했어요. 잠시 후 다시 시도해 주세요';
