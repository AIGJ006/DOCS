import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import AvailabilityHint, { AVAILABILITY_DELAY_MS } from './AvailabilityHint';

beforeEach(() => {
  resetClientForTests();
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('AvailabilityHint', () => {
  it('입력이 0.5초 멈춘 뒤 한 번만 요청한다', async () => {
    const fetchMock = stubFetch({
      'GET /api/handles/availability': () =>
        json(200, { available: false, reason: 'HANDLE_DUPLICATE', suggestion: 'kim_2' }),
    });
    const onSuggestion = vi.fn();
    const { rerender } = render(
      <AvailabilityHint kind="handle" value="k" onSuggestion={onSuggestion} />,
    );
    rerender(<AvailabilityHint kind="handle" value="ki" onSuggestion={onSuggestion} />);
    rerender(<AvailabilityHint kind="handle" value="kim" onSuggestion={onSuggestion} />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(AVAILABILITY_DELAY_MS - 50);
    });
    expect(fetchMock).not.toHaveBeenCalled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    const calls = requestsTo(fetchMock, 'GET', '/api/handles/availability');
    expect(calls).toHaveLength(1);
    expect(String(calls[0]?.[0])).toBe('/api/handles/availability?handle=kim');
    expect(await screen.findByText('이미 사용 중인 주소예요')).toBeInTheDocument();
    screen.getByRole('button', { name: 'kim_2 쓰기' }).click();
    expect(onSuggestion).toHaveBeenCalledWith('kim_2');
  });

  it('사용 가능하면 그렇게 알린다 (닉네임)', async () => {
    stubFetch({
      'GET /api/nicknames/availability': () => json(200, { available: true, code: null }),
    });
    render(<AvailabilityHint kind="nickname" value="김민서" />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(AVAILABILITY_DELAY_MS + 10);
    });
    expect(await screen.findByText('사용할 수 있어요')).toBeInTheDocument();
  });

  it('429면 아무것도 표시하지 않는다', async () => {
    stubFetch({
      'GET /api/nicknames/availability': () =>
        json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'), {
          'Retry-After': '30',
        }),
    });
    const { container } = render(<AvailabilityHint kind="nickname" value="김민서" />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(AVAILABILITY_DELAY_MS + 10);
    });
    expect(container).toHaveTextContent('');
  });

  it('빈 값은 요청하지 않는다', async () => {
    const fetchMock = stubFetch({});
    render(<AvailabilityHint kind="handle" value="" />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(AVAILABILITY_DELAY_MS + 10);
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
