/**
 * 화면 설정값 (constitution VII — 수치는 한곳에 둔다).
 */

/** 목록 뒤로 가기 복원 보관 시간(분) — 005 FR-018, 10 L-6, research R-10. */
export const LIST_RESTORE_TTL_MINUTES = 30;

/** 공통 머리말 왼쪽에 보이는 서비스 이름. 이름이 정해지면 여기만 바꾼다(08 예약어 목록·`index.html` 제목도 함께). */
export const SITE_NAME = 'BuildLOG';

/**
 * 다크 모드(016)를 켠 빌드인가. 규격만 공통이고 구현은 선택이라, 다크 모드를 만들지 않는 서비스는 `VITE_DARK_MODE=false`로 빌드한다
 * (FR-001, research R7). 끈 빌드는 테마 버튼이 없고 `<head>`에 `theme-init.js`도 들어가지 않아 라이트 값만 쓴다.
 */
export const DARK_MODE_ENABLED = import.meta.env.VITE_DARK_MODE !== 'false';
