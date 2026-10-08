import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * 색 값 직접 쓰기 금지 (016 FR-011·FR-010·FR-015, contracts/theme.md §6).
 * `frontend/src` 아래 `.ts`·`.tsx`·`.css`(테스트·`styles/tokens.css` 제외)에서 색 값·색 전환 효과·사진 필터를 찾는다.
 * 새 색이 필요하면 `styles/tokens.css`에 토큰을 먼저 더한다. 꼭 필요한 줄은 같은 줄에 `raw-color-ok: 이유` 주석.
 */

const SRC = resolve(__dirname, '../..');

const NAMED = 'white|black|red|green|blue|gray|grey|silver|yellow|orange|purple|pink|brown|navy|teal';

const RULES: Array<{ pattern: RegExp; css: boolean; ts: boolean }> = [
  // 16진 색: CSS 값 위치(`:`·공백·`,`·`(` 뒤) 또는 따옴표 바로 뒤. 주소 조각(#add-section)은 뒤가 `-`·글자라 잡지 않음
  {
    pattern: /(?<=[:\s,(]|['"`])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![\w-])/g,
    css: true,
    ts: true,
  },
  // 함수 색
  {
    pattern: /(?<![\w-])(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\(/g,
    css: true,
    ts: true,
  },
  // 색 이름 값: CSS 값 위치 또는 따옴표 안 단독 값
  {
    pattern: new RegExp(`(?<=:\\s*|['"])(?:${NAMED})(?=\\s*[;'",}!]|\\s*$)`, 'g'),
    css: true,
    ts: true,
  },
  // 색 전환 효과 (FR-010)
  {
    pattern: /transition[^;]*\b(?:color|background|border-color|all)\b/g,
    css: true,
    ts: true,
  },
  // 사진 필터 (FR-015). backdrop-filter는 대상 아님
  { pattern: /(?<![\w-])(?:filter|mix-blend-mode)\s*:/g, css: true, ts: false },
  { pattern: /(?<![\w-])(?:filter|mixBlendMode)\s*:\s*['"`]/g, css: false, ts: true },
];

const EXCEPTION = /raw-color-ok/;
const EXCEPTION_WITH_REASON = /raw-color-ok:\s*\S/;

/** 주석·HTML 엔티티를 지운 줄. 주소 안의 `//`는 남긴다. */
function stripLine(line: string, state: { inBlock: boolean }, css: boolean): string {
  let out = '';
  let i = 0;
  while (i < line.length) {
    if (state.inBlock) {
      const end = line.indexOf('*/', i);
      if (end < 0) {
        return out;
      }
      state.inBlock = false;
      i = end + 2;
      continue;
    }
    if (line.startsWith('/*', i)) {
      state.inBlock = true;
      i += 2;
      continue;
    }
    if (!css && line.startsWith('//', i) && !/[:\w]$/.test(out)) {
      return out;
    }
    out += line[i];
    i += 1;
  }
  return out.replace(/&#\w+;/g, '');
}

export function findRawColors(source: string, kind: 'css' | 'ts'): Array<{ line: number; value: string }> {
  const css = kind === 'css';
  const state = { inBlock: false };
  const found: Array<{ line: number; value: string }> = [];
  source.split('\n').forEach((raw, index) => {
    const code = stripLine(raw, state, css);
    const hits: string[] = [];
    for (const rule of RULES) {
      if (css ? !rule.css : !rule.ts) {
        continue;
      }
      for (const match of code.matchAll(rule.pattern)) {
        hits.push(match[0]);
      }
    }
    if (hits.length === 0) {
      if (EXCEPTION.test(raw) && !EXCEPTION_WITH_REASON.test(raw)) {
        found.push({ line: index + 1, value: 'raw-color-ok 이유 없음' });
      }
      return;
    }
    if (EXCEPTION_WITH_REASON.test(raw)) {
      return;
    }
    for (const value of hits) {
      found.push({ line: index + 1, value });
    }
  });
  return found;
}

function listFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return name === '__tests__' ? [] : listFiles(path);
    }
    return [path];
  });
}

function scanTargets(): string[] {
  return listFiles(SRC)
    .map((path) => relative(SRC, path).split('\\').join('/'))
    .filter((path) => /\.(ts|tsx|css)$/.test(path))
    .filter((path) => !/\.test\.[tj]sx?$/.test(path))
    .filter((path) => !path.startsWith('test/'))
    .filter((path) => path !== 'styles/tokens.css');
}

describe('noRawColors', () => {
  it.each([
    ['css', 'color: #333;'],
    ['css', '  background: #FFF4E5;'],
    ['css', 'border: 1px solid #b00020;'],
    ['css', 'background: rgb(0 0 0 / 40%);'],
    ['css', 'box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);'],
    ['css', 'background: white;'],
    ['css', 'transition: background 0.2s;'],
    ['css', 'img { filter: brightness(0.8) }'],
    ['ts', "background: 'var(--card-bg, #fff)',"],
    ['ts', "style={{ color: 'black' }}"],
    ['ts', '<circle fill="#FFFFFF" />'],
    ['ts', "transition: 'all 0.2s',"],
    ['css', 'color: #111; /* raw-color-ok */'],
  ] as const)('잡는다 (%s) %s', (kind, line) => {
    expect(findRawColors(line, kind)).not.toEqual([]);
  });

  it.each([
    ['css', 'color: var(--color-text);'],
    ['css', 'background: transparent;'],
    ['css', 'color: currentColor;'],
    ['css', '/* color: #333; */'],
    ['css', 'backdrop-filter: blur(4px);'],
    ['css', 'color: #ffffff; /* raw-color-ok: 테스트 이유 */'],
    ['ts', "navigate('/#top'); const anchor = '#comment-12';"],
    ['ts', "const url = 'https://example.com/#add-section'; // #fff 주석"],
    ['ts', "const s = '&#123;'; items.filter((x) => x.ok);"],
    ['ts', "const label = 'white-space';"],
  ] as const)('잡지 않는다 (%s) %s', (kind, line) => {
    expect(findRawColors(line, kind)).toEqual([]);
  });

  it('여러 줄 주석 안은 보지 않는다', () => {
    expect(findRawColors('/*\n color: #333;\n*/\ncolor: var(--x);', 'css')).toEqual([]);
  });

  it('frontend/src의 화면 코드는 색 값을 직접 쓰지 않는다', () => {
    const files = scanTargets();
    expect(files.length).toBeGreaterThan(10);
    const failures = files.flatMap((path) =>
      findRawColors(readFileSync(join(SRC, path), 'utf8'), path.endsWith('.css') ? 'css' : 'ts').map(
        ({ line, value }) => `${path}:${line}: 색 값을 직접 쓰지 말고 토큰을 쓰세요 (${value})`,
      ),
    );
    expect(failures).toEqual([]);
  });
});
