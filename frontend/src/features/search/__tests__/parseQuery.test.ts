import { describe, expect, it } from 'vitest';
import { parsePeopleQuery, parseQuery, tagTarget } from '../parseQuery';

/** 서버 `SearchQueryParser`와 같은 규칙 (012 research R6·R10, contracts §4). */
describe('parseQuery', () => {
  it.each([
    ['트랜잭션 정리 a', ['트랜잭션', '정리'], true],
    ['롬복', ['롬복'], true],
    ['a b c', [], false],
    ['100% 할인', ['100%', '할인'], true],
    ['snake_case', ['snake_case'], false],
    [
      '하나둘 둘셋넷 셋넷다 넷다섯 다섯여섯 여섯일곱',
      ['하나둘', '둘셋넷', '셋넷다', '넷다섯', '다섯여섯'],
      false,
    ],
    ['', [], false],
    ['　트랜잭션 격리 ', ['트랜잭션', '격리'], true],
  ])('%s', (raw, words, twoChar) => {
    expect(parseQuery(raw)).toEqual({ words, hasTwoCharWord: twoChar });
  });

  it('50 코드 포인트까지 자른 뒤 나눈다', () => {
    const raw = '가'.repeat(49) + ' 나다';
    expect(parseQuery(raw).words).toEqual(['가'.repeat(49)]);
  });

  it('사람 검색어: 공백 제거, 맨 앞 @ 하나 제거, 2글자 미만 null', () => {
    expect(parsePeopleQuery(' @kim 755 ')).toBe('kim755');
    expect(parsePeopleQuery('@김')).toBeNull();
    expect(parsePeopleQuery('@@a')).toBe('@a');
    expect(parsePeopleQuery('김 민')).toBe('김민');
  });

  it('태그 이동 판정', () => {
    expect(tagTarget('#Spring')).toBe('spring');
    expect(tagTarget(' #jpa ')).toBe('jpa');
    expect(tagTarget('# spring')).toBeNull();
    expect(tagTarget('#')).toBeNull();
    expect(tagTarget('spring')).toBeNull();
  });
});
