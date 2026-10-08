import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import AdminReportDetailPage from '../AdminReportDetailPage';
import { detail } from './adminFixtures';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/reports/7']}>
      <Routes>
        <Route path="/admin/reports/:caseId" element={<AdminReportDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminReportDetailPage', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=t';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('스냅샷은 텍스트로만, 현재 상태·기타 설명·작성자 카드와 [회원 화면]', async () => {
    stubFetch({ 'GET /api/admin/reports/7': () => json(200, detail()) });
    renderPage();
    expect(await screen.findByText('신고 당시 제목')).toBeInTheDocument();
    const content = screen.getByTestId('snapshot-content');
    expect(content).toHaveTextContent('당시 본문 <script>alert(1)</script>');
    expect(content.querySelector('script')).toBeNull();
    expect(screen.getByTestId('current-state')).toHaveTextContent('현재: 비공개');
    expect(screen.getByText('광고 링크가 있어요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '회원 화면' })).toHaveAttribute(
      'href',
      '/admin/members/writer01',
    );
  });

  it('숨기기는 사유를 골라야 켜지고 확인 창을 거쳐 보낸다', async () => {
    const mock = stubFetch({
      'GET /api/admin/reports/7': () => json(200, detail()),
      'POST /api/admin/reports/7/resolution': () =>
        json(200, detail({ status: 'HIDDEN', currentState: 'HIDDEN' })),
    });
    renderPage();
    const hide = await screen.findByRole('button', { name: '숨기기' });
    expect(hide).toBeDisabled();
    await userEvent.click(screen.getByRole('radio', { name: '스팸·광고' }));
    expect(hide).toBeEnabled();
    await userEvent.click(hide);
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '숨기기' }),
    );
    await waitFor(() =>
      expect(requestsTo(mock, 'POST', '/api/admin/reports/7/resolution')).toHaveLength(1),
    );
    const [, init] = requestsTo(mock, 'POST', '/api/admin/reports/7/resolution')[0];
    expect(JSON.parse(String(init?.body))).toEqual({ action: 'HIDE', reason: 'SPAM' });
    expect(await screen.findByRole('button', { name: '숨김 해제' })).toBeInTheDocument();
  });

  it('409면 "이미 처리된 신고예요"와 [다시 불러오기]', async () => {
    let loads = 0;
    stubFetch({
      'GET /api/admin/reports/7': () => {
        loads += 1;
        return json(200, loads === 1 ? detail() : detail({ status: 'REJECTED' }));
      },
      'POST /api/admin/reports/7/resolution': () =>
        json(
          409,
          errorBody('REPORT_ALREADY_HANDLED', '이미 처리된 신고예요', [], { status: 'REJECTED' }),
        ),
    });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: '문제없음' }));
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '문제없음' }),
    );
    expect(await screen.findByText(/이미 처리된 신고예요/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }));
    await waitFor(() => expect(screen.getByTestId('case-status')).toHaveTextContent('문제없음'));
    expect(screen.queryByRole('button', { name: '문제없음' })).toBeNull();
  });

  it('혼자 신고한 건이면 버튼 비활성과 안내', async () => {
    stubFetch({
      'GET /api/admin/reports/7': () =>
        json(200, detail({ reportedByMe: true, onlyMyReport: true })),
    });
    renderPage();
    expect(await screen.findByText(/혼자 신고한 건은 처리할 수 없어요/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '문제없음' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '숨기기' })).toBeDisabled();
  });

  it('대상 없음 사건은 처리 칸 없이 안내', async () => {
    stubFetch({
      'GET /api/admin/reports/7': () =>
        json(200, detail({ status: 'CLOSED_NO_TARGET', currentState: 'GONE', postId: null })),
    });
    renderPage();
    expect(await screen.findByText('대상이 사라져 자동으로 닫힌 신고예요')).toBeInTheDocument();
    expect(screen.getByTestId('current-state')).toHaveTextContent('현재: 대상 없음');
    expect(screen.queryByRole('button', { name: '숨기기' })).toBeNull();
  });

  it('댓글 사건은 제목 없이 내용 전체를 보인다', async () => {
    stubFetch({
      'GET /api/admin/reports/7': () =>
        json(
          200,
          detail({
            targetType: 'COMMENT',
            postId: null,
            commentId: 11,
            snapshotTitle: null,
            snapshotContent: '첫 줄\n둘째 줄',
            currentState: 'VISIBLE',
          }),
        ),
    });
    renderPage();
    const content = await screen.findByTestId('snapshot-content');
    expect(content.textContent).toBe('첫 줄\n둘째 줄');
    expect(screen.queryByRole('heading', { level: 2, name: '신고 당시 제목' })).toBeNull();
    expect(screen.getByTestId('current-state')).toHaveTextContent('현재: 보임');
  });
});
