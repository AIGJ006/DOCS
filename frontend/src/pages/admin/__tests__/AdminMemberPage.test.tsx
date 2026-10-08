import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import type { AdminMemberView } from '../../../api/types/moderation';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import AdminMemberPage from '../AdminMemberPage';

function member(overrides: Partial<AdminMemberView> = {}): AdminMemberView {
  return {
    handle: 'writer01',
    nickname: '글쓴이',
    role: 'USER',
    status: 'ACTIVE',
    joinedAt: '2026-01-01T00:00:00Z',
    hiddenCount: 2,
    openSuspension: null,
    history: [],
    ...overrides,
  };
}

const OPEN = {
  id: 3,
  reason: '스팸 글 반복',
  startedAt: '2026-10-01T00:00:00Z',
  endsAt: null,
  suspendedByHandle: 'admin001',
  liftedAt: null,
  liftedByHandle: null,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/members/writer01']}>
      <Routes>
        <Route path="/admin/members/:handle" element={<AdminMemberPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminMemberPage', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=t';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('정지 중이면 끝(영구)·사유·[정지 해제]와 이력 표', async () => {
    const mock = stubFetch({
      'GET /api/admin/members/writer01': () =>
        json(200, member({ status: 'SUSPENDED', openSuspension: OPEN, history: [OPEN] })),
      'DELETE /api/admin/members/writer01/suspensions/current': () =>
        json(
          200,
          member({
            history: [{ ...OPEN, liftedAt: '2026-10-08T00:00:00Z', liftedByHandle: 'admin001' }],
          }),
        ),
    });
    renderPage();
    expect(await screen.findByText('사유: 스팸 글 반복')).toBeInTheDocument();
    expect(screen.getAllByText('영구').length).toBeGreaterThan(0);
    expect(screen.getByRole('table', { name: '정지 이력' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '정지 해제' }));
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '정지 해제' }),
    );
    await waitFor(() =>
      expect(
        requestsTo(mock, 'DELETE', '/api/admin/members/writer01/suspensions/current'),
      ).toHaveLength(1),
    );
    expect(await screen.findByRole('button', { name: '정지' })).toBeInTheDocument();
  });

  it('정지 칸: 기간 4개, 사유 없으면 비활성, 확인 창 "모든 기기에서 로그아웃돼요"', async () => {
    const mock = stubFetch({
      'GET /api/admin/members/writer01': () => json(200, member()),
      'POST /api/admin/members/writer01/suspensions': () =>
        json(
          201,
          member({
            status: 'SUSPENDED',
            openSuspension: { ...OPEN, endsAt: '2026-10-15T00:00:00Z' },
            history: [OPEN],
          }),
        ),
    });
    renderPage();
    const submit = await screen.findByRole('button', { name: '정지' });
    expect(screen.getAllByRole('radio')).toHaveLength(4);
    expect(submit).toBeDisabled();
    await userEvent.click(screen.getByRole('radio', { name: '30일' }));
    await userEvent.type(screen.getByLabelText('정지 사유'), '반복 위반');
    expect(screen.getByText('5/200')).toBeInTheDocument();
    await userEvent.click(submit);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/모든 기기에서 로그아웃돼요/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '정지' }));
    await waitFor(() =>
      expect(requestsTo(mock, 'POST', '/api/admin/members/writer01/suspensions')).toHaveLength(1),
    );
    const [, init] = requestsTo(mock, 'POST', '/api/admin/members/writer01/suspensions')[0];
    expect(JSON.parse(String(init?.body))).toEqual({ duration: 'P30D', reason: '반복 위반' });
    expect(await screen.findByRole('button', { name: '정지 해제' })).toBeInTheDocument();
  });

  it('관리자 회원이면 정지 칸이 없다', async () => {
    stubFetch({ 'GET /api/admin/members/writer01': () => json(200, member({ role: 'ADMIN' })) });
    renderPage();
    expect(await screen.findByText('관리자는 정지할 수 없어요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '정지' })).toBeNull();
  });

  it('409·400은 서버 문구를 보인다', async () => {
    stubFetch({
      'GET /api/admin/members/writer01': () => json(200, member()),
      'POST /api/admin/members/writer01/suspensions': () =>
        json(409, errorBody('ALREADY_SUSPENDED', '이미 정지된 회원이에요')),
    });
    renderPage();
    await userEvent.type(await screen.findByLabelText('정지 사유'), '사유');
    await userEvent.click(screen.getByRole('button', { name: '정지' }));
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '정지' }),
    );
    expect(await screen.findByText('이미 정지된 회원이에요')).toBeInTheDocument();
  });
});
