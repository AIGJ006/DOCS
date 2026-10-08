import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch, type Handler } from '../../../test/fetchRoutes';
import NotificationBell from '../NotificationBell';
import { notification } from './fixtures';

const LIST = 'GET /api/notifications';

function renderBell(list: Handler, count = 2) {
  const fetchMock = stubFetch({
    'GET /api/notifications/unread-count': () => json(200, { count }),
    [LIST]: list,
    'POST /api/notifications/read-all': () => json(200, { updated: 2 }),
  });
  render(
    <MemoryRouter>
      <NotificationBell />
    </MemoryRouter>,
  );
  return fetchMock;
}

async function openBell(name = '안 읽은 알림 2개') {
  await userEvent.click(await screen.findByRole('button', { name }));
}

/** 알림 펼침 목록 (011 T025, FR-027·FR-028). */
describe('NotificationDropdown', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('열 때마다 size=10으로 요청하고, 불러오는 동안 "불러오는 중…"', async () => {
    let release: () => void = () => {};
    const fetchMock = renderBell(
      () =>
        new Promise((resolve) => {
          release = () => resolve(json(200, { items: [notification()], nextCursor: null }));
        }),
    );
    await openBell();
    expect(screen.getByText('불러오는 중…')).toBeInTheDocument();
    release();
    expect(await screen.findByText('김민서')).toBeInTheDocument();
    const calls = requestsTo(fetchMock, 'GET', '/api/notifications');
    expect(String(calls[0][0])).toBe('/api/notifications?size=10');

    await userEvent.click(screen.getByRole('button', { name: '안 읽은 알림 2개' }));
    await userEvent.click(screen.getByRole('button', { name: '안 읽은 알림 2개' }));
    await waitFor(() => expect(requestsTo(fetchMock, 'GET', '/api/notifications')).toHaveLength(2));
  });

  it('비어 있으면 "새 알림이 없어요", 아래에 [모두 읽음]·[모든 알림 보기]', async () => {
    renderBell(() => json(200, { items: [], nextCursor: null }));
    await openBell();
    expect(await screen.findByText('새 알림이 없어요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '모두 읽음' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '모든 알림 보기' })).toHaveAttribute(
      'href',
      '/notifications',
    );
  });

  it('실패하면 "알림을 불러오지 못했어요 [다시 시도]", 배지는 그대로', async () => {
    let fail = true;
    renderBell(() =>
      fail
        ? json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요'))
        : json(200, { items: [notification()], nextCursor: null }),
    );
    await openBell();
    expect(await screen.findByText('알림을 불러오지 못했어요')).toBeInTheDocument();
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('2');
    fail = false;
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByText('김민서')).toBeInTheDocument();
  });

  it('[모두 읽음]은 배지를 지운다', async () => {
    const fetchMock = renderBell(() => json(200, { items: [notification()], nextCursor: null }));
    await openBell();
    await screen.findByText('김민서');
    await userEvent.click(screen.getByRole('button', { name: '모두 읽음' }));
    await waitFor(() => expect(screen.queryByTestId('notification-badge')).toBeNull());
    expect(requestsTo(fetchMock, 'POST', '/api/notifications/read-all')).toHaveLength(1);
  });

  it('Esc로 닫고 초점을 종으로 돌린다', async () => {
    renderBell(() => json(200, { items: [], nextCursor: null }));
    await openBell();
    await screen.findByText('새 알림이 없어요');
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByText('새 알림이 없어요')).toBeNull();
    const bell = screen.getByRole('button', { name: '안 읽은 알림 2개' });
    expect(bell).toHaveFocus();
    expect(bell).toHaveAttribute('aria-expanded', 'false');
  });
});
