import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import type { NotificationItem as Item } from '../../../api/types/notification';
import { json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import NotificationItem from '../NotificationItem';
import { notification } from './fixtures';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search + location.hash}</p>;
}

function renderItem(item: Item, onRead = vi.fn()) {
  render(
    <MemoryRouter initialEntries={['/start']}>
      <Routes>
        <Route
          path="/start"
          element={
            <ul>
              <NotificationItem item={item} onRead={onRead} />
            </ul>
          }
        />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
  return onRead;
}

/** 알림 한 줄 (011 T025, FR-029·FR-031, Clarifications Q3). */
describe('NotificationItem', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('안 읽음은 ●와 굵은 문장, 시각은 <time dateTime>', () => {
    stubFetch({});
    renderItem(notification());
    expect(screen.getByText('●')).toBeInTheDocument();
    expect(screen.getByTestId('notification-text').tagName).toBe('STRONG');
    expect(document.querySelector('time')).toHaveAttribute('dateTime');
    expect(screen.getByText('5분 전')).toBeInTheDocument();
  });

  it('읽은 알림은 ● 없이 보통 굵기', () => {
    stubFetch({});
    renderItem(notification({ read: true }));
    expect(screen.queryByText('●')).toBeNull();
    expect(screen.getByTestId('notification-text').tagName).toBe('SPAN');
  });

  it('누르면 읽음 요청 후 url로 이동한다', async () => {
    const fetchMock = stubFetch({
      'PUT /api/notifications/1/read': () => new Response(null, { status: 204 }),
    });
    const onRead = renderItem(notification());
    await userEvent.click(screen.getByRole('button'));
    expect(await screen.findByTestId('where')).toHaveTextContent(
      '/@na_ms/posts/7?comment=3#comment-3',
    );
    expect(requestsTo(fetchMock, 'PUT', '/api/notifications/1/read')).toHaveLength(1);
    expect(onRead).toHaveBeenCalledWith(1);
  });

  it('url이 null이면 이동하지 않고 읽음 표시만 바꾼다', async () => {
    const fetchMock = stubFetch({
      'PUT /api/notifications/1/read': () => new Response(null, { status: 204 }),
    });
    const onRead = renderItem(
      notification({ post: { unavailable: true }, comment: null, url: null }),
    );
    await userEvent.click(screen.getByRole('button'));
    await waitFor(() => expect(onRead).toHaveBeenCalledWith(1));
    expect(screen.queryByTestId('where')).toBeNull();
    expect(requestsTo(fetchMock, 'PUT', '/api/notifications/1/read')).toHaveLength(1);
  });

  it('읽음 요청이 실패해도 이동은 한다', async () => {
    stubFetch({ 'PUT /api/notifications/1/read': () => json(500, { code: 'X' }) });
    renderItem(notification());
    await userEvent.click(screen.getByRole('button'));
    expect(await screen.findByTestId('where')).toHaveTextContent('/@na_ms/posts/7');
  });

  it('탈퇴한 사용자는 굵게 하지 않는다', () => {
    stubFetch({});
    renderItem(notification({ read: true, actor: { withdrawn: true } }));
    const text = screen.getByTestId('notification-text');
    expect(text).toHaveTextContent('탈퇴한 사용자가');
    expect(text.querySelector('strong')).toBeNull();
  });
});
