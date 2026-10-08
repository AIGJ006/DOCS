import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, stubFetch } from '../../../test/fetchRoutes';
import ReportButton from '../ReportButton';

const MEMBER = { loggedIn: true, emailVerified: true, isAdmin: false, isAuthor: false };

function renderButton(viewer = MEMBER) {
  return render(
    <MemoryRouter initialEntries={['/@a/posts/42']}>
      <ReportButton targetType="POST" targetId={42} viewer={viewer} />
    </MemoryRouter>,
  );
}

describe('ReportButton', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=t';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('성공하면 창이 닫히고 접수 안내가 뜬다', async () => {
    stubFetch({ 'POST /api/reports': () => json(200, { accepted: true }) });
    renderButton();
    await userEvent.click(screen.getByRole('button', { name: '신고' }));
    await userEvent.click(screen.getByRole('radio', { name: '저작권 침해' }));
    await userEvent.click(screen.getByRole('button', { name: '신고하기' }));
    expect(await screen.findByText('신고가 접수됐어요. 검토 후 처리할게요')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('비회원이 누르면 창 없이 로그인 안내', async () => {
    const fetchMock = stubFetch({});
    renderButton({ ...MEMBER, loggedIn: false, emailVerified: false });
    await userEvent.click(screen.getByRole('button', { name: '신고' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByText('로그인이 필요해요')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('인증 전 회원은 이메일 인증 안내', async () => {
    stubFetch({});
    renderButton({ ...MEMBER, emailVerified: false });
    await userEvent.click(screen.getByRole('button', { name: '신고' }));
    expect(screen.getByText('이메일 인증 후 이용할 수 있어요')).toBeInTheDocument();
  });

  it('서버가 401이면 창을 닫고 로그인 안내', async () => {
    stubFetch({
      'POST /api/reports': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
    });
    renderButton();
    await userEvent.click(screen.getByRole('button', { name: '신고' }));
    await userEvent.click(screen.getByRole('radio', { name: '스팸·광고' }));
    await userEvent.click(screen.getByRole('button', { name: '신고하기' }));
    expect(await screen.findByText('로그인이 필요해요')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('작성자에게는 버튼이 없다', () => {
    stubFetch({});
    renderButton({ ...MEMBER, isAuthor: true });
    expect(screen.queryByRole('button', { name: '신고' })).toBeNull();
  });
});
