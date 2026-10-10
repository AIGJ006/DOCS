/**
 * 테마 선택 저장 (016 research R1·R8, data-model §1, contracts/theme.md §1).
 *
 * - 브라우저 `localStorage` 키 `theme` = `light` | `dark`. "시스템"은 저장하지 않는다(키 없음 = 시스템).
 * - 세 값 밖의 값·저장소 예외(사생활 보호 모드 등)는 `system`으로 본다. 쓰기 예외는 삼키고 화면만 바꾼다.
 * - 서버·계정에 저장하지 않고, 로그아웃·탈퇴 때 지우지 않는다(FR-003·FR-007).
 */
export type ThemeChoice = 'system' | 'light' | 'dark';
export type AppliedTheme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'theme';
export const PREFERS_DARK_QUERY = '(prefers-color-scheme: dark)';

export function isThemeChoice(value: unknown): value is ThemeChoice {
  return value === 'system' || value === 'light' || value === 'dark';
}

export function readChoice(): ThemeChoice {
  try {
    const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
    return saved === 'light' || saved === 'dark' ? saved : 'system';
  } catch {
    return 'system';
  }
}

export function writeChoice(choice: ThemeChoice): void {
  try {
    if (choice === 'system') {
      window.localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      window.localStorage.setItem(THEME_STORAGE_KEY, choice);
    }
  } catch {
    // 저장소가 막혀 있으면 이번 페이지에서만 유지한다.
  }
}

export function resolve(choice: ThemeChoice, prefersDark: boolean): AppliedTheme {
  if (choice === 'system') {
    return prefersDark ? 'dark' : 'light';
  }
  return choice;
}

/** 기기 설정이 다크인가. `matchMedia`가 없으면 라이트로 본다. */
export function prefersDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(PREFERS_DARK_QUERY).matches;
}
