import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { installMatchMedia, uninstallMatchMedia } from '../../../test/matchMedia';
import { blockStorage, restoreStorage } from '../../../test/storage';

/** 016 T018: `public/js/theme-init.js`의 첫 화면 판정 (contracts/theme.md §3). */
const PATH = resolve(__dirname, '../../../../public/js/theme-init.js');
const SOURCE = readFileSync(PATH, 'utf8');

function runInit() {
  new Function(SOURCE)();
  const root = document.documentElement;
  return { theme: root.dataset.theme, choice: root.dataset.themeChoice };
}

describe('theme-init.js', () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset.theme;
    delete document.documentElement.dataset.themeChoice;
  });

  afterEach(() => {
    restoreStorage();
    uninstallMatchMedia();
    localStorage.clear();
  });

  it.each([
    ['dark', true, 'dark', 'dark'],
    ['dark', false, 'dark', 'dark'],
    ['light', true, 'light', 'light'],
    ['light', false, 'light', 'light'],
    [null, true, 'dark', 'system'],
    [null, false, 'light', 'system'],
    ['Dark', true, 'dark', 'system'],
    ['"dark"', false, 'light', 'system'],
    ['system', true, 'dark', 'system'],
  ])('저장 %s · 기기 다크 %s → data-theme %s, choice %s', (saved, prefersDark, theme, choice) => {
    installMatchMedia(prefersDark);
    if (saved !== null) localStorage.setItem('theme', saved);
    expect(runInit()).toEqual({ theme, choice });
  });

  it('저장소 접근이 막혀도 오류 없이 기기 설정을 따른다 (US1 #4)', () => {
    installMatchMedia(true);
    blockStorage();
    expect(() => runInit()).not.toThrow();
    expect(runInit()).toEqual({ theme: 'dark', choice: 'system' });
  });

  it('저장소도 matchMedia도 없으면 라이트', () => {
    uninstallMatchMedia();
    blockStorage();
    expect(runInit()).toEqual({ theme: 'light', choice: 'system' });
  });

  it('두 속성만 바꾸고 전역 변수를 만들지 않는다', () => {
    installMatchMedia(false);
    const before = new Set(Object.keys(window));
    runInit();
    expect(Object.keys(window).filter((key) => !before.has(key))).toEqual([]);
    expect(
      document.documentElement.getAttributeNames().filter((name) => name.startsWith('data-')),
    ).toEqual(['data-theme', 'data-theme-choice']);
  });

  it('파일이 1KB 미만이고 모듈 문법을 쓰지 않는다', () => {
    expect(Buffer.byteLength(SOURCE)).toBeLessThan(1024);
    expect(SOURCE).not.toMatch(/\b(import|export)\b/);
  });
});
