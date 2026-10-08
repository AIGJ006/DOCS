import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * 색 토큰 대비 검사 (016 FR-012, contracts/theme.md §7, WCAG 2.2 AA 상대 휘도식).
 * 라이트 블록은 모든 서비스(공통 묶음), 다크 블록은 있을 때만(선택 묶음). 값을 바꾸면 이 테스트가 기준을 지키는지 막는다.
 */

const TOKENS_CSS = readFileSync(resolve(__dirname, '../tokens.css'), 'utf8');

export const COMMON_TOKENS = [
  '--color-bg',
  '--color-surface',
  '--color-text',
  '--color-text-muted',
  '--color-border',
  '--color-brand',
  '--color-brand-fill',
  '--color-danger',
  '--thumb-empty',
  '--color-code-bg',
  '--color-focus',
] as const;

const HLJS_TEXT = [
  '--hljs-text',
  '--hljs-keyword',
  '--hljs-title',
  '--hljs-attr',
  '--hljs-string',
  '--hljs-built-in',
  '--hljs-comment',
  '--hljs-name',
  '--hljs-section',
  '--hljs-bullet',
];

const AVATARS = Array.from({ length: 8 }, (_, i) => `--avatar-${i + 1}`);

/** [앞, 뒤 목록, 기준] — contracts/theme.md §7 */
const PAIRS: Array<[string, string[], number]> = [
  [
    '--color-text',
    [
      '--color-bg',
      '--color-surface',
      '--color-code-bg',
      '--thumb-empty',
      '--color-notice-bg',
      '--color-warning-bg',
      '--diff-del-bg',
      '--diff-add-bg',
      '--diff-del-strong',
      '--diff-add-strong',
      '--color-danger-bg',
    ],
    4.5,
  ],
  ['--color-text-muted', ['--color-bg', '--color-surface'], 4.5],
  ['--color-brand', ['--color-bg', '--color-surface'], 4.5],
  ['--color-on-fill', ['--color-brand-fill', ...AVATARS], 4.5],
  ['--color-danger', ['--color-bg', '--color-surface', '--color-danger-bg'], 4.5],
  ['--color-success', ['--color-bg', '--color-surface'], 4.5],
  ['--color-toast-text', ['--color-toast-bg'], 4.5],
  ['--color-toast-link', ['--color-toast-bg'], 4.5],
  ...HLJS_TEXT.map((name): [string, string[], number] => [name, ['--color-code-bg'], 4.5]),
  ['--hljs-addition', ['--hljs-addition-bg'], 4.5],
  ['--hljs-deletion', ['--hljs-deletion-bg'], 4.5],
  ['--color-focus', ['--color-bg', '--color-surface'], 3],
  // 입력칸·버튼 윤곽 테두리는 --color-text-muted (research R2)
  ['--color-text-muted', ['--color-bg', '--color-surface'], 3],
  ['--color-warning-border', ['--color-warning-bg'], 3],
];

export function parseBlock(css: string, selector: RegExp): Record<string, string> | null {
  const match = selector.exec(css);
  if (!match) {
    return null;
  }
  const body = css.slice(match.index + match[0].length, css.indexOf('}', match.index));
  const tokens: Record<string, string> = {};
  for (const [, name, value] of body
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/g)) {
    tokens[name] = value.trim();
  }
  return tokens;
}

export function luminance(hex: string): number {
  const h = hex.replace('#', '');
  const full = h.length === 3 ? [...h].map((c) => c + c).join('') : h;
  const [r, g, b] = [0, 2, 4]
    .map((i) => parseInt(full.slice(i, i + 2), 16) / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const isHex = (v: string | undefined): v is string =>
  !!v && /^#[0-9a-f]{3}([0-9a-f]{3})?$/i.test(v);

const LIGHT = parseBlock(TOKENS_CSS, /:root,\s*\[data-theme='light'\]\s*\{/);
const DARK = parseBlock(TOKENS_CSS, /\[data-theme='dark'\]\s*\{/);

function checkPairs(tokens: Record<string, string>) {
  const failures: string[] = [];
  for (const [fg, bgs, min] of PAIRS) {
    for (const bg of bgs) {
      const [f, b] = [tokens[fg], tokens[bg]];
      if (f === undefined || b === undefined) {
        failures.push(`${fg} / ${bg}: 정의 없음`);
        continue;
      }
      if (!isHex(f) || !isHex(b)) {
        continue; // 반투명 값(--color-overlay 등)은 짝에서 뺀다
      }
      const ratio = contrast(f, b);
      if (ratio < min) {
        failures.push(`${fg}(${f}) / ${bg}(${b}) = ${ratio.toFixed(2)} < ${min}`);
      }
    }
  }
  return failures;
}

describe('tokenContrast', () => {
  it('WCAG 식이 알려진 값과 같다', () => {
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(contrast('#212529', '#ffffff')).toBeCloseTo(15.43, 1);
    expect(contrast('#fff', '#ffffff')).toBe(1);
  });

  it('라이트 블록에 공통 토큰 11개가 모두 있다', () => {
    expect(LIGHT).not.toBeNull();
    for (const name of COMMON_TOKENS) {
      expect(LIGHT, name).toHaveProperty([name]);
    }
  });

  it('라이트 짝이 모두 기준 이상이다', () => {
    expect(checkPairs(LIGHT ?? {})).toEqual([]);
  });

  describe.runIf(DARK !== null)('다크 블록 (선택 묶음)', () => {
    it('공통 토큰 11개를 모두 다시 정의한다(빠지면 라이트 값이 새어 나옴)', () => {
      for (const name of COMMON_TOKENS) {
        expect(DARK, name).toHaveProperty([name]);
      }
    });

    it('라이트에 있는 토큰은 다크에도 모두 있다', () => {
      expect(Object.keys(LIGHT ?? {}).filter((name) => !(name in (DARK ?? {})))).toEqual([]);
    });

    it('다크 짝이 모두 기준 이상이다', () => {
      expect(checkPairs(DARK ?? {})).toEqual([]);
    });

    it('썸네일 빈 영역은 카드 면과 다른 색이다 (US3 #5)', () => {
      expect(DARK?.['--thumb-empty']).not.toBe(DARK?.['--color-surface']);
      expect(LIGHT?.['--thumb-empty']).not.toBe(LIGHT?.['--color-surface']);
    });
  });
});
