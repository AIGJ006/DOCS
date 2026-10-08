import { describe, expect, it } from 'vitest';
import { formatDeadline, remainingDays } from '../formatDeadline';

describe('formatDeadline (015 R12)', () => {
  it('한국 시간 "2026년 11월 7일 오후 3:20"', () => {
    expect(formatDeadline('2026-11-07T06:20:00Z')).toBe('2026년 11월 7일 오후 3:20');
  });

  it('오전·자정·정오', () => {
    expect(formatDeadline('2026-11-07T00:05:00Z')).toBe('2026년 11월 7일 오전 9:05');
    expect(formatDeadline('2026-11-06T15:00:00Z')).toBe('2026년 11월 7일 오전 12:00');
    expect(formatDeadline('2026-11-07T03:00:00Z')).toBe('2026년 11월 7일 오후 12:00');
  });
});

describe('remainingDays', () => {
  const deadline = '2026-11-07T06:20:00Z';

  it('남은 날은 올림', () => {
    expect(remainingDays(deadline, new Date('2026-10-08T06:20:00Z'))).toBe(30);
    expect(remainingDays(deadline, new Date('2026-10-09T06:20:01Z'))).toBe(29);
    expect(remainingDays(deadline, new Date('2026-11-07T06:19:00Z'))).toBe(1);
  });

  it('기한이 지났거나 정각이면 0', () => {
    expect(remainingDays(deadline, new Date('2026-11-07T06:20:00Z'))).toBe(0);
    expect(remainingDays(deadline, new Date('2026-11-08T00:00:00Z'))).toBe(0);
  });
});
