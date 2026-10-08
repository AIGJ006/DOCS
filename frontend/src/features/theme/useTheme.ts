import { useCallback, useEffect, useState } from 'react';
import {
  PREFERS_DARK_QUERY,
  isThemeChoice,
  prefersDark,
  readChoice,
  resolve,
  writeChoice,
  type ThemeChoice,
} from './themeStore';

/** `<html>`의 `data-theme`(적용 테마)·`data-theme-choice`(선택)를 같은 동기 코드 안에서 함께 바꾼다 (contracts §2). */
function apply(choice: ThemeChoice): void {
  const root = document.documentElement;
  root.dataset.theme = resolve(choice, prefersDark());
  root.dataset.themeChoice = choice;
}

/** 처음 값: `theme-init.js`가 정해 둔 `data-theme-choice`(다시 계산하지 않아 첫 그리기와 어긋나지 않음). 없으면 저장 값. */
function initialChoice(): ThemeChoice {
  const fromInit = document.documentElement.dataset.themeChoice;
  return isThemeChoice(fromInit) ? fromInit : readChoice();
}

/**
 * 테마 선택 상태 (016 research R8, FR-004~FR-006).
 *
 * - `setChoice`는 누른 그 이벤트 안에서 저장 + 두 속성을 바꾼다(새로 고침·지연 없음, 전환 효과 없음).
 * - "시스템"일 때만 기기 설정 변경(`matchMedia` `change`)을 구독해 `data-theme`를 바꾼다. 라이트·다크로 고정하면 구독을 끊는다.
 */
export function useTheme() {
  const [choice, setChoiceState] = useState<ThemeChoice>(initialChoice);

  useEffect(() => {
    const root = document.documentElement;
    if (root.dataset.themeChoice !== choice || !root.dataset.theme) {
      apply(choice);
    }
  }, [choice]);

  useEffect(() => {
    if (choice !== 'system' || typeof window.matchMedia !== 'function') {
      return undefined;
    }
    const query = window.matchMedia(PREFERS_DARK_QUERY);
    const onChange = (event: MediaQueryListEvent) => {
      document.documentElement.dataset.theme = event.matches ? 'dark' : 'light';
    };
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [choice]);

  const setChoice = useCallback((next: ThemeChoice) => {
    writeChoice(next);
    apply(next);
    setChoiceState(next);
  }, []);

  return { choice, setChoice };
}
