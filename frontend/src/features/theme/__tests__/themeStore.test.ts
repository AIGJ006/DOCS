import { afterEach, describe, expect, it } from 'vitest';
import { blockStorage, restoreStorage } from '../../../test/storage';
import { nextChoice, readChoice, resolve, writeChoice } from '../themeStore';

/** 016 T024: 저장 값 읽기·쓰기, 순서, 적용 테마 (contracts/theme.md §1·§4). */
describe('themeStore', () => {
  afterEach(() => {
    restoreStorage();
    localStorage.clear();
  });

  it.each([
    [null, 'system'],
    ['light', 'light'],
    ['dark', 'dark'],
    ['system', 'system'],
    ['Dark', 'system'],
    ['', 'system'],
  ])('읽기: 저장 %s → %s', (saved, expected) => {
    if (saved !== null) localStorage.setItem('theme', saved);
    expect(readChoice()).toBe(expected);
  });

  it('읽기: 저장소 예외면 system', () => {
    blockStorage();
    expect(readChoice()).toBe('system');
  });

  it('쓰기: light·dark는 저장, system은 키를 지운다', () => {
    writeChoice('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
    writeChoice('light');
    expect(localStorage.getItem('theme')).toBe('light');
    writeChoice('system');
    expect(localStorage.getItem('theme')).toBeNull();
  });

  it('쓰기: 저장소 예외는 삼킨다', () => {
    blockStorage();
    expect(() => writeChoice('dark')).not.toThrow();
  });

  it('순서: 시스템 → 라이트 → 다크 → 시스템 (US2 #1)', () => {
    expect(nextChoice('system')).toBe('light');
    expect(nextChoice('light')).toBe('dark');
    expect(nextChoice('dark')).toBe('system');
  });

  it.each([
    ['system', true, 'dark'],
    ['system', false, 'light'],
    ['light', true, 'light'],
    ['light', false, 'light'],
    ['dark', true, 'dark'],
    ['dark', false, 'dark'],
  ] as const)('적용 테마: %s · 기기 다크 %s → %s', (choice, dark, expected) => {
    expect(resolve(choice, dark)).toBe(expected);
  });
});
