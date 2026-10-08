import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { registerLogoutCleanup, resetLogoutCleanupForTests } from '../../features/auth/logout';
import { SessionProvider } from '../../features/auth/SessionProvider';
import { ME, errorBody, json, requestsTo, stubFetch, type Handler } from '../../test/fetchRoutes';
import WithdrawPage from '../WithdrawPage';

const PREVIEW = {
  handle: 'kim755030',
  postCount: 1024,
  commentCount: 18,
  receivedLikeCount: 126,
  restoreDeadline: '2026-11-07T06:20:00Z',
  verification: 'PASSWORD',
};

function Where() {
  const location = useLocation();
  return (
    <p data-testid="where">
      {location.pathname + location.search}|{JSON.stringify(location.state)}
    </p>
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/settings/withdraw']}>
      <SessionProvider>
        <Where />
        <Routes>
          <Route path="/settings/withdraw" element={<WithdrawPage />} />
          <Route path="/withdrawn" element={<p>완료 화면</p>} />
          <Route path="/settings" element={<p>설정 화면</p>} />
          <Route path="/login" element={<p>로그인 화면</p>} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

function routes(overrides: Record<string, Handler> = {}) {
  return stubFetch({
    'GET /api/me': () => json(200, { ...ME, emailVerified: true }),
    'GET /api/me/withdrawal': () => json(200, PREVIEW),
    'GET /api/auth/csrf': () => new Response(null, { status: 204 }),
    'POST /api/me/withdraw': () => json(200, { restoreDeadline: '2026-11-07T06:20:00Z' }),
    ...overrides,
  });
}

async function fill(user: ReturnType<typeof userEvent.setup>, secret = 'Blog#2026a') {
  await user.click(await screen.findByLabelText('위 내용을 확인했어요'));
  await user.type(screen.getByLabelText(/비밀번호|'탈퇴'를 입력해 주세요/), secret);
}

beforeEach(() => {
  resetClientForTests();
  resetLogoutCleanupForTests();
});
afterEach(() => vi.unstubAllGlobals());

describe('WithdrawPage (015 T019, FR-003·FR-007)', () => {
  it('비로그인이면 로그인 화면으로 보내고 돌아올 곳을 남긴다', async () => {
    stubFetch({ 'GET /api/me': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')) });
    renderPage();
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent(
        '/login?returnTo=%2Fsettings%2Fwithdraw',
      ),
    );
  });

  it('숫자·주소·기한을 안내하고 사유 칸은 없다', async () => {
    routes();
    renderPage();
    expect(
      await screen.findByText('블로그 @kim755030과 글 1,024개가 바로 보이지 않아요'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('남의 글에 쓴 댓글 18개는 "탈퇴한 사용자의 댓글이에요"로 가려져요'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '30일(2026년 11월 7일 오후 3:20까지) 안에 다시 로그인하면 모두 복구할 수 있어요',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/받은 좋아요 126개가 완전히 삭제되고/)).toBeInTheDocument();
    expect(
      screen.getByText('블로그 주소 @kim755030은 다른 사람도, 나도 다시 쓸 수 없어요'),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/사유/)).not.toBeInTheDocument();
    expect(screen.queryAllByRole('textbox')).toHaveLength(0); // 비밀번호 칸만 (textbox 아님)
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('체크와 본인 확인을 모두 채워야 [탈퇴하기]가 켜지고, 처음 포커스는 버튼이 아니다', async () => {
    routes();
    const user = userEvent.setup();
    renderPage();
    const button = await screen.findByRole('button', { name: '탈퇴하기' });
    expect(button).toBeDisabled();
    expect(button).not.toHaveFocus();
    await user.click(screen.getByLabelText('위 내용을 확인했어요'));
    expect(button).toBeDisabled();
    await user.type(screen.getByLabelText('비밀번호'), 'x');
    expect(button).toBeEnabled();
    expect(button).not.toHaveFocus();
    // 탭 순서상 마지막
    const focusables = Array.from(
      document.querySelectorAll<HTMLElement>('main input, main a, main button'),
    );
    expect(focusables.at(-1)).toBe(button);
  });

  it('칸에서 Enter를 눌러도 제출되지 않는다', async () => {
    const mock = routes();
    const user = userEvent.setup();
    renderPage();
    await fill(user);
    await user.type(screen.getByLabelText('비밀번호'), '{Enter}');
    expect(requestsTo(mock, 'POST', '/api/me/withdraw')).toHaveLength(0);
  });

  it('소셜 가입은 "탈퇴" 입력 칸을 보이고 confirmText만 보낸다', async () => {
    const mock = routes({
      'GET /api/me/withdrawal': () => json(200, { ...PREVIEW, verification: 'CONFIRM_TEXT' }),
    });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByLabelText('위 내용을 확인했어요'));
    expect(screen.queryByLabelText('비밀번호')).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("'탈퇴'를 입력해 주세요"), '탈퇴');
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));
    await waitFor(() => expect(requestsTo(mock, 'POST', '/api/me/withdraw')).toHaveLength(1));
    const body = JSON.parse(String(requestsTo(mock, 'POST', '/api/me/withdraw')[0]?.[1]?.body));
    expect(body).toEqual({ confirmed: true, confirmText: '탈퇴' });
  });

  it('비밀번호가 틀리면 칸 아래에 문구', async () => {
    routes({
      'POST /api/me/withdraw': () =>
        json(400, errorBody('CURRENT_PASSWORD_MISMATCH', '현재 비밀번호가 올바르지 않아요')),
    });
    const user = userEvent.setup();
    renderPage();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('현재 비밀번호가 올바르지 않아요');
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('aria-describedby', alert.id);
    expect(screen.getByLabelText('비밀번호')).toHaveValue('');
  });

  it('429면 잠금 문구와 함께 버튼을 끈다', async () => {
    routes({
      'POST /api/me/withdraw': () =>
        json(
          429,
          errorBody('PASSWORD_CHANGE_TEMPORARILY_LOCKED', '잠시 후 다시 시도해 주세요(약 15분)'),
          { 'Retry-After': '900' },
        ),
    });
    const user = userEvent.setup();
    renderPage();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '잠시 후 다시 시도해 주세요(약 15분)',
    );
    await user.type(screen.getByLabelText('비밀번호'), 'again');
    expect(screen.getByRole('button', { name: '탈퇴하기' })).toBeDisabled();
  });

  it('관리자면 화면 위에 안내', async () => {
    routes({
      'POST /api/me/withdraw': () =>
        json(409, errorBody('ADMIN_CANNOT_WITHDRAW', '관리자 권한을 해제한 뒤 탈퇴할 수 있어요')),
    });
    const user = userEvent.setup();
    renderPage();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('관리자 권한을 해제한 뒤 탈퇴할 수 있어요');
    expect(alert).toHaveClass('notice');
  });

  it('성공하면 이 브라우저 임시 글을 지우고 기한과 함께 /withdrawn으로 간다', async () => {
    const clearMemberDrafts = vi.fn(async () => undefined);
    const flushPendingWork = vi.fn(async () => true);
    registerLogoutCleanup({ clearMemberDrafts, flushPendingWork });
    const mock = routes();
    const user = userEvent.setup();
    renderPage();
    await fill(user);
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }));

    expect(await screen.findByText('완료 화면')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent(
      '/withdrawn|{"restoreDeadline":"2026-11-07T06:20:00Z"}',
    );
    expect(clearMemberDrafts).toHaveBeenCalledWith(ME.memberId);
    expect(flushPendingWork).not.toHaveBeenCalled();
    expect(requestsTo(mock, 'POST', '/api/auth/logout')).toHaveLength(0);
    const body = JSON.parse(String(requestsTo(mock, 'POST', '/api/me/withdraw')[0]?.[1]?.body));
    expect(body).toEqual({ confirmed: true, password: 'Blog#2026a' });
  });
});
