import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { onNotFound, resetClientForTests } from '../api/client';
import type { ManageCounts, ManagePostItem } from '../api/managePosts';
import type { MeSummary } from '../api/me';
import { SessionContext } from '../features/auth/sessionContext';
import { manageItem } from '../features/manage-posts/testItems';
import { errorBody, json, ME, requestsTo, stubFetch, type Handler } from '../test/fetchRoutes';
import ManagePostsPage from './ManagePostsPage';

const COUNTS: ManageCounts = { drafts: 3, published: 24, trash: 1 };

function listResponse(items: ManagePostItem[], counts: ManageCounts | null = COUNTS) {
  return json(200, { items, nextCursor: null, counts });
}

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname + location.search}</span>;
}

function renderPage(path = '/manage/posts') {
  return render(
    <SessionContext.Provider
      value={{ loading: false, me: ME as MeSummary, refresh: async () => null }}
    >
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/manage/posts" element={<ManagePostsPage />} />
          <Route path="/write/:postId" element={<main data-route="editor" />} />
          <Route path="/login" element={<main data-route="login" />} />
        </Routes>
        <LocationProbe />
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

function rowOf(title: string): HTMLElement {
  const row = screen.getByText(title).closest('li');
  if (!row) {
    throw new Error(`row not found: ${title}`);
  }
  return row;
}

const DRAFTS = [
  manageItem(13, { title: '셋째 임시글' }),
  manageItem(12, { title: '' }),
  manageItem(11, { title: '첫 임시글' }),
];

/** 내 글 관리 화면 (006 T067, US3·US5, FR-001·004·013·014). */
describe('ManagePostsPage', () => {
  beforeEach(() => {
    resetClientForTests();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('기본 탭은 임시글이고 탭 옆에 글 수, 빈 제목은 "(제목 없음)"', async () => {
    stubFetch({ 'GET /api/me/posts': () => listResponse(DRAFTS) });
    renderPage();

    expect(await screen.findByText('셋째 임시글')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '임시글 3' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: '발행 글 24' })).toHaveAttribute(
      'aria-selected',
      'false',
    );
    expect(screen.getByRole('tab', { name: '휴지통 1' })).toBeInTheDocument();
    expect(screen.getByText('(제목 없음)')).toBeInTheDocument();
    expect(within(rowOf('첫 임시글')).getByRole('link', { name: '이어 쓰기' })).toHaveAttribute(
      'href',
      '/write/11',
    );
    expect(screen.queryByText('공개')).not.toBeInTheDocument();
  });

  it('[삭제]는 확인 뒤 그 줄만 빼고 숫자를 고치며 다른 줄 DOM은 그대로다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'GET /api/me/posts': () => listResponse(DRAFTS),
      'DELETE /api/posts/11': () => json(200, { trashed: true, purgeAt: '2026-11-02T00:00:00Z' }),
    });
    renderPage();
    await screen.findByText('첫 임시글');
    const untouched = rowOf('셋째 임시글');

    await user.click(within(rowOf('첫 임시글')).getByRole('button', { name: '삭제' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('휴지통으로 옮길까요? 30일 뒤 완전히 삭제돼요');
    await user.click(within(dialog).getByRole('button', { name: '휴지통으로' }));

    await waitFor(() => expect(screen.queryByText('첫 임시글')).not.toBeInTheDocument());
    expect(rowOf('셋째 임시글')).toBe(untouched);
    expect(screen.getByRole('tab', { name: '임시글 2' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '휴지통 2' })).toBeInTheDocument();
    expect(requestsTo(mock, 'GET', '/api/me/posts')).toHaveLength(1);
  });

  it('[삭제]에서 [취소]면 요청하지 않는다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({ 'GET /api/me/posts': () => listResponse(DRAFTS) });
    renderPage();
    await screen.findByText('첫 임시글');

    await user.click(within(rowOf('첫 임시글')).getByRole('button', { name: '삭제' }));
    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(requestsTo(mock, 'DELETE', '/api/posts/11')).toHaveLength(0);
    expect(screen.getByText('첫 임시글')).toBeInTheDocument();
  });

  it('빈 임시글이면 "빈 글이라 바로 삭제했어요"', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/me/posts': () => listResponse(DRAFTS),
      'DELETE /api/posts/12': () => json(200, { purged: true }),
    });
    renderPage();
    await screen.findByText('(제목 없음)');

    await user.click(within(rowOf('(제목 없음)')).getByRole('button', { name: '삭제' }));
    await user.click(screen.getByRole('button', { name: '휴지통으로' }));

    expect(await screen.findByRole('status')).toHaveTextContent('빈 글이라 바로 삭제했어요');
    expect(screen.getByRole('tab', { name: '임시글 2' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '휴지통 1' })).toBeInTheDocument();
  });

  it('휴지통 탭: 안내 문구, [복구]는 확인창 없이 "복구했어요 [발행 글 탭에서 보기]"', async () => {
    const user = userEvent.setup();
    const trashed = manageItem(21, {
      title: '지운 발행 글',
      status: 'PUBLISHED',
      deletedAt: '2026-10-02T01:00:00Z',
      purgeAt: '2026-11-01T01:00:00Z',
    });
    const mock = stubFetch({
      'GET /api/me/posts': () => listResponse([trashed]),
      'POST /api/posts/21/restore': () =>
        json(200, { restored: true, status: 'PUBLISHED', visibility: 'PUBLIC' }),
    });
    renderPage('/manage/posts?tab=trash');

    expect(await screen.findByText('지운 발행 글')).toBeInTheDocument();
    expect(screen.getByText('휴지통의 글은 30일 뒤 자동으로 완전히 삭제돼요')).toBeInTheDocument();
    expect(screen.getByText('(발행 글이었음)')).toBeInTheDocument();
    expect(rowOf('지운 발행 글')).toHaveTextContent(/삭제 10월 2일 · (\d+일 뒤|곧) 완전 삭제/);
    expect(String(mock.mock.calls[0][0])).toBe('/api/me/posts?tab=trash');

    await user.click(within(rowOf('지운 발행 글')).getByRole('button', { name: '복구' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('복구했어요');
    expect(within(status).getByRole('link', { name: '발행 글 탭에서 보기' })).toHaveAttribute(
      'href',
      '/manage/posts?tab=published',
    );
    expect(screen.queryByText('지운 발행 글')).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '휴지통 0' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '발행 글 25' })).toBeInTheDocument();
  });

  it('[영구 삭제]는 경고 확인 뒤 지우고 휴지통 수를 줄인다', async () => {
    const user = userEvent.setup();
    const trashed = manageItem(21, {
      title: '지운 임시글',
      deletedAt: '2026-10-02T01:00:00Z',
      purgeAt: '2026-11-01T01:00:00Z',
    });
    const mock = stubFetch({
      'GET /api/me/posts': () => listResponse([trashed]),
      'DELETE /api/posts/21/permanent': () => json(200, { purged: true }),
    });
    renderPage('/manage/posts?tab=trash');
    await screen.findByText('지운 임시글');
    expect(screen.getByText('(임시글이었음)')).toBeInTheDocument();

    await user.click(within(rowOf('지운 임시글')).getByRole('button', { name: '영구 삭제' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('영구 삭제하면 되돌릴 수 없어요. 댓글·좋아요도 함께 지워져요');
    await user.click(within(dialog).getByRole('button', { name: '영구 삭제' }));

    await waitFor(() => expect(screen.queryByText('지운 임시글')).not.toBeInTheDocument());
    expect(requestsTo(mock, 'DELETE', '/api/posts/21/permanent')).toHaveLength(1);
    expect(screen.getByRole('tab', { name: '휴지통 0' })).toBeInTheDocument();
    expect(screen.getByText('휴지통이 비어 있어요')).toBeInTheDocument();
  });

  it('US5_4 [새 글]은 확인창 없이 POST /api/posts 후 에디터로 간다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'GET /api/me/posts': () => listResponse(DRAFTS),
      'POST /api/posts': () => json(201, { postId: 99, status: 'DRAFT' }),
    });
    renderPage();
    await screen.findByText('첫 임시글');

    await user.click(screen.getByRole('button', { name: '새 글' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/write/99'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(requestsTo(mock, 'POST', '/api/posts')).toHaveLength(1);
  });

  it('US5_5 [삭제]·[복구]가 404 NOT_FOUND면 이유를 보이고 목록을 다시 부른다 (공통 404 화면 없음)', async () => {
    const user = userEvent.setup();
    const notFound = vi.fn();
    const off = onNotFound(notFound);
    let listCalls = 0;
    const list: Handler = () => {
      listCalls += 1;
      return listCalls === 1
        ? listResponse(DRAFTS)
        : listResponse(
            DRAFTS.filter((row) => row.id !== 11),
            {
              drafts: 2,
              published: 24,
              trash: 2,
            },
          );
    };
    stubFetch({
      'GET /api/me/posts': list,
      'DELETE /api/posts/11': () => json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요')),
    });
    renderPage();
    await screen.findByText('첫 임시글');

    await user.click(within(rowOf('첫 임시글')).getByRole('button', { name: '삭제' }));
    await user.click(screen.getByRole('button', { name: '휴지통으로' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이미 처리된 글이에요. 목록을 다시 불러왔어요',
    );
    await waitFor(() => expect(listCalls).toBe(2));
    await waitFor(() => expect(screen.queryByText('첫 임시글')).not.toBeInTheDocument());
    expect(screen.getByRole('tab', { name: '휴지통 2' })).toBeInTheDocument();
    expect(notFound).not.toHaveBeenCalled();
    off();
  });

  it('그 밖의 실패는 그 줄 바로 아래에 서버 문구를 보인다', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/me/posts': () => listResponse(DRAFTS),
      'DELETE /api/posts/11': () =>
        json(403, errorBody('ACCOUNT_SUSPENDED', '이용이 정지된 계정이에요')),
    });
    renderPage();
    await screen.findByText('첫 임시글');

    await user.click(within(rowOf('첫 임시글')).getByRole('button', { name: '삭제' }));
    await user.click(screen.getByRole('button', { name: '휴지통으로' }));

    expect(await within(rowOf('첫 임시글')).findByRole('alert')).toHaveTextContent(
      '이용이 정지된 계정이에요',
    );
  });

  it('발행 글 탭: 필터는 이 탭에만 있고 [비공개]를 누르면 visibility=private로 다시 부른다', async () => {
    const user = userEvent.setup();
    const published = manageItem(31, {
      title: '<img src=x onerror=alert(1)>',
      status: 'PUBLISHED',
      publishedAt: '2026-10-01T01:00:00Z',
    });
    const mock = stubFetch({ 'GET /api/me/posts': () => listResponse([published]) });
    renderPage('/manage/posts?tab=published');

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(document.querySelector('main img')).toBeNull();
    expect(
      within(rowOf('<img src=x onerror=alert(1)>')).getByRole('link', { name: '보기' }),
    ).toHaveAttribute('href', `/@${ME.handle}/posts/31`);
    await user.click(screen.getByRole('button', { name: '비공개' }));

    await waitFor(() =>
      expect(String(mock.mock.calls.at(-1)?.[0])).toBe(
        '/api/me/posts?tab=published&visibility=private',
      ),
    );
    expect(screen.getByRole('button', { name: '비공개' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('tab', { name: '임시글 3' }));
    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/manage/posts?tab=drafts'),
    );
    expect(screen.queryByRole('button', { name: '비공개' })).not.toBeInTheDocument();
  });

  it('비회원(401)은 로그인 화면으로 가고 돌아올 주소가 붙는다 (US3-3, FR-001)', async () => {
    stubFetch({
      'GET /api/me/posts': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
    });
    renderPage('/manage/posts?tab=trash');

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(
        '/login?returnTo=%2Fmanage%2Fposts%3Ftab%3Dtrash',
      ),
    );
  });
});
