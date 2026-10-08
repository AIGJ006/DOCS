import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { SessionProvider } from '../features/auth/SessionProvider';
import { ME, errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import FriendButton from './FriendButton';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderButton() {
  return render(
    <MemoryRouter initialEntries={['/@bob']}>
      <SessionProvider>
        <Where />
        <Routes>
          <Route path="/login" element={<p>로그인 화면</p>} />
          <Route path="*" element={<FriendButton handle="bob" />} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

function withStatus(status: string, extra: Record<string, () => Response> = {}) {
  return stubFetch({
    'GET /api/me': () => json(200, ME),
    'GET /api/members/bob/friend': () => json(200, { status }),
    ...extra,
  });
}

beforeEach(() => resetClientForTests());
afterEach(() => vi.unstubAllGlobals());

describe('FriendButton (FR-054~056)', () => {
  it('SELF면 아무것도 보이지 않는다', async () => {
    const mock = withStatus('SELF');
    const { container } = renderButton();
    await waitFor(() => expect(requestsTo(mock, 'GET', '/api/members/bob/friend')).toHaveLength(1));
    expect(container.querySelector('button')).toBeNull();
  });

  it('NONE → [친구 요청] → PUT 뒤 응답 상태(REQUEST_SENT)로 바뀐다', async () => {
    const mock = withStatus('NONE', {
      'PUT /api/members/bob/friend': () => json(200, { status: 'REQUEST_SENT' }),
    });
    const user = userEvent.setup();
    renderButton();
    await user.click(await screen.findByRole('button', { name: '친구 요청' }));
    expect(await screen.findByRole('button', { name: '요청 취소' })).toBeInTheDocument();
    expect(requestsTo(mock, 'PUT', '/api/members/bob/friend')).toHaveLength(1);
  });

  it('REQUEST_SENT → [요청 취소] → DELETE → NONE', async () => {
    withStatus('REQUEST_SENT', {
      'DELETE /api/members/bob/friend': () => json(200, { status: 'NONE' }),
    });
    const user = userEvent.setup();
    renderButton();
    await user.click(await screen.findByRole('button', { name: '요청 취소' }));
    expect(await screen.findByRole('button', { name: '친구 요청' })).toBeInTheDocument();
  });

  it('REQUEST_RECEIVED → [수락]·[거절]', async () => {
    const mock = withStatus('REQUEST_RECEIVED', {
      'PUT /api/members/bob/friend': () => json(200, { status: 'FRIENDS' }),
    });
    const user = userEvent.setup();
    renderButton();
    expect(await screen.findByRole('button', { name: '거절' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '수락' }));
    expect(await screen.findByRole('button', { name: '친구 끊기' })).toBeInTheDocument();
    expect(requestsTo(mock, 'PUT', '/api/members/bob/friend')).toHaveLength(1);
  });

  it('[거절]은 DELETE', async () => {
    const mock = withStatus('REQUEST_RECEIVED', {
      'DELETE /api/members/bob/friend': () => json(200, { status: 'NONE' }),
    });
    const user = userEvent.setup();
    renderButton();
    await user.click(await screen.findByRole('button', { name: '거절' }));
    expect(await screen.findByRole('button', { name: '친구 요청' })).toBeInTheDocument();
    expect(requestsTo(mock, 'DELETE', '/api/members/bob/friend')).toHaveLength(1);
  });

  it('FRIENDS → [친구 끊기]는 확인한 뒤에만 DELETE', async () => {
    const mock = withStatus('FRIENDS', {
      'DELETE /api/members/bob/friend': () => json(200, { status: 'NONE' }),
    });
    const user = userEvent.setup();
    renderButton();
    await user.click(await screen.findByRole('button', { name: '친구 끊기' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(dialog).not.toBeInTheDocument();
    expect(requestsTo(mock, 'DELETE', '/api/members/bob/friend')).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: '친구 끊기' }));
    await screen.findByRole('dialog');
    await user.click(screen.getByRole('button', { name: '끊기' }));
    expect(await screen.findByRole('button', { name: '친구 요청' })).toBeInTheDocument();
    expect(requestsTo(mock, 'DELETE', '/api/members/bob/friend')).toHaveLength(1);
  });

  it('비로그인은 [친구 요청]을 누르면 로그인 화면으로(돌아올 곳 포함)', async () => {
    const mock = stubFetch({
      'GET /api/me': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
    });
    const user = userEvent.setup();
    renderButton();
    await user.click(await screen.findByRole('button', { name: '친구 요청' }));
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent('/login?returnTo=%2F%40bob'),
    );
    expect(requestsTo(mock, 'GET', '/api/members/bob/friend')).toHaveLength(0);
  });

  it('처리에 실패하면 이유를 보이고 상태는 그대로', async () => {
    withStatus('NONE', {
      'PUT /api/members/bob/friend': () =>
        json(403, errorBody('ACCOUNT_SUSPENDED', '정지된 계정이에요')),
    });
    const user = userEvent.setup();
    renderButton();
    await user.click(await screen.findByRole('button', { name: '친구 요청' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('정지된 계정이에요');
    expect(screen.getByRole('button', { name: '친구 요청' })).toBeInTheDocument();
  });
});
