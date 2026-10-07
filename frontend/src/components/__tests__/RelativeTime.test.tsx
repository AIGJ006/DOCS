import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RelativeTime from '../RelativeTime';

/** 상대 시간·날짜 형식 (005 FR-012, research R-29). 기준 시각은 2026-10-07T05:00:00Z = 14:00 KST. */
describe('RelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('1분 미만은 방금 전', () => {
    render(<RelativeTime value="2026-10-07T04:59:30Z" />);
    expect(screen.getByText('방금 전')).toBeInTheDocument();
  });

  it('1시간 미만은 N분 전', () => {
    render(<RelativeTime value="2026-10-07T04:17:00Z" />);
    expect(screen.getByText('43분 전')).toBeInTheDocument();
  });

  it('24시간 미만은 N시간 전', () => {
    render(<RelativeTime value="2026-10-06T06:30:00Z" />);
    expect(screen.getByText('22시간 전')).toBeInTheDocument();
  });

  it('그 이후는 한국 시간 기준 YYYY.MM.DD', () => {
    // 2026-10-02T15:30:00Z = 2026-10-03 00:30 KST → 날짜는 한국 시간으로
    render(<RelativeTime value="2026-10-02T15:30:00Z" />);
    expect(screen.getByText('2026.10.03')).toBeInTheDocument();
  });

  it('time 요소의 datetime은 UTC ISO-8601이다', () => {
    render(<RelativeTime value="2026-10-02T14:03:12.123456Z" />);
    const time = screen.getByText('2026.10.02');
    expect(time.tagName).toBe('TIME');
    expect(time).toHaveAttribute('datetime', '2026-10-02T14:03:12.123Z');
    expect(time).toHaveAttribute('title', '2026년 10월 2일 23:03');
  });

  it('미래 시각도 방금 전으로 보여준다', () => {
    render(<RelativeTime value="2026-10-07T05:00:30Z" />);
    expect(screen.getByText('방금 전')).toBeInTheDocument();
  });
});
