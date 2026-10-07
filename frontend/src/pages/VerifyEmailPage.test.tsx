import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { SessionProvider } from '../features/auth/SessionProvider';
import { ME, errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import VerifyEmailPage from './VerifyEmailPage';

const TOKEN = 'a'.repeat(43);

function renderPage() {
  return render(
    <BrowserRouter>
      <SessionProvider>
        <VerifyEmailPage />
      </SessionProvider>
    </BrowserRouter>,
  );
}

let replaceState: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  resetClientForTests();
  window.history.replaceState(null, '', `/verify-email?token=${TOKEN}`);
  replaceState = vi.spyOn(window.history, 'replaceState');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('VerifyEmailPage', () => {
  it('토큰을 POST로 보내 인증하고 주소창에서 토큰을 지운다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me': () => json(200, ME),
      'POST /api/auth/email-verification/confirm': () => json(200, { verified: true }),
    });
    renderPage();

    expect(await screen.findByText('인증이 완료됐어요')).toBeInTheDocument();
    const confirms = requestsTo(fetchMock, 'POST', '/api/auth/email-verification/confirm');
    expect(confirms).toHaveLength(1);
    expect(JSON.parse(String(confirms[0]?.[1]?.body))).toEqual({ token: TOKEN });
    expect(requestsTo(fetchMock, 'GET', '/api/auth/email-verification/confirm')).toHaveLength(0);
    expect(replaceState).toHaveBeenCalled();
    expect(window.location.pathname).toBe('/verify-email');
    expect(window.location.search).not.toContain('token');
  });

  it('만료된 링크면 안내하고, 로그인 상태면 인증 메일을 다시 보낼 수 있다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me': () => json(200, ME),
      'POST /api/auth/email-verification/confirm': () =>
        json(400, errorBody('LINK_EXPIRED', '링크가 만료됐어요. [인증 메일 다시 보내기]')),
      'POST /api/auth/email-verification': () => new Response(null, { status: 202 }),
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('링크가 만료됐어요')).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: '인증 메일 다시 보내기' }));
    expect(await screen.findByText('인증 메일을 다시 보냈어요')).toBeInTheDocument();
    expect(requestsTo(fetchMock, 'POST', '/api/auth/email-verification')).toHaveLength(1);
  });

  it('다시 보내기가 너무 잦으면(429) 남은 시간을 알린다', async () => {
    stubFetch({
      'GET /api/me': () => json(200, ME),
      'POST /api/auth/email-verification/confirm': () =>
        json(400, errorBody('LINK_EXPIRED', '링크가 만료됐어요. [인증 메일 다시 보내기]')),
      'POST /api/auth/email-verification': () =>
        json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'), {
          'Retry-After': '42',
        }),
    });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: '인증 메일 다시 보내기' }));
    expect(await screen.findByText(/42초 뒤에 다시 보낼 수 있어요/)).toBeInTheDocument();
  });

  it('로그인하지 않았으면 다시 보내기 대신 로그인 링크를 준다', async () => {
    stubFetch({
      'GET /api/me': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
      'POST /api/auth/email-verification/confirm': () =>
        json(400, errorBody('LINK_EXPIRED', '링크가 만료됐어요. [인증 메일 다시 보내기]')),
    });
    renderPage();
    expect(await screen.findByText('링크가 만료됐어요')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login'),
    );
    expect(screen.queryByRole('button', { name: '인증 메일 다시 보내기' })).toBeNull();
  });
});
