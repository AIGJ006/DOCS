import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import AuthPrompt from './AuthPrompt';

function renderPrompt(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>);
}

/** 거부 안내 (004 T051·T053, FR-029). */
describe('AuthPrompt', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetClientForTests();
  });

  it('로그인 안내는 returnTo가 붙은 로그인 링크를 보인다', () => {
    renderPrompt(<AuthPrompt kind="login" loginPath="/login?returnTo=%2F%40kim%2Fposts%2F42" />);

    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요해요');
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F%40kim%2Fposts%2F42',
    );
  });

  it('인증 전 회원에게 [인증 메일 다시 보내기]를 보이고 누르면 001 재발송 API를 부른다', async () => {
    const user = userEvent.setup();
    const fetchMock = stubFetch({
      'POST /api/auth/email-verification': () => new Response(null, { status: 204 }),
    });
    vi.stubGlobal('fetch', fetchMock);
    renderPrompt(<AuthPrompt kind="verify-email" />);

    expect(screen.getByRole('alert')).toHaveTextContent('이메일 인증 후 이용할 수 있어요');
    await user.click(screen.getByRole('button', { name: '인증 메일 다시 보내기' }));

    expect(await screen.findByText('인증 메일을 보냈어요')).toBeInTheDocument();
    expect(requestsTo(fetchMock, 'POST', '/api/auth/email-verification')).toHaveLength(1);
  });

  it('재발송이 거부되면 서버 문구를 보인다', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'POST /api/auth/email-verification': () =>
          json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요')),
      }),
    );
    renderPrompt(<AuthPrompt kind="verify-email" />);

    await user.click(screen.getByRole('button', { name: '인증 메일 다시 보내기' }));

    expect(await screen.findByText('잠시 후 다시 시도해 주세요')).toBeInTheDocument();
  });

  it('탈퇴 유예 회원은 /restore 복구 화면으로 안내한다', () => {
    renderPrompt(<AuthPrompt kind="restore" />);

    expect(screen.getByRole('alert')).toHaveTextContent('탈퇴 신청한 계정이에요');
    expect(screen.getByRole('link', { name: '계정 복구하기' })).toHaveAttribute('href', '/restore');
  });

  it('정지 회원에게 "정지된 계정이에요"를 보이고 다른 행동 버튼은 없다', () => {
    const onClose = vi.fn();
    renderPrompt(<AuthPrompt kind="suspended" onClose={onClose} />);

    expect(screen.getByRole('alert')).toHaveTextContent('정지된 계정이에요');
    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual(['닫기']);
  });
});
