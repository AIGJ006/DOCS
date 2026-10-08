/**
 * 탈퇴·복구 화면 문구 (015 R12, docs/44 §2·§3). 끝 마침표 없음(README "정해진 것"). 숫자·주소는 화면이 텍스트 노드로 끼운다.
 */
export const WITHDRAW_TITLE = '회원 탈퇴';
export const WITHDRAW_EFFECTS_TITLE = '탈퇴하면 이렇게 돼요';
export const WITHDRAW_CONFIRM_LABEL = '위 내용을 확인했어요';
export const WITHDRAW_VERIFY_TITLE = '본인 확인';
export const WITHDRAW_PASSWORD_LABEL = '비밀번호';
export const WITHDRAW_CONFIRM_TEXT_LABEL = "'탈퇴'를 입력해 주세요";
export const WITHDRAW_SUBMIT = '탈퇴하기';
export const WITHDRAW_SUBMITTING = '탈퇴하는 중…';
export const WITHDRAW_CANCEL = '취소';
export const WITHDRAW_LOAD_FAILED = '탈퇴 안내를 불러오지 못했어요. 새로 고쳐 주세요';
export const WITHDRAW_LOCKED = '잠시 후 다시 시도해 주세요(약 15분)';
export const GENERIC_FAILED = '잠시 후 다시 시도해 주세요';

export function effectLines(p: {
  handle: string;
  postCount: string;
  commentCount: string;
  receivedLikeCount: string;
  deadline: string;
}): string[] {
  return [
    `블로그 @${p.handle}과 글 ${p.postCount}개가 바로 보이지 않아요`,
    `남의 글에 쓴 댓글 ${p.commentCount}개는 "탈퇴한 사용자의 댓글이에요"로 가려져요`,
    `30일(${p.deadline}까지) 안에 다시 로그인하면 모두 복구할 수 있어요`,
    `30일이 지나면 글·사진·받은 좋아요 ${p.receivedLikeCount}개가 완전히 삭제되고 되돌릴 수 없어요`,
    `블로그 주소 @${p.handle}은 다른 사람도, 나도 다시 쓸 수 없어요`,
  ];
}

export const PLACEHOLDER_NOTE =
  '답글이 달린 댓글은 내용 없이 "탈퇴한 사용자의 댓글이에요"로 자리만 남아요';

export const WITHDRAWN_TITLE = '탈퇴 신청이 완료됐어요';
export const withdrawnDeadlineLine = (deadline: string) =>
  `${deadline}까지 로그인하면 복구할 수 있어요`;
export const WITHDRAWN_HIDDEN_LINE = '그동안 블로그와 글은 다른 사람에게 보이지 않아요';
export const GO_HOME = '홈으로';

export const RESTORE_TITLE = '탈퇴 신청한 계정이에요';
export const restoreDeadlineLine = (deadline: string, days: number) =>
  `${deadline}까지 복구할 수 있어요 (${days}일 남음)`;
export const RESTORE_EFFECT_LINE = '복구하면 블로그·글·댓글이 모두 원래대로 돌아와요';
export const RESTORE_EXPIRED_TITLE = '복구 기한이 지났어요';
export const RESTORE_EXPIRED_LINE = '계정 정보는 곧 정리돼요. 같은 이메일로 다시 가입할 수 있어요';
export const RESTORE_SUBMIT = '복구하기';
export const RESTORE_SUBMITTING = '복구하는 중…';
export const LOGOUT = '로그아웃';
export const RESTORED_TOAST = '다시 오신 걸 환영해요';
