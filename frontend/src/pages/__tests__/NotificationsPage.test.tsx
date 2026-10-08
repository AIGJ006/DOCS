import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import type { MeSummary } from '../../api/me';
import { SessionContext } from '../../features/auth/sessionContext';
import { notification } from '../../features/notification/__tests__/fixtures';
import { ME, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import NotificationsPage from '../NotificationsPage';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function items(ids: number[]) {
  return ids.map((id) => notification({ id, url: null, post: { title: `글 ${id}`, url: '/x' } }));
}

function renderPage({ loggedIn = true } = {}) {
  const session = {
    loading: false,
    me: loggedIn ? ({ ...ME, emailVerified: true } as MeSummary) : null,
    refresh: async () => null,
  };
  return render(
    <MemoryRouter initialEntries={['/notifications']}>
      <SessionContext.Provider value={session}>
        <Routes>
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </SessionContext.Provider>
    </MemoryRouter>,
  );
}

/** 알림 화면 `/notifications` (011 T026, FR-029·FR-030·FR-031). */
describe('NotificationsPage', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('비회원은 로그인 화면으로 보낸다 (요청 없음)', async () => {
    const fetchMock = stubFetch({});
    renderPage({ loggedIn: false });
    expect(await screen.findByTestId('where')).toHaveTextContent(
      '/login?returnTo=%2Fnotifications',
    );
    expect(requestsTo(fetchMock, 'GET', '/api/notifications')).toHaveLength(0);
  });

  it('20개씩 [더 보기]로 이어 붙이고 같은 번호는 건너뛴다', async () => {
    const first = items(Array.from({ length: 20 }, (_, i) => 40 - i));
    const second = items([21, 20, 19]);
    let call = 0;
    const fetchMock = stubFetch({
      'GET /api/notifications': () =>
        json(
          200,
          call++ === 0 ? { items: first, nextCursor: 'c1' } : { items: second, nextCursor: null },
        ),
    });
    renderPage();
    await waitFor(() => expect(screen.getAllByTestId('notification-item')).toHaveLength(20));
    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));
    await waitFor(() => expect(screen.getAllByTestId('notification-item')).toHaveLength(22));
    const calls = requestsTo(fetchMock, 'GET', '/api/notifications');
    expect(String(calls[0][0])).toBe('/api/notifications?size=20');
    expect(String(calls[1][0])).toBe('/api/notifications?size=20&cursor=c1');
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
  });

  it('[×]를 누르면 지우고 목록에서 뺀다', async () => {
    const fetchMock = stubFetch({
      'GET /api/notifications': () => json(200, { items: items([2, 1]), nextCursor: null }),
      'DELETE /api/notifications/2': () => new Response(null, { status: 204 }),
    });
    renderPage();
    await waitFor(() => expect(screen.getAllByTestId('notification-item')).toHaveLength(2));
    const firstRow = screen.getAllByTestId('notification-item')[0];
    await userEvent.click(within(firstRow).getByRole('button', { name: '알림 삭제' }));
    await waitFor(() => expect(screen.getAllByTestId('notification-item')).toHaveLength(1));
    expect(requestsTo(fetchMock, 'DELETE', '/api/notifications/2')).toHaveLength(1);
    expect(screen.queryByText('글 2', { exact: false })).toBeNull();
  });

  it('[모두 읽음]은 모두 읽음 표시로 바꾸고 바뀐 개수를 알린다', async () => {
    stubFetch({
      'GET /api/notifications': () => json(200, { items: items([2, 1]), nextCursor: null }),
      'POST /api/notifications/read-all': () => json(200, { updated: 2 }),
    });
    renderPage();
    await waitFor(() => expect(screen.getAllByText('●')).toHaveLength(2));
    await userEvent.click(screen.getByRole('button', { name: '모두 읽음' }));
    expect(await screen.findByText('알림 2개를 읽음으로 바꿨어요')).toBeInTheDocument();
    expect(screen.queryByText('●')).toBeNull();
  });

  it('비어 있으면 "새 알림이 없어요"', async () => {
    stubFetch({ 'GET /api/notifications': () => json(200, { items: [], nextCursor: null }) });
    renderPage();
    expect(await screen.findByText('새 알림이 없어요')).toBeInTheDocument();
  });
});
