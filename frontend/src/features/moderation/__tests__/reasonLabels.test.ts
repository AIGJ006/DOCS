import { describe, expect, it } from 'vitest';
import { REASON_LABELS, reasonLabel } from '../reasonLabels';

describe('reasonLabels', () => {
  it('사유 6개의 화면 이름', () => {
    expect(Object.keys(REASON_LABELS)).toEqual([
      'SPAM',
      'ABUSE',
      'SEXUAL',
      'PRIVACY',
      'COPYRIGHT',
      'OTHER',
    ]);
    expect(reasonLabel('SPAM')).toBe('스팸·광고');
    expect(reasonLabel('COPYRIGHT')).toBe('저작권 침해');
  });

  it('모르는 코드와 빈 값은 기타', () => {
    expect(reasonLabel('UNKNOWN')).toBe('기타');
    expect(reasonLabel(null)).toBe('기타');
    expect(reasonLabel(undefined)).toBe('기타');
  });
});
