import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { resetClientForTests } from '../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import ForgotPasswordPage from './ForgotPasswordPage';
import ResetPasswordPage from './ResetPasswordPage';

const TOKEN = 'A'.repeat(43);

beforeEach(() => {
  resetClientForTests();
});

function Search() {
  return <output data-testid="search">{useLocation().search}</output>;
}

describe('ForgotPasswordPage', () => {
  it('결과 문구는 하나다', async () => {
    const fetchMock = stubFetch({
      'POST /api/auth/password-reset': () =>
        json(202, { message: '가입된 이메일이면 안내 메일을 보냈어요' }),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText('이메일'), 'kim@naver.com');
    await user.click(screen.getByRole('button', { name: '안내 메일 받기' }));
    expect(await screen.findByRole('status')).toHaveTextContent(
      '가입된 이메일이면 안내 메일을 보냈어요',
    );
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/password-reset');
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ email: 'kim@naver.com' });
  });

  it('429면 기다릴 시간을 보인다', async () => {
    stubFetch({
      'POST /api/auth/password-reset': () =>
        json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'), {
          'Retry-After': '42',
        }),
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText('이메일'), 'kim@naver.com');
    await user.click(screen.getByRole('button', { name: '안내 메일 받기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('42초 뒤 다시 보낼 수 있어요');
  });
});

describe('ResetPasswordPage', () => {
  function renderAt(url: string) {
    return render(
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route
            path="/reset-password"
            element={
              <>
                <ResetPasswordPage />
                <Search />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    );
  }

  it('토큰을 주소창에서 지우고, 새 비밀번호를 보내면 모든 기기 로그아웃 안내를 보인다', async () => {
    const fetchMock = stubFetch({
      'POST /api/auth/password-reset/confirm': () => new Response(null, { status: 204 }),
    });
    const user = userEvent.setup();
    renderAt(`/reset-password?token=${TOKEN}`);
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent(''));
    await user.type(screen.getByLabelText('새 비밀번호'), 'Fresh#2026b');
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'Fresh#2026b');
    await user.click(screen.getByRole('button', { name: '새 비밀번호 저장' }));
    expect(
      await screen.findByText('모든 기기에서 로그아웃됐어요. 새 비밀번호로 다시 로그인해 주세요.'),
    ).toBeInTheDocument();
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/password-reset/confirm');
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      token: TOKEN,
      newPassword: 'Fresh#2026b',
      newPasswordConfirm: 'Fresh#2026b',
    });
  });

  it('LINK_EXPIRED면 만료 안내', async () => {
    stubFetch({
      'POST /api/auth/password-reset/confirm': () =>
        json(400, errorBody('LINK_EXPIRED', '링크가 만료됐어요')),
    });
    const user = userEvent.setup();
    renderAt(`/reset-password?token=${TOKEN}`);
    await user.type(screen.getByLabelText('새 비밀번호'), 'Fresh#2026b');
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'Fresh#2026b');
    await user.click(screen.getByRole('button', { name: '새 비밀번호 저장' }));
    expect(await screen.findByText('링크가 만료됐어요')).toBeInTheDocument();
  });

  it('규칙 오류는 칸 아래에', async () => {
    stubFetch({
      'POST /api/auth/password-reset/confirm': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
            {
              field: 'newPassword',
              code: 'PASSWORD_INVALID_LENGTH',
              message: '비밀번호는 8~16자로 입력해 주세요 (최대 16자)',
            },
          ]),
        ),
    });
    const user = userEvent.setup();
    renderAt(`/reset-password?token=${TOKEN}`);
    await user.type(screen.getByLabelText('새 비밀번호'), 'short');
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'short');
    await user.click(screen.getByRole('button', { name: '새 비밀번호 저장' }));
    expect(
      await screen.findByText('비밀번호는 8~16자로 입력해 주세요 (최대 16자)'),
    ).toBeInTheDocument();
  });

  it('토큰 없이 열면 만료 안내', () => {
    renderAt('/reset-password');
    expect(screen.getByText('링크가 만료됐어요')).toBeInTheDocument();
  });
});
