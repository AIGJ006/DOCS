import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  installMatchMedia,
  listenerCount,
  setPrefersDark,
  uninstallMatchMedia,
} from '../../../test/matchMedia';
import { useTheme } from '../useTheme';

/** 016 T025: 처음 값, 즉시 적용, 시스템일 때만 기기 설정 구독 (US2 #3·#4). */
const root = document.documentElement;

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear();
    delete root.dataset.theme;
    delete root.dataset.themeChoice;
    installMatchMedia(false);
  });

  afterEach(() => {
    uninstallMatchMedia();
    localStorage.clear();
  });

  it('처음 값은 theme-init이 정한 data-theme-choice', () => {
    root.dataset.theme = 'dark';
    root.dataset.themeChoice = 'dark';
    const { result } = renderHook(() => useTheme());
    expect(result.current.choice).toBe('dark');
    expect(root.dataset.theme).toBe('dark');
  });

  it('속성이 없으면 저장 값으로 정하고 속성을 채운다', () => {
    localStorage.setItem('theme', 'light');
    const { result } = renderHook(() => useTheme());
    expect(result.current.choice).toBe('light');
    expect(root.dataset.theme).toBe('light');
    expect(root.dataset.themeChoice).toBe('light');
  });

  it('setChoice는 같은 동기 호출 안에서 두 속성과 저장 값을 바꾼다', () => {
    root.dataset.theme = 'light';
    root.dataset.themeChoice = 'system';
    const { result } = renderHook(() => useTheme());
    act(() => {
      result.current.setChoice('dark');
      expect(root.dataset.theme).toBe('dark');
      expect(root.dataset.themeChoice).toBe('dark');
      expect(localStorage.getItem('theme')).toBe('dark');
    });
  });

  it('시스템 상태면 기기 설정 변경을 바로 따라간다 (US2 #3)', () => {
    root.dataset.theme = 'light';
    root.dataset.themeChoice = 'system';
    renderHook(() => useTheme());
    act(() => setPrefersDark(true));
    expect(root.dataset.theme).toBe('dark');
    act(() => setPrefersDark(false));
    expect(root.dataset.theme).toBe('light');
  });

  it('라이트로 고정하면 기기 설정을 바꿔도 그대로 (US2 #4)', () => {
    root.dataset.theme = 'light';
    root.dataset.themeChoice = 'light';
    renderHook(() => useTheme());
    expect(listenerCount()).toBe(0);
    act(() => setPrefersDark(true));
    expect(root.dataset.theme).toBe('light');
  });

  it('고정 ↔ 시스템을 오가도 구독이 새지 않는다', () => {
    root.dataset.theme = 'light';
    root.dataset.themeChoice = 'system';
    const { result, unmount } = renderHook(() => useTheme());
    expect(listenerCount()).toBe(1);
    act(() => result.current.setChoice('light'));
    expect(listenerCount()).toBe(0);
    act(() => result.current.setChoice('dark'));
    act(() => result.current.setChoice('system'));
    expect(listenerCount()).toBe(1);
    unmount();
    expect(listenerCount()).toBe(0);
  });

  it('다크 → 시스템이면 기기 설정으로 바로 돌아간다', () => {
    root.dataset.theme = 'dark';
    root.dataset.themeChoice = 'dark';
    localStorage.setItem('theme', 'dark');
    const { result } = renderHook(() => useTheme());
    act(() => result.current.setChoice('system'));
    expect(root.dataset.theme).toBe('light');
    expect(localStorage.getItem('theme')).toBeNull();
  });
});
