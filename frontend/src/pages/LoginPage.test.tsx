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

function renderPage() {
  return render(
    <MemoryRouter>
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
});
