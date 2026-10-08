import { describe, expect, it } from 'vitest';
import { buildDiff, foldUnchanged, nextChange, previousChange, sideBySideRows } from '../diffModel';

/** 002 T097 (FR-023): 줄 단위 비교 + 바뀐 줄 안 단어 강조, 제목 비교, 긴 같은 구간 접기, 이전·다음 차이 이동. */
describe('diffModel', () => {
  const server = { title: '서버 제목', contentMd: '첫 줄\n둘째 줄은 짧다\n셋째 줄' };
  const mine = { title: '서버 제목', contentMd: '첫 줄\n둘째 줄은 길다\n셋째 줄\n넷째 줄' };

  it('줄 단위로 비교하고 바뀐 줄 쌍 안에서 단어를 강조한다', () => {
    const diff = buildDiff(server, mine);

    expect(diff.lines.map((l) => [l.kind, l.text])).toEqual([
      ['equal', '첫 줄'],
      ['removed', '둘째 줄은 짧다'],
      ['added', '둘째 줄은 길다'],
      ['equal', '셋째 줄'],
      ['added', '넷째 줄'],
    ]);
    const removed = diff.lines[1];
    const added = diff.lines[2];
    expect(removed.segments.filter((s) => s.changed).map((s) => s.text)).toEqual(['짧다']);
    expect(added.segments.filter((s) => s.changed).map((s) => s.text)).toEqual(['길다']);
    expect(removed.segments.map((s) => s.text).join('')).toBe('둘째 줄은 짧다');
    // 짝이 없는 추가 줄은 통째로 바뀐 것
    expect(diff.lines[4].segments).toEqual([{ text: '넷째 줄', changed: true }]);
    // 줄 번호: 지운 줄은 서버 쪽, 더한 줄은 편집 쪽
    expect(removed.oldNumber).toBe(2);
    expect(removed.newNumber).toBeNull();
    expect(added.newNumber).toBe(2);
  });

  it('끝 줄바꿈 유무만 다른 것은 차이로 보지 않는다', () => {
    const diff = buildDiff({ title: 't', contentMd: 'a\nb' }, { title: 't', contentMd: 'a\nb\n' });
    expect(diff.lines.every((l) => l.kind === 'equal')).toBe(true);
    expect(diff.changeCount).toBe(0);
  });

  it('제목이 같으면 제목 비교가 없고, 다르면 단어 단위로 비교한다', () => {
    expect(buildDiff(server, mine).title).toBeNull();

    const diff = buildDiff(server, { ...mine, title: '새 제목' });
    expect(diff.title).not.toBeNull();
    expect(diff.title?.old.filter((s) => s.changed).map((s) => s.text)).toEqual(['서버']);
    expect(diff.title?.new.filter((s) => s.changed).map((s) => s.text)).toEqual(['새']);
    expect(diff.changeCount).toBe(3);
    expect(diff.title?.changeIndex).toBe(0);
  });

  it('바뀐 덩어리마다 차이 번호를 매긴다', () => {
    const diff = buildDiff(server, mine);
    expect(diff.changeCount).toBe(2);
    expect(diff.lines.map((l) => l.changeIndex)).toEqual([null, 0, 0, null, 1]);
  });

  it('바뀌지 않은 긴 구간(6줄 이상)은 앞뒤 문맥만 남기고 접는다', () => {
    const same = Array.from({ length: 10 }, (_, i) => `같은 줄 ${i + 1}`);
    const diff = buildDiff(
      { title: 't', contentMd: ['처음', ...same, '끝'].join('\n') },
      { title: 't', contentMd: ['처음 바뀜', ...same, '끝 바뀜'].join('\n') },
    );

    const items = foldUnchanged(diff.lines, { context: 2, minFold: 6 });
    const folds = items.filter((i) => i.type === 'fold');
    expect(folds).toHaveLength(1);
    expect(folds[0].type === 'fold' && folds[0].lines.map((l) => l.text)).toEqual([
      '같은 줄 3',
      '같은 줄 4',
      '같은 줄 5',
      '같은 줄 6',
      '같은 줄 7',
      '같은 줄 8',
    ]);
    // 접은 구간 앞뒤로 2줄씩 남는다
    const shown = items
      .filter((i) => i.type === 'line')
      .map((i) => i.type === 'line' && i.line.text);
    expect(shown).toContain('같은 줄 1');
    expect(shown).toContain('같은 줄 2');
    expect(shown).toContain('같은 줄 9');
    expect(shown).toContain('같은 줄 10');
  });

  it('짧은 같은 구간은 접지 않는다', () => {
    const diff = buildDiff(server, mine);
    expect(
      foldUnchanged(diff.lines, { context: 2, minFold: 6 }).every((i) => i.type === 'line'),
    ).toBe(true);
  });

  it('[이전 차이]·[다음 차이]는 처음과 끝에서 멈춘다', () => {
    expect(nextChange(null, 3)).toBe(0);
    expect(nextChange(0, 3)).toBe(1);
    expect(nextChange(2, 3)).toBe(2);
    expect(previousChange(2, 3)).toBe(1);
    expect(previousChange(0, 3)).toBe(0);
    expect(previousChange(null, 3)).toBe(2);
    expect(nextChange(null, 0)).toBeNull();
  });

  it('좌우 보기는 지운 줄과 더한 줄을 한 줄에 나란히 놓는다', () => {
    const rows = sideBySideRows(buildDiff(server, mine).lines);
    expect(rows.map((r) => [r.left?.text ?? null, r.right?.text ?? null])).toEqual([
      ['첫 줄', '첫 줄'],
      ['둘째 줄은 짧다', '둘째 줄은 길다'],
      ['셋째 줄', '셋째 줄'],
      [null, '넷째 줄'],
    ]);
  });
});
