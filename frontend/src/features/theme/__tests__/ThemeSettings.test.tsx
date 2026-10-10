import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installMatchMedia, setPrefersDark, uninstallMatchMedia } from '../../../test/matchMedia';
import ThemeSettings from '../ThemeSettings';

/** 016 설정 화면 "화면 테마" (2026-10-10 머리말 버튼에서 옮김): 선택지 3개, 고르면 즉시 적용·저장, aria-live 안내. */
const root = document.documentElement;

describe('ThemeSettings', () => {
  beforeEach(() => {
    localStorage.clear();
    root.dataset.theme = 'light';
    root.dataset.themeChoice = 'system';
    installMatchMedia(false);
  });

  afterEach(() => {
    uninstallMatchMedia();
    localStorage.clear();
    vi.doUnmock('../../../config');
    vi.resetModules();
  });

  it('제목 "화면 테마" 칸에 선택지 3개가 이 순서로, 기본은 시스템', () => {
    render(<ThemeSettings />);
    const section = screen.getByRole('region', { name: '화면 테마' });
    const group = within(section).getByRole('group');
    const radios = within(group).getAllByRole('radio');
    expect(radios.map((radio) => radio.closest('label')?.textContent)).toEqual([
      '시스템 설정 따르기 (기본)',
      '라이트 모드',
      '다크 모드',
    ]);
    expect(screen.getByRole('radio', { name: '시스템 설정 따르기 (기본)' })).toBeChecked();
    expect(screen.getByTestId('theme-announcement')).toHaveAttribute('aria-live', 'polite');
  });

  it('고르면 바로 data-theme·data-theme-choice·저장 값이 바뀌고, 시스템으로 돌아오면 저장 값을 지운다', async () => {
    const user = userEvent.setup();
    render(<ThemeSettings />);

    await user.click(screen.getByRole('radio', { name: '다크 모드' }));
    expect(root.dataset.theme).toBe('dark');
    expect(root.dataset.themeChoice).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
    expect(screen.getByRole('radio', { name: '다크 모드' })).toBeChecked();
    expect(screen.getByTestId('theme-announcement')).toHaveTextContent('다크 테마로 바꿨어요');

    await user.click(screen.getByRole('radio', { name: '라이트 모드' }));
    expect(root.dataset.theme).toBe('light');
    expect(root.dataset.themeChoice).toBe('light');
    expect(localStorage.getItem('theme')).toBe('light');
    expect(screen.getByTestId('theme-announcement')).toHaveTextContent('라이트 테마로 바꿨어요');

    setPrefersDark(true);
    await user.click(screen.getByRole('radio', { name: '시스템 설정 따르기 (기본)' }));
    expect(root.dataset.theme).toBe('dark');
    expect(root.dataset.themeChoice).toBe('system');
    expect(localStorage.getItem('theme')).toBeNull();
    expect(screen.getByTestId('theme-announcement')).toHaveTextContent('기기 설정을 따라가요');
  });

  it('저장된 선택(theme-init.js가 정한 data-theme-choice)을 처음 선택으로 보인다', () => {
    localStorage.setItem('theme', 'dark');
    root.dataset.theme = 'dark';
    root.dataset.themeChoice = 'dark';
    render(<ThemeSettings />);
    expect(screen.getByRole('radio', { name: '다크 모드' })).toBeChecked();
  });

  it('다크 모드를 끈 빌드면 그리지 않는다', async () => {
    vi.resetModules();
    vi.doMock('../../../config', async (importOriginal) => ({
      ...(await importOriginal<typeof import('../../../config')>()),
      DARK_MODE_ENABLED: false,
    }));
    const { default: Disabled } = await import('../ThemeSettings');
    const { container } = render(<Disabled />);
    expect(container).toBeEmptyDOMElement();
  });
});
