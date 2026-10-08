import type { ThemeChoice } from './themeStore';

/** 지금 선택별 아이콘·이름 (contracts/theme.md §4). 문구 끝에 마침표 없음. */
export const THEME_LABELS: Record<ThemeChoice, { icon: string; label: string }> = {
  system: { icon: '🖥', label: '테마: 시스템 설정 (누르면 라이트)' },
  light: { icon: '☀️', label: '테마: 라이트 (누르면 다크)' },
  dark: { icon: '🌙', label: '테마: 다크 (누르면 시스템 설정)' },
};

/** 바꾼 뒤 화면 읽기 도구에 한 번 알리는 문구. */
export const THEME_ANNOUNCEMENTS: Record<ThemeChoice, string> = {
  light: '라이트 테마로 바꿨어요',
  dark: '다크 테마로 바꿨어요',
  system: '기기 설정을 따라가요',
};
