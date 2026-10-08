import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installMatchMedia, uninstallMatchMedia } from '../../../test/matchMedia';
import ThemeToggle from '../ThemeToggle';

/** 016 T026: 아이콘·이름 3가지, 누르면 다음 상태·즉시 적용, aria-live 안내 (contracts/theme.md §4). */
const root = document.documentElement;

describe('ThemeToggle', () => {
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

  it('누를 때마다 시스템 → 라이트 → 다크 → 시스템, 즉시 적용·안내 (US2 #1)', async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);
    const button = screen.getByRole('button', { name: '테마: 시스템 설정 (누르면 라이트)' });
    expect(button).toHaveAttribute('title', '테마: 시스템 설정 (누르면 라이트)');
    expect(button.querySelector('[aria-hidden="true"]')?.textContent).toBe('🖥');
    expect(screen.getByTestId('theme-announcement')).toHaveAttribute('aria-live', 'polite');

    await user.click(button);
    expect(root.dataset.theme).toBe('light');
    expect(root.dataset.themeChoice).toBe('light');
    expect(button).toHaveAccessibleName('테마: 라이트 (누르면 다크)');
    expect(button).toHaveAttribute('title', '테마: 라이트 (누르면 다크)');
    expect(button.textContent).toBe('☀️');
    expect(screen.getByTestId('theme-announcement')).toHaveTextContent('라이트 테마로 바꿨어요');

    await user.click(button);
    expect(root.dataset.theme).toBe('dark');
    expect(button).toHaveAccessibleName('테마: 다크 (누르면 시스템 설정)');
    expect(button.textContent).toBe('🌙');
    expect(screen.getByTestId('theme-announcement')).toHaveTextContent('다크 테마로 바꿨어요');

    await user.click(button);
    expect(root.dataset.theme).toBe('light');
    expect(root.dataset.themeChoice).toBe('system');
    expect(localStorage.getItem('theme')).toBeNull();
    expect(button).toHaveAccessibleName('테마: 시스템 설정 (누르면 라이트)');
    expect(screen.getByTestId('theme-announcement')).toHaveTextContent('기기 설정을 따라가요');
  });

  it('버튼은 type=button이다(폼 안에서도 제출하지 않음)', () => {
    render(<ThemeToggle />);
    expect(screen.getByTestId('theme-toggle')).toHaveAttribute('type', 'button');
  });

  it('다크 모드를 끈 빌드면 그리지 않는다', async () => {
    vi.resetModules();
    vi.doMock('../../../config', async (importOriginal) => ({
      ...(await importOriginal<typeof import('../../../config')>()),
      DARK_MODE_ENABLED: false,
    }));
    const { default: Disabled } = await import('../ThemeToggle');
    const { container } = render(<Disabled />);
    expect(container).toBeEmptyDOMElement();
  });
});
