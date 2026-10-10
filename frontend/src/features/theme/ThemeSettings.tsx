import { useId, useState } from 'react';
import { DARK_MODE_ENABLED } from '../../config';
import { THEME_ANNOUNCEMENTS, THEME_OPTIONS } from './themeLabels';
import { useTheme } from './useTheme';
import './theme.css';

/**
 * 설정 화면의 "화면 테마" 칸 (016 FR-004, 2026-10-10 민서 결정: 머리말 버튼에서 설정 화면 라디오로 옮김).
 * 시스템 설정 따르기(기본)·라이트·다크 중 고르면 바로 저장·적용한다(저장 키 `theme`, 시스템이면 저장 값 없음).
 * 다크 모드를 끈 빌드(`VITE_DARK_MODE=false`)에서는 그리지 않는다.
 */
export default function ThemeSettings() {
  if (!DARK_MODE_ENABLED) {
    return null;
  }
  return <ThemeSettingsSection />;
}

function ThemeSettingsSection() {
  const { choice, setChoice } = useTheme();
  const [announcement, setAnnouncement] = useState('');
  const id = useId();
  const titleId = `${id}-title`;

  return (
    <section aria-labelledby={titleId}>
      <h2 id={titleId}>화면 테마</h2>
      <fieldset className="theme-options">
        <legend>이 기기에서 쓸 화면 테마</legend>
        {THEME_OPTIONS.map(({ value, label }) => (
          <label key={value} className="theme-option">
            <input
              type="radio"
              name={`${id}-theme`}
              value={value}
              checked={choice === value}
              onChange={() => {
                setChoice(value);
                setAnnouncement(THEME_ANNOUNCEMENTS[value]);
              }}
            />
            <span>{label}</span>
          </label>
        ))}
      </fieldset>
      <p className="visually-hidden" aria-live="polite" data-testid="theme-announcement">
        {announcement}
      </p>
    </section>
  );
}
