import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch, type Handler } from '../../../test/fetchRoutes';
import NotificationSettingsSection from '../NotificationSettingsSection';

const PATH = '/api/me/notification-settings';
const ALL_ON = { COMMENT: true, REPLY: true, LIKE: true, FOLLOW: true, NEW_POST: true };

function renderSection(put: Handler) {
  const fetchMock = stubFetch({
    [`GET ${PATH}`]: () => json(200, ALL_ON),
    [`PUT ${PATH}`]: put,
  });
  render(<NotificationSettingsSection />);
  return fetchMock;
}

function bodyOf(call: unknown[]) {
  return JSON.parse(String((call[1] as RequestInit).body));
}

/** 설정 "알림" 칸 (011 T049, US6, FR-033·FR-035). */
describe('NotificationSettingsSection', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('스위치 5개와 끌 수 없는 운영 알림 안내', async () => {
    renderSection((init) => json(200, JSON.parse(String(init?.body))));
    for (const name of [
      '내 글에 달린 댓글',
      '내 댓글에 달린 답글',
      '좋아요',
      '새 팔로워',
      '팔로우한 사람의 새 글',
    ]) {
      expect(await screen.findByRole('switch', { name })).toHaveAttribute('aria-checked', 'true');
    }
    expect(screen.getAllByRole('switch')).toHaveLength(5);
    expect(screen.getByText('운영 알림(신고 결과·숨김)은 끌 수 없어요')).toBeInTheDocument();
  });

  it('바꾸면 PUT으로 다섯 값을 보낸다. 빠르게 두 번 바꾸면 마지막 상태가 남는다', async () => {
    const fetchMock = renderSection((init) => json(200, JSON.parse(String(init?.body))));
    const like = await screen.findByRole('switch', { name: '좋아요' });
    await userEvent.click(like);
    await userEvent.click(like);
    await waitFor(() => expect(requestsTo(fetchMock, 'PUT', PATH).length).toBeGreaterThan(0));
    await waitFor(() => {
      const calls = requestsTo(fetchMock, 'PUT', PATH);
      expect(bodyOf(calls[calls.length - 1])).toEqual(ALL_ON);
    });
    expect(like).toHaveAttribute('aria-checked', 'true');

    await userEvent.click(screen.getByRole('switch', { name: '팔로우한 사람의 새 글' }));
    await waitFor(() => {
      const calls = requestsTo(fetchMock, 'PUT', PATH);
      expect(bodyOf(calls[calls.length - 1])).toEqual({ ...ALL_ON, NEW_POST: false });
    });
    expect(screen.getByRole('switch', { name: '팔로우한 사람의 새 글' })).toHaveAttribute(
      'aria-checked',
      'false',
    );
  });

  it('실패하면 되돌리고 "잠시 후 다시 시도해 주세요"', async () => {
    renderSection(() => json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요')));
    const comment = await screen.findByRole('switch', { name: '내 글에 달린 댓글' });
    await userEvent.click(comment);
    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(comment).toHaveAttribute('aria-checked', 'true');
  });
});
