import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import AdminReportsPage from '../AdminReportsPage';
import { detail, item } from './adminFixtures';

function renderPage(path = '/admin/reports') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/reports" element={<AdminReportsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function query(mock: ReturnType<typeof stubFetch>, index = -1) {
  const calls = mock.mock.calls.filter(([url]) => String(url).startsWith('/api/admin/reports?'));
  return new URL(String(calls.at(index)?.[0]), 'http://x').searchParams;
}

describe('AdminReportsPage', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=t';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('대기 탭 항목: 배지·제목·@작성자·신고 수·사유별 수', async () => {
    stubFetch({
      'GET /api/admin/reports': () => json(200, { items: [item()], nextCursor: null }),
    });
    renderPage();
    const row = await screen.findByTestId('case-item');
    expect(within(row).getByText('글')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: '신고된 글' })).toHaveAttribute(
      'href',
      '/admin/reports/1',
    );
    expect(within(row).getByText('@writer01')).toBeInTheDocument();
    expect(within(row).getByText('신고 3건')).toBeInTheDocument();
    expect(within(row).getByText('스팸·광고 2')).toBeInTheDocument();
    expect(within(row).getByText('욕설·혐오 1')).toBeInTheDocument();
  });

  it('탭을 바꾸면 ?tab=handled로 처리됨 목록을 부른다', async () => {
    const mock = stubFetch({
      'GET /api/admin/reports': () => json(200, { items: [], nextCursor: null }),
    });
    renderPage();
    await waitFor(() => expect(query(mock).get('tab')).toBe('PENDING'));
    await userEvent.click(screen.getByRole('link', { name: '처리됨' }));
    await waitFor(() => expect(query(mock).get('tab')).toBe('HANDLED'));
    expect(screen.getByRole('link', { name: '처리됨' })).toHaveAttribute('aria-current', 'page');
  });

  it('[더 보기]는 커서를 보내고 이미 있는 사건은 거른다', async () => {
    let call = 0;
    const mock = stubFetch({
      'GET /api/admin/reports': () => {
        call += 1;
        return call === 1
          ? json(200, {
              items: [item({ caseId: 1 }), item({ caseId: 2, title: '둘' })],
              nextCursor: 'c1',
            })
          : json(200, {
              items: [item({ caseId: 2, title: '둘' }), item({ caseId: 3, title: '셋' })],
              nextCursor: null,
            });
      },
    });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: '더 보기' }));
    await screen.findByText('셋');
    expect(query(mock).get('cursor')).toBe('c1');
    expect(screen.getAllByTestId('case-item')).toHaveLength(3);
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull();
  });

  it('제목은 글자 그대로 그린다', async () => {
    stubFetch({
      'GET /api/admin/reports': () =>
        json(200, { items: [item({ title: '<b>굵게</b>' })], nextCursor: null }),
    });
    renderPage();
    expect(await screen.findByText('<b>굵게</b>')).toBeInTheDocument();
  });

  it('처리됨 탭: 결과·처리자, 지금 숨김이면 [숨김 해제] → 확인 → DELETE → 버튼 사라짐, 결과는 숨김 그대로', async () => {
    const mock = stubFetch({
      'GET /api/admin/reports': () =>
        json(200, {
          items: [
            item({
              caseId: 7,
              status: 'HIDDEN',
              handledAt: new Date().toISOString(),
              handledByNickname: '운영자',
              targetHiddenNow: true,
            }),
            item({
              caseId: 8,
              title: '자동 종료',
              status: 'CLOSED_NO_TARGET',
              handledAt: new Date().toISOString(),
            }),
          ],
          nextCursor: null,
        }),
      'GET /api/admin/reports/7': () =>
        json(200, detail({ status: 'HIDDEN', currentState: 'HIDDEN' })),
      'DELETE /api/admin/posts/42/hidden': () => json(200, { hidden: false, caseId: null }),
    });
    renderPage('/admin/reports?tab=handled');
    const rows = await screen.findAllByTestId('case-item');
    expect(within(rows[0]).getByTestId('case-status')).toHaveTextContent('숨김');
    expect(within(rows[0]).getByText('운영자')).toBeInTheDocument();
    expect(within(rows[1]).getByText('자동')).toBeInTheDocument();
    expect(within(rows[1]).getByText('대상 없음')).toBeInTheDocument();

    await userEvent.click(within(rows[0]).getByRole('button', { name: '숨김 해제' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: '숨김 해제' }));
    await waitFor(() =>
      expect(requestsTo(mock, 'DELETE', '/api/admin/posts/42/hidden')).toHaveLength(1),
    );
    await waitFor(() =>
      expect(within(rows[0]).queryByRole('button', { name: '숨김 해제' })).toBeNull(),
    );
    expect(within(rows[0]).getByTestId('case-status')).toHaveTextContent('숨김');
  });

  it('처리됨 탭의 댓글 항목은 댓글 배지와 내용 앞부분을 보인다', async () => {
    stubFetch({
      'GET /api/admin/reports': () =>
        json(200, {
          items: [
            item({
              targetType: 'COMMENT',
              title: '나쁜 댓글 앞부분',
              status: 'REJECTED',
              handledAt: new Date().toISOString(),
              handledByNickname: '운영자',
            }),
          ],
          nextCursor: null,
        }),
    });
    renderPage('/admin/reports?tab=handled');
    const row = await screen.findByTestId('case-item');
    expect(within(row).getByText('댓글')).toBeInTheDocument();
    expect(within(row).getByText('나쁜 댓글 앞부분')).toBeInTheDocument();
    expect(within(row).getByText('문제없음')).toBeInTheDocument();
  });

  it('글 주소로 숨기기: 주소에서 번호를 꺼내 PUT', async () => {
    const mock = stubFetch({
      'GET /api/admin/reports': () => json(200, { items: [], nextCursor: null }),
      'PUT /api/admin/posts/55/hidden': () => json(200, { hidden: true, caseId: 9 }),
    });
    renderPage();
    const button = screen.getByRole('button', { name: '숨기기' });
    expect(button).toBeDisabled();
    await userEvent.type(screen.getByLabelText('글 주소 또는 글 번호'), '/@writer01/posts/55');
    await userEvent.selectOptions(screen.getByLabelText('숨김 사유'), 'SPAM');
    await userEvent.click(button);
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '숨기기' }),
    );
    await waitFor(() =>
      expect(requestsTo(mock, 'PUT', '/api/admin/posts/55/hidden')).toHaveLength(1),
    );
    expect(await screen.findByText('숨겼어요')).toBeInTheDocument();
  });
});
