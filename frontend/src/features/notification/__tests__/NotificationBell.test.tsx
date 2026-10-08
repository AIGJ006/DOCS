import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { json, stubFetch } from '../../../test/fetchRoutes';
import NotificationBell from '../NotificationBell';

function renderBell(count: number) {
  stubFetch({ 'GET /api/notifications/unread-count': () => json(200, { count }) });
  return render(
    <MemoryRouter>
      <NotificationBell />
    </MemoryRouter>,
  );
}

/** 알림 종 (011 T025, FR-026). */
describe('NotificationBell', () => {
  beforeEach(() => resetClientForTests());
  afterEach(() => vi.unstubAllGlobals());

  it('0개면 배지를 숨기고 이름은 "알림"', async () => {
    renderBell(0);
    const button = await screen.findByRole('button', { name: '알림' });
    expect(button).toHaveAttribute('aria-haspopup', 'true');
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByTestId('notification-badge')).toBeNull();
  });

  it('3개면 배지 3, 이름 "안 읽은 알림 3개"', async () => {
    renderBell(3);
    await screen.findByRole('button', { name: '안 읽은 알림 3개' });
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('3');
  });

  it('100개 이상은 배지 "99+"', async () => {
    renderBell(100);
    await screen.findByRole('button', { name: '안 읽은 알림 100개' });
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('99+');
  });
});
