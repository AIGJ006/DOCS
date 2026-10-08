/**
 * 충돌 비교 모델 (002 T101, FR-023, research A-3). jsdiff로 줄 단위로 비교하고, 바뀐 줄 쌍 안에서는 단어 단위로 다시 비교해 바뀐
 * 부분만 강조한다. 제목이 다르면 제목 비교를 앞에 둔다. 바뀌지 않은 긴 구간은 접고, 바뀐 덩어리마다 번호를 매겨 [이전 차이]·[다음 차이]로
 * 옮겨 다닌다.
 *
 * 방향: `old` = 서버에 저장된 내용, `new` = 지금 편집 중인 내용. "편집 중인 내용으로 저장"하면 지운 줄(−)이 사라지고 더한 줄(+)이 남는다.
 */
import { diffArrays, diffLines } from 'diff';

export interface Segment {
  text: string;
  /** 단어 단위로 바뀐 부분인가. */
  changed: boolean;
}

export type LineKind = 'equal' | 'removed' | 'added';

export interface DiffLine {
  kind: LineKind;
  text: string;
  /** 서버 쪽 줄 번호 (더한 줄이면 null). */
  oldNumber: number | null;
  /** 편집 쪽 줄 번호 (지운 줄이면 null). */
  newNumber: number | null;
  segments: Segment[];
  /** 바뀐 덩어리 번호 (같은 줄이면 null). 제목이 다르면 제목이 0번이다. */
  changeIndex: number | null;
}

export interface TitleDiff {
  old: Segment[];
  new: Segment[];
  changeIndex: number;
}

export interface DiffModel {
  title: TitleDiff | null;
  lines: DiffLine[];
  /** 바뀐 덩어리 수 (제목 포함). */
  changeCount: number;
}

export interface Comparable {
  title: string;
  contentMd: string;
}

function splitLines(value: string): string[] {
  const lines = value.split('\n');
  if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop();
  }
  return lines;
}

/** 끝 줄바꿈 유무만 다른 것은 차이로 보지 않도록 맞춘다. */
function normalize(text: string): string {
  const unified = text.replace(/\r\n?/g, '\n');
  return unified === '' || unified.endsWith('\n') ? unified : `${unified}\n`;
}

/** 공백 경계로 나눈 낱말·공백 조각. 한글 낱말도 글자 단위가 아니라 낱말 단위로 비교한다. */
function tokens(text: string): string[] {
  return text.split(/(\s+)/).filter((token) => token !== '');
}

/**
 * 두 글자열을 단어 단위로 비교해 각 쪽의 조각을 돌려준다. jsdiff `diffWordsWithSpace`는 한글 낱말을 글자로 쪼개("짧다"→"짧"+"다")
 * 공백으로 나눈 조각을 `diffArrays`로 비교한다.
 */
export function wordSegments(oldText: string, newText: string): { old: Segment[]; new: Segment[] } {
  const oldSegments: Segment[] = [];
  const newSegments: Segment[] = [];
  for (const part of diffArrays(tokens(oldText), tokens(newText))) {
    const value = part.value.join('');
    if (part.removed) {
      oldSegments.push({ text: value, changed: true });
    } else if (part.added) {
      newSegments.push({ text: value, changed: true });
    } else {
      oldSegments.push({ text: value, changed: false });
      newSegments.push({ text: value, changed: false });
    }
  }
  return { old: merge(oldSegments), new: merge(newSegments) };
}

function merge(segments: Segment[]): Segment[] {
  const merged: Segment[] = [];
  for (const segment of segments) {
    const last = merged[merged.length - 1];
    if (last && last.changed === segment.changed) {
      last.text += segment.text;
    } else if (segment.text !== '') {
      merged.push({ ...segment });
    }
  }
  return merged;
}

export function buildDiff(server: Comparable, mine: Comparable): DiffModel {
  let changeCount = 0;
  let title: TitleDiff | null = null;
  if (server.title !== mine.title) {
    const segments = wordSegments(server.title, mine.title);
    title = { old: segments.old, new: segments.new, changeIndex: changeCount++ };
  }

  const lines: DiffLine[] = [];
  let oldNumber = 1;
  let newNumber = 1;
  const parts = diffLines(normalize(server.contentMd), normalize(mine.contentMd));
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    if (!part.added && !part.removed) {
      for (const text of splitLines(part.value)) {
        lines.push({
          kind: 'equal',
          text,
          oldNumber: oldNumber++,
          newNumber: newNumber++,
          segments: [{ text, changed: false }],
          changeIndex: null,
        });
      }
      continue;
    }
    // 바뀐 덩어리 하나 = 지운 줄들 + 바로 뒤의 더한 줄들
    const removed = part.removed ? splitLines(part.value) : [];
    let added: string[] = [];
    if (part.added) {
      added = splitLines(part.value);
    } else if (i + 1 < parts.length && parts[i + 1].added) {
      added = splitLines(parts[i + 1].value);
      i++;
    }
    const index = changeCount++;
    const pairs = Math.min(removed.length, added.length);
    const removedSegments: Segment[][] = [];
    const addedSegments: Segment[][] = [];
    for (let k = 0; k < pairs; k++) {
      const segments = wordSegments(removed[k], added[k]);
      removedSegments.push(segments.old);
      addedSegments.push(segments.new);
    }
    removed.forEach((text, k) =>
      lines.push({
        kind: 'removed',
        text,
        oldNumber: oldNumber++,
        newNumber: null,
        segments: removedSegments[k] ?? [{ text, changed: true }],
        changeIndex: index,
      }),
    );
    added.forEach((text, k) =>
      lines.push({
        kind: 'added',
        text,
        oldNumber: null,
        newNumber: newNumber++,
        segments: addedSegments[k] ?? [{ text, changed: true }],
        changeIndex: index,
      }),
    );
  }
  return { title, lines, changeCount };
}

export type FoldItem =
  { type: 'line'; line: DiffLine } | { type: 'fold'; id: string; lines: DiffLine[] };

export interface FoldOptions {
  /** 바뀐 줄 앞뒤로 남길 같은 줄 수. */
  context: number;
  /** 같은 줄이 이만큼 이어지면 접는다. */
  minFold: number;
}

export const DEFAULT_FOLD: FoldOptions = { context: 2, minFold: 6 };

/** 바뀌지 않은 긴 구간을 앞뒤 문맥만 남기고 접는다. */
export function foldUnchanged(lines: DiffLine[], options: FoldOptions = DEFAULT_FOLD): FoldItem[] {
  const items: FoldItem[] = [];
  let i = 0;
  while (i < lines.length) {
    if (lines[i].kind !== 'equal') {
      items.push({ type: 'line', line: lines[i] });
      i++;
      continue;
    }
    let end = i;
    while (end < lines.length && lines[end].kind === 'equal') {
      end++;
    }
    const run = lines.slice(i, end);
    const keepBefore = i > 0 ? options.context : 0;
    const keepAfter = end < lines.length ? options.context : 0;
    const hidden = run.length - keepBefore - keepAfter;
    if (run.length >= options.minFold && hidden >= 2) {
      run.slice(0, keepBefore).forEach((line) => items.push({ type: 'line', line }));
      const folded = run.slice(keepBefore, run.length - keepAfter);
      items.push({ type: 'fold', id: `fold-${i + keepBefore}`, lines: folded });
      run.slice(run.length - keepAfter).forEach((line) => items.push({ type: 'line', line }));
    } else {
      run.forEach((line) => items.push({ type: 'line', line }));
    }
    i = end;
  }
  return items;
}

export interface SideRow {
  left: DiffLine | null;
  right: DiffLine | null;
}

/** 좌우 보기: 같은 줄은 양쪽에, 바뀐 덩어리는 지운 줄과 더한 줄을 차례로 짝지어 한 줄에 놓는다. */
export function sideBySideRows(lines: DiffLine[]): SideRow[] {
  const rows: SideRow[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.kind === 'equal') {
      rows.push({ left: line, right: line });
      i++;
      continue;
    }
    const removed: DiffLine[] = [];
    const added: DiffLine[] = [];
    const index = line.changeIndex;
    while (i < lines.length && lines[i].kind !== 'equal' && lines[i].changeIndex === index) {
      (lines[i].kind === 'removed' ? removed : added).push(lines[i]);
      i++;
    }
    for (let k = 0; k < Math.max(removed.length, added.length); k++) {
      rows.push({ left: removed[k] ?? null, right: added[k] ?? null });
    }
  }
  return rows;
}

/** [다음 차이]: 아직 고르지 않았으면 첫 차이, 끝에서는 멈춘다. 차이가 없으면 null. */
export function nextChange(current: number | null, count: number): number | null {
  if (count === 0) {
    return null;
  }
  return current === null ? 0 : Math.min(current + 1, count - 1);
}

/** [이전 차이]: 아직 고르지 않았으면 마지막 차이, 처음에서는 멈춘다. 차이가 없으면 null. */
export function previousChange(current: number | null, count: number): number | null {
  if (count === 0) {
    return null;
  }
  return current === null ? count - 1 : Math.max(current - 1, 0);
}
