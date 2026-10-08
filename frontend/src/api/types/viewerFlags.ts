/**
 * 화면 버튼 판단용 보는 사람 표시 플래그 (004 T060, research R-29, FR-045). 005 상세 응답의 `viewer` 필드가 서버 판정으로 채운다 —
 * 화면은 작성자 id를 직접 비교하지 않는다. 버튼을 숨기는 것은 보조이고 판정은 항상 서버가 한다(42 P-1).
 */
export interface ViewerFlags {
  /** 로그인한 회원 */
  loggedIn: boolean;
  /** 이메일 인증을 마친 회원 (비회원은 false) */
  emailVerified: boolean;
  /** 관리자 */
  isAdmin: boolean;
  /** 이 글의 작성자 (관리자도 자기 글이면 true) */
  isAuthor: boolean;
}

/** 비회원 플래그 */
export const ANONYMOUS_VIEWER: ViewerFlags = {
  loggedIn: false,
  emailVerified: false,
  isAdmin: false,
  isAuthor: false,
};
