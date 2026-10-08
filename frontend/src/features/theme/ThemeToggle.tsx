import { useState } from 'react';
import { DARK_MODE_ENABLED } from '../../config';
import { THEME_ANNOUNCEMENTS, THEME_LABELS } from './themeLabels';
import { nextChoice } from './themeStore';
import { useTheme } from './useTheme';
import './theme.css';

/**
 * 머리말 맨 오른쪽 테마 전환 버튼 (016 FR-004, US2). 시스템 → 라이트 → 다크 → 시스템.
 * 다크 모드를 끈 빌드(`VITE_DARK_MODE=false`)에서는 그리지 않는다.
 */
export default function ThemeToggle() {
  if (!DARK_MODE_ENABLED) {
    return null;
  }
  return <ThemeToggleButton />;
}

function ThemeToggleButton() {
  const { choice, setChoice } = useTheme();
  const [announcement, setAnnouncement] = useState('');
  const { icon, label } = THEME_LABELS[choice];

  const onClick = () => {
    const next = nextChoice(choice);
    setChoice(next);
    setAnnouncement(THEME_ANNOUNCEMENTS[next]);
  };

  return (
    <span className="theme-toggle">
      <button
        type="button"
        className="theme-toggle-button"
        data-testid="theme-toggle"
        data-choice={choice}
        aria-label={label}
        title={label}
        onClick={onClick}
      >
        <span aria-hidden="true">{icon}</span>
      </button>
      <span className="visually-hidden" aria-live="polite" data-testid="theme-announcement">
        {announcement}
      </span>
    </span>
  );
}
