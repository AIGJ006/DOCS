import { ApiError } from '../../api/client';

/**
 * 댓글 화면 문구 (007 spec FR-019·021~024, data-model §4-3). 서버 문구가 오면 그것을 우선하고, 여기 값은 같은 문구의 화면 쪽
 * 사본이다(끝에 마침표 없음).
 */
export const COMMENT_TEXT = {
  heading: (count: number) => `댓글 ${count}`,
  empty: '첫 댓글을 남겨 보세요',
  loading: '불러오는 중…',
  loadFailed: '불러오지 못했어요',
  retry: '다시 시도',
  more: '댓글 더 보기',
  previous: '이전 댓글 보기',
  moreReplies: (n: number) => `답글 ${n}개 더 보기`,
  deleted: '삭제된 댓글이에요',
  hidden: '운영 정책에 따라 숨겨진 댓글이에요',
  hiddenMine: '숨겨졌어요 (나만 보여요)',
  withdrawn: '탈퇴한 사용자의 댓글이에요',
  withdrawnAuthor: '탈퇴한 사용자',
  edited: '· 수정됨',
  postAuthorBadge: '작성자',
  replyToWithdrawn: '@탈퇴한 사용자에게',
  replyTo: (nickname: string) => `@${nickname}에게`,
  placeholder: '댓글을 남겨 보세요',
  replyPlaceholder: '답글을 남겨 보세요',
  submit: '등록',
  submitting: '등록 중…',
  save: '저장',
  saving: '저장 중…',
  cancel: '취소',
  reply: '답글',
  edit: '수정',
  remove: '삭제',
  removing: '삭제 중…',
  loginPrompt: '로그인하고 댓글을 남겨 보세요',
  login: '로그인',
  verifyPrompt: '이메일 인증 후 댓글을 쓸 수 있어요',
  replyLabel: (nickname: string) => `${nickname}님 댓글에 답글`,
} as const;

/** 삭제 확인창 (FR-024). */
export const DELETE_CONFIRM = {
  message: '댓글을 삭제할까요? 삭제한 댓글은 되돌릴 수 없어요',
  confirmLabel: '삭제',
} as const;

/** 서버 이유 코드 → 문구 (data-model §4-3 + 공통 코드). */
export const COMMENT_ERROR_MESSAGES: Record<string, string> = {
  COMMENT_REQUIRED: '댓글 내용을 입력해 주세요',
  COMMENT_TOO_LONG: '댓글은 1000자까지 쓸 수 있어요',
  REPLY_TARGET_UNAVAILABLE: '답글을 달 수 없는 댓글이에요',
  COMMENT_HIDDEN: '숨겨진 댓글은 수정할 수 없어요',
  TOO_MANY_REQUESTS: '잠시 후 다시 시도해 주세요',
  LOGIN_REQUIRED: '로그인이 필요해요',
  EMAIL_NOT_VERIFIED: '이메일 인증 후 이용할 수 있어요',
  NOT_FOUND: '볼 수 없는 페이지예요',
  INVALID_CURSOR: '목록을 처음부터 다시 불러와 주세요',
};

export const RETRY_LATER = '잠시 후 다시 시도해 주세요';

/** 댓글 글자 수 상한 — 서버 `blog.comment.content-max`와 같다. 서버가 다시 센다. */
export const COMMENT_MAX = 1000;

/** 화면에 보이는 문자 단위(코드 포인트) 글자 수. 이모지 하나는 1자. */
export function countChars(text: string): number {
  return [...text].length;
}

/**
 * 요청 실패를 사용자 문구로. 503은 code와 상관없이 "잠시 후 다시 시도해 주세요"(research R17 — Redis 메모리 부족 때 서버가
 * `AUTOSAVE_UNAVAILABLE`을 보낸다). 칸 오류가 있으면 첫 칸 오류 문구.
 */
export function commentErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return RETRY_LATER;
  }
  if (error.status >= 500) {
    return RETRY_LATER;
  }
  const field = error.errors[0];
  if (field) {
    return COMMENT_ERROR_MESSAGES[field.code] ?? field.message;
  }
  return COMMENT_ERROR_MESSAGES[error.code] ?? (error.message || RETRY_LATER);
}
