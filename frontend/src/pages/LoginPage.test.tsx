import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import LoginPage from './LoginPage';

let assign: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetClientForTests();
  assign = vi.fn();
  vi.stubGlobal('location', { ...window.location, assign });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderPage(entry = '/login') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  it('폼으로 로그인하고 redirectTo로 이동한다', async () => {
    const fetchMock = stubFetch({
      'POST /api/auth/login': () =>
        json(200, { redirectTo: '/', reagreementRequired: false, accountStatus: 'ACTIVE' }),
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('/'));
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/login');
    const body = call?.[1]?.body;
    expect(body).toBeInstanceOf(URLSearchParams);
    expect(String(body)).toBe('email=kim755030%40naver.com&password=Blog%232026a');
  });

  it('재동의가 필요하면 재동의 화면으로 간다', async () => {
    stubFetch({
      'POST /api/auth/login': () =>
        json(200, { redirectTo: '/', reagreementRequired: true, accountStatus: 'ACTIVE' }),
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
    await user.click(screen.getByRole('button', { name: '로그인' }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/reagree'));
  });

  it('실패하면 어느 칸이 틀렸는지 알리지 않는 문구 하나만 보인다', async () => {
    stubFetch({
      'POST /api/auth/login': () =>
        json(401, errorBody('INVALID_CREDENTIALS', '이메일 또는 비밀번호가 올바르지 않아요')),
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'wrong');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이메일 또는 비밀번호가 올바르지 않아요',
    );
    expect(assign).not.toHaveBeenCalled();
    expect(screen.getByLabelText('비밀번호')).toHaveValue('');
  });

  it('returnTo로 받은 사이트 안 경로를 redirect로 넘긴다', async () => {
    const fetchMock = stubFetch({
      'POST /api/auth/login': () =>
        json(200, {
          redirectTo: '/@kim/posts/3',
          reagreementRequired: false,
          accountStatus: 'ACTIVE',
        }),
    });
    const user = userEvent.setup();
    renderPage('/login?returnTo=%2F%40kim%2Fposts%2F3');
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
    await user.click(screen.getByRole('button', { name: '로그인' }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/@kim/posts/3'));
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/login');
    expect(new URLSearchParams(String(call?.[1]?.body)).get('redirect')).toBe('/@kim/posts/3');
  });

  it('외부 주소 returnTo는 보내지 않는다', async () => {
    const fetchMock = stubFetch({
      'POST /api/auth/login': () =>
        json(200, { redirectTo: '/', reagreementRequired: false, accountStatus: 'ACTIVE' }),
    });
    const user = userEvent.setup();
    renderPage('/login?returnTo=%2F%2Fevil.example.com');
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
    await user.click(screen.getByRole('button', { name: '로그인' }));
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/'));
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/login');
    expect(new URLSearchParams(String(call?.[1]?.body)).has('redirect')).toBe(false);
  });

  it('설정된 소셜 로그인 버튼만 보이고 돌아갈 곳을 redirect로 붙인다', async () => {
    stubFetch({
      'GET /api/auth/social-providers': () => json(200, { providers: ['GITHUB'] }),
    });
    renderPage('/login?returnTo=%2Fsettings');
    const github = await screen.findByRole('link', { name: 'GitHub로 계속하기' });
    expect(github).toHaveAttribute('href', '/oauth2/authorization/github?redirect=%2Fsettings');
    expect(screen.queryByRole('link', { name: 'Google로 계속하기' })).not.toBeInTheDocument();
  });

  it('앱 키가 없으면 소셜 버튼을 숨긴다', async () => {
    const fetchMock = stubFetch({
      'GET /api/auth/social-providers': () => json(200, { providers: [] }),
    });
    renderPage();
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'GET', '/api/auth/social-providers')).toHaveLength(1),
    );
    expect(screen.queryByRole('link', { name: /계속하기/ })).not.toBeInTheDocument();
  });

  it('?error=social이면 보관된 소셜 로그인 오류를 읽어 보인다', async () => {
    stubFetch({
      'GET /api/auth/social-login-error': () =>
        json(
          200,
          errorBody('ACCOUNT_SUSPENDED', '정지된 계정이에요 (~2026-10-11). 사유: 스팸', [], {
            endsAt: '2026-10-11T00:00:00Z',
            reason: '스팸',
          }),
        ),
    });
    renderPage('/login?error=social');
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '정지된 계정이에요 (~2026-10-11). 사유: 스팸',
    );
  });

  it('정지 계정 이메일 로그인은 서버 문구(기한·사유)를 보인다', async () => {
    stubFetch({
      'POST /api/auth/login': () =>
        json(
          403,
          errorBody('ACCOUNT_SUSPENDED', '정지된 계정이에요 (영구). 사유: 스팸', [], {
            endsAt: null,
            reason: '스팸',
          }),
        ),
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
    await user.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '정지된 계정이에요 (영구). 사유: 스팸',
    );
  });

  it('잠금(429)이면 기다릴 시간을 함께 보인다', async () => {
    stubFetch({
      'POST /api/auth/login': () =>
        json(429, errorBody('LOGIN_TEMPORARILY_LOCKED', '잠시 후 다시 시도해 주세요(약 15분)'), {
          'Retry-After': '900',
        }),
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('이메일'), 'kim755030@naver.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
    await user.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('약 15분 뒤 다시 시도할 수 있어요');
  });
});
