import { describe, expect, it } from 'vitest';
import { tagPath, tagQueryValue } from '../tagPath';

// contracts/normalization.md §3 주소 인코딩 표
const TABLE: Array<[string, string, string]> = [
  ['spring-boot', 'spring-boot', 'spring-boot'],
  ['c#', 'c%23', 'c%23'],
  ['c++', 'c++', 'c%2B%2B'],
  ['node.js', 'node.js', 'node.js'],
  ['.net', '.net', '.net'],
  [
    '스프링-부트',
    '%EC%8A%A4%ED%94%84%EB%A7%81-%EB%B6%80%ED%8A%B8',
    '%EC%8A%A4%ED%94%84%EB%A7%81-%EB%B6%80%ED%8A%B8',
  ],
  ['자바_기초', '%EC%9E%90%EB%B0%94_%EA%B8%B0%EC%B4%88', '%EC%9E%90%EB%B0%94_%EA%B8%B0%EC%B4%88'],
];

describe('tagPath', () => {
  it.each(TABLE)('%s → 경로 /tags/%s', (name, path) => {
    expect(tagPath(name)).toBe('/tags/' + path);
  });

  it.each(TABLE)('%s → 쿼리 값 %s', (name, _path, query) => {
    expect(tagQueryValue(name)).toBe(query);
  });

  it('경로는 decodeURIComponent로 원래 이름이 된다', () => {
    for (const [name] of TABLE) {
      expect(decodeURIComponent(tagPath(name).slice('/tags/'.length))).toBe(name);
    }
  });
});
