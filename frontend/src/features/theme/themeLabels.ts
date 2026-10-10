import type { ThemeChoice } from './themeStore';

/** 설정 화면 "화면 테마" 선택지 — 이 순서로 보인다. 문구 끝에 마침표 없음. */
export const THEME_OPTIONS: ReadonlyArray<{ value: ThemeChoice; label: string }> = [
  { value: 'system', label: '시스템 설정 따르기 (기본)' },
  { value: 'light', label: '라이트 모드' },
  { value: 'dark', label: '다크 모드' },
];

/** 바꾼 뒤 화면 읽기 도구에 한 번 알리는 문구. */
export const THEME_ANNOUNCEMENTS: Record<ThemeChoice, string> = {
  light: '라이트 테마로 바꿨어요',
  dark: '다크 테마로 바꿨어요',
  system: '기기 설정을 따라가요',
};
