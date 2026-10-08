/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { normalizeTag } from '../normalizeTag';

// 서버 TagNormalizerTest와 같은 예시 표 (008 research R4, contracts/normalization.md §2). vitest는 frontend/에서 돈다.
const casesCsv = readFileSync(
  resolve(process.cwd(), '../backend/src/test/resources/tag/normalization-cases.csv'),
  'utf-8',
);

type Row = { input: string; expected: string; code: string; note: string };

/** 따옴표로 감싼 칸만 쓰는 이 CSV 전용 파서 ("" = 따옴표 하나). */
function parseCsv(text: string): Row[] {
  const lines = text.split(/\r?\n/).filter((line) => line.length > 0);
  const rows: Row[] = [];
  for (const line of lines.slice(1)) {
    const cells: string[] = [];
    let i = 0;
    while (i < line.length) {
      if (line[i] === '"') {
        let value = '';
        i += 1;
        while (i < line.length) {
          if (line[i] === '"' && line[i + 1] === '"') {
            value += '"';
            i += 2;
          } else if (line[i] === '"') {
            i += 1;
            break;
          } else {
            value += line[i];
            i += 1;
          }
        }
        cells.push(value);
        if (line[i] === ',') i += 1;
      } else {
        const end = line.indexOf(',', i);
        cells.push(end < 0 ? line.slice(i) : line.slice(i, end));
        i = end < 0 ? line.length : end + 1;
      }
    }
    rows.push({ input: cells[0], expected: cells[1], code: cells[2], note: cells[3] ?? '' });
  }
  return rows;
}

const BACKSLASH = String.fromCharCode(92);
const ESCAPE = new RegExp(BACKSLASH + BACKSLASH + 'u([0-9A-Fa-f]{4})', 'g');

function unescape(raw: string): string {
  return raw.replace(ESCAPE, (_, hex: string) => String.fromCodePoint(parseInt(hex, 16)));
}

const rows = parseCsv(casesCsv);

describe('normalizeTag — 서버와 같은 예시 표', () => {
  it('표를 읽었다', () => {
    expect(rows.length).toBeGreaterThan(20);
  });

  // 금칙어는 화면이 모른다 — 서버 400이 그 칩에 표시한다
  const cases = rows.filter((row) => row.code !== 'TAG_BANNED_WORD');
  it.each(cases.map((row) => [row.note || row.input, row] as const))('%s', (_, row) => {
    const result = normalizeTag(unescape(row.input));
    if (row.code === '') {
      expect(result).toEqual({ ok: true, name: row.expected });
    } else {
      expect(result).toEqual({ ok: false, code: row.code });
    }
  });
});

describe('normalizeTag — 경계', () => {
  it('허용 밖 문자가 길이보다 먼저다', () => {
    expect(normalizeTag(String.fromCodePoint(0x1f525).repeat(31))).toEqual({
      ok: false,
      code: 'INVALID_TAG',
    });
  });

  it('제어 문자는 공백보다 먼저 지워진다', () => {
    expect(normalizeTag('a\tb')).toEqual({ ok: true, name: 'ab' });
  });

  it('빈 값은 INVALID_TAG', () => {
    expect(normalizeTag('')).toEqual({ ok: false, code: 'INVALID_TAG' });
    expect(normalizeTag('   ')).toEqual({ ok: false, code: 'INVALID_TAG' });
  });

  it('보이지 않는 글자를 모두 지운다', () => {
    for (const cp of [0x200b, 0x200f, 0x2060, 0x2069, 0xfeff, 0x202a, 0x202e, 0x7f, 0x9f]) {
      expect(normalizeTag('a' + String.fromCodePoint(cp) + 'b')).toEqual({ ok: true, name: 'ab' });
    }
  });
});
