import { describe, expect, it } from 'vitest';
import {
  daysUntilPurge,
  formatPublished,
  formatSavedAt,
  originalStatusLabel,
  purgeText,
} from './format';

/** 관리 목록 표시 계산 (006 T037, FR-007·008·011, research R23). 날짜는 한국 시간 기준. */
describe('format', () => {
  it('daysUntilPurge는 한국 날짜 차이 — 10월 2일 삭제(11월 1일 완전 삭제)를 10월 5일에 보면 27', () => {
    const purgeAt = '2026-11-01T03:00:00Z'; // 11월 1일 12:00 KST
    expect(daysUntilPurge(purgeAt, new Date('2026-10-05T14:00:00Z'))).toBe(27); // 10월 5일 23:00 KST
    expect(daysUntilPurge(purgeAt, new Date('2026-10-05T15:30:00Z'))).toBe(26); // 10월 6일 00:30 KST
  });

  it('남은 날이 0 이하면 "곧 완전 삭제"', () => {
    const purgeAt = '2026-11-01T03:00:00Z';
    expect(purgeText(purgeAt, new Date('2026-10-05T14:00:00Z'))).toBe('27일 뒤 완전 삭제');
    expect(purgeText(purgeAt, new Date('2026-11-01T01:00:00Z'))).toBe('곧 완전 삭제');
    expect(purgeText(purgeAt, new Date('2026-11-03T01:00:00Z'))).toBe('곧 완전 삭제');
  });

  it('formatSavedAt은 24시간 이내 "N분 전"/"N시간 전", 그 밖 "10월 3일 14:03"', () => {
    const now = new Date('2026-10-05T05:03:00Z');
    expect(formatSavedAt('2026-10-05T05:02:30Z', now)).toBe('방금 전');
    expect(formatSavedAt('2026-10-05T04:50:00Z', now)).toBe('13분 전');
    expect(formatSavedAt('2026-10-04T08:03:00Z', now)).toBe('21시간 전');
    expect(formatSavedAt('2026-10-03T05:03:00Z', now)).toBe('10월 3일 14:03');
  });

  it('formatPublished는 "발행 2026.10.01"과 다시 발행했으면 "· 수정됨 10월 3일"', () => {
    expect(formatPublished('2026-10-01T01:00:00Z', null)).toBe('발행 2026.10.01');
    expect(formatPublished('2026-10-01T01:00:00Z', '2026-10-03T05:03:00Z')).toBe(
      '발행 2026.10.01 · 수정됨 10월 3일',
    );
    expect(formatPublished(null, null)).toBe('');
  });

  it('휴지통 원래 상태 표시', () => {
    expect(originalStatusLabel('DRAFT')).toBe('(임시글이었음)');
    expect(originalStatusLabel('PUBLISHED')).toBe('(발행 글이었음)');
  });
});
