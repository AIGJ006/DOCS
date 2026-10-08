import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { resetLogoutCleanupForTests } from '../../features/auth/logout';
import { SessionProvider } from '../../features/auth/SessionProvider';
import { RestoreGate } from '../../features/withdraw/RestoreGate';
import { ME, errorBody, json, requestsTo, stubFetch, type Handler } from '../../test/fetchRoutes';
import RestorePage from '../RestorePage';

const DAY = 24 * 60 * 60 * 1000;

function withdrawnMe(deadline: string, expired = false) {
  return {
    ...ME,
    emailVerified: true,
    status: 'WITHDRAWN',
    restoreDeadline: deadline,
    restoreExpired: expired,
  };
}

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}</p>;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/account/restore']}>
      <SessionProvider>
        <RestoreGate>
          <Where />
          <Routes>
            <Route path="/account/restore" element={<RestorePage />} />
            <Route path="/" element={<p>홈 화면</p>} />
            <Route path="/login" element={<p>로그인 화면</p>} />
          </Routes>
        </RestoreGate>
      </SessionProvider>
    </MemoryRouter>,
  );
}

let assign: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetClientForTests();
  resetLogoutCleanupForTests();
  assign = vi.fn();
  vi.stubGlobal('location', { ...window.location, assign });
});
afterEach(() => vi.unstubAllGlobals());

function routes(me: unknown, overrides: Record<string, Handler> = {}) {
  let current = me;
  const mock = stubFetch({
    'GET /api/me': () => json(200, current),
    'GET /api/auth/csrf': () => new Response(null, { status: 204 }),
    'POST /api/me/restore': () => {
      current = { ...(me as object), status: 'ACTIVE', restoreDeadline: null };
      return json(200, { status: 'ACTIVE' });
    },
    'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    ...overrides,
  });
  return mock;
}

describe('RestorePage (015 T033, docs/44 §3)', () => {
  it('복구 가능: 기한과 남은 날(올림)을 보인다', async () => {
    const deadline = new Date(Date.now() + 28 * DAY + 60_000).toISOString();
    routes(withdrawnMe(deadline));
    renderPage();
    expect(
      await screen.findByRole('heading', { name: '탈퇴 신청한 계정이에요' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/까지 복구할 수 있어요 \(29일 남음\)/)).toBeInTheDocument();
    expect(
      screen.getByText('복구하면 블로그·글·댓글이 모두 원래대로 돌아와요'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '복구하기' })).toBeInTheDocument();
  });

  it('기한이 지났으면 안내와 [로그아웃]만', async () => {
    routes(withdrawnMe(new Date(Date.now() - DAY).toISOString(), true));
    renderPage();
    expect(
      await screen.findByRole('heading', { name: '복구 기한이 지났어요' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '복구하기' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
  });

  it('[복구하기] 성공 → 홈 + "다시 오신 걸 환영해요"', async () => {
    const mock = routes(withdrawnMe(new Date(Date.now() + 10 * DAY).toISOString()));
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: '복구하기' }));
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('다시 오신 걸 환영해요');
    expect(requestsTo(mock, 'POST', '/api/me/restore')).toHaveLength(1);
  });

  it('409 RESTORE_PERIOD_EXPIRED면 기한 지남 상태로 바꾼다', async () => {
    routes(withdrawnMe(new Date(Date.now() + 60_000).toISOString()), {
      'POST /api/me/restore': () =>
        json(409, errorBody('RESTORE_PERIOD_EXPIRED', '복구 기한이 지났어요')),
    });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: '복구하기' }));
    expect(
      await screen.findByRole('heading', { name: '복구 기한이 지났어요' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '복구하기' })).not.toBeInTheDocument();
  });

  it('[로그아웃]은 로그아웃만 하고 홈으로 (유예는 계속)', async () => {
    const mock = routes(withdrawnMe(new Date(Date.now() + 10 * DAY).toISOString()));
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: '로그아웃' }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/'));
    expect(requestsTo(mock, 'POST', '/api/auth/logout')).toHaveLength(1);
    expect(requestsTo(mock, 'POST', '/api/me/restore')).toHaveLength(0);
  });

  it('비로그인이면 로그인 화면, 활동 중이면 홈', async () => {
    stubFetch({ 'GET /api/me': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')) });
    const first = renderPage();
    expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
    first.unmount();
    routes({ ...ME, emailVerified: true });
    renderPage();
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  });
});
