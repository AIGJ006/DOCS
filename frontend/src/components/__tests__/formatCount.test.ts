import { describe, expect, it } from 'vitest';
import { formatCount } from '../formatCount';

/** 좋아요 수·조회수 형식 (009 T006, FR-016): 0~9,999는 쉼표, 1만 이상은 소수 첫째 자리까지 내림한 "만". */
describe('formatCount', () => {
  it.each([
    [0, '0'],
    [999, '999'],
    [1234, '1,234'],
    [9999, '9,999'],
    [10_000, '1만'],
    [12_999, '1.2만'],
    [19_999, '1.9만'],
    [1_000_000, '100만'],
  ])('%d → %s', (value, expected) => {
    expect(formatCount(value)).toBe(expected);
  });
});
