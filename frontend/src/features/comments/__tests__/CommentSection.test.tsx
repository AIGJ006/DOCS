import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import { MEMBER, comment, page, renderSection, root, roots, urls } from './commentFixtures';

function items() {
  return screen.getAllByTestId('comment-item');
}

/** 댓글 목록 (007 T018, US1, FR-016~024). */
describe('CommentSection 읽기', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('머리말 "댓글 N"과 최상위 20개, [댓글 더 보기]로 이어 붙인다', async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const mock = stubFetch({
      'GET /api/posts/7/comments': () => json(200, page([])),
    });
    mock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('cursor=c2')) {
        await new Promise<void>((resolve) => {
          release = resolve;
        });
        return json(200, page(roots(5, 21)));
      }
      return json(200, page(roots(20, 1), { nextCursor: 'c2' }));
    });

    renderSection({ commentCount: 25 });

    expect(screen.getByRole('heading', { name: '댓글 25' })).toBeInTheDocument();
    await waitFor(() => expect(items()).toHaveLength(20));
    await user.click(screen.getByRole('button', { name: '댓글 더 보기' }));
    const loading = await screen.findByRole('button', { name: '불러오는 중…' });
    expect(loading).toBeDisabled();
    await waitFor(() => expect(urls(mock)).toHaveLength(2));
    release();
    await waitFor(() => expect(items()).toHaveLength(25));
    expect(screen.queryByRole('button', { name: '댓글 더 보기' })).toBeNull();
    expect(urls(mock)).toEqual(['/api/posts/7/comments', '/api/posts/7/comments?cursor=c2']);
  });

  it('[댓글 더 보기] 실패면 "불러오지 못했어요 [다시 시도]", 같은 커서로 다시 요청하고 보이는 댓글은 그대로', async () => {
    const user = userEvent.setup();
    let failNext = true;
    const mock = stubFetch({});
    mock.mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes('cursor=c2')) {
        if (failNext) {
          failNext = false;
          return json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요'));
        }
        return json(200, page(roots(2, 21)));
      }
      return json(200, page(roots(20, 1), { nextCursor: 'c2' }));
    });

    renderSection({ commentCount: 22 });
    await waitFor(() => expect(items()).toHaveLength(20));
    await user.click(screen.getByRole('button', { name: '댓글 더 보기' }));

    expect(await screen.findByText('불러오지 못했어요')).toBeInTheDocument();
    expect(items()).toHaveLength(20);
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(items()).toHaveLength(22));
    expect(urls(mock).filter((u) => u.includes('cursor=c2'))).toHaveLength(2);
  });

  it('첫 페이지 실패여도 [다시 시도]로 다시 부른다', async () => {
    const user = userEvent.setup();
    let calls = 0;
    stubFetch({
      'GET /api/posts/7/comments': () =>
        ++calls === 1
          ? json(503, errorBody('AUTOSAVE_UNAVAILABLE', '잠시 후 다시 저장할게요'))
          : json(200, page([root({ id: 1 })])),
    });

    renderSection({ commentCount: 1 });

    expect(await screen.findByText('불러오지 못했어요')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(items()).toHaveLength(1));
  });

  it('답글은 3개만 보이고 [답글 5개 더 보기]로 펼친다', async () => {
    const user = userEvent.setup();
    const replies = [1, 2, 3].map((n) => comment({ id: 100 + n, parentId: 10 }));
    const mock = stubFetch({
      'GET /api/posts/7/comments': () =>
        json(200, page([root({ id: 10, replyCount: 8, repliesNextCursor: 'r1' }, replies)])),
      'GET /api/comments/10/replies': () =>
        json(200, {
          items: [4, 5, 6, 7, 8].map((n) => comment({ id: 100 + n, parentId: 10 })),
          nextCursor: null,
        }),
    });

    renderSection({ commentCount: 9 });

    const thread = await screen.findByTestId('replies-10');
    expect(within(thread).getAllByTestId('comment-item')).toHaveLength(3);
    await user.click(screen.getByRole('button', { name: '답글 5개 더 보기' }));
    await waitFor(() => expect(within(thread).getAllByTestId('comment-item')).toHaveLength(8));
    expect(urls(mock)).toContain('/api/comments/10/replies?cursor=r1');
    expect(screen.queryByRole('button', { name: /답글 \d+개 더 보기/ })).toBeNull();
  });

  it('답글의 답글은 "@닉네임에게", 대상이 탈퇴했으면 "@탈퇴한 사용자에게"', async () => {
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page([
            root({ id: 10 }, [
              comment({ id: 11, replyTo: { handle: 'b', nickname: '비회원B' } }),
              comment({ id: 12, replyTo: { withdrawn: true } }),
            ]),
          ]),
        ),
    });

    renderSection({ commentCount: 3 });

    expect(await screen.findByText('@비회원B에게')).toBeInTheDocument();
    expect(screen.getByText('@탈퇴한 사용자에게')).toBeInTheDocument();
  });

  it('상태별 문구 4가지와 [작성자] 배지', async () => {
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page([
            root({ id: 1, state: 'DELETED', content: null, author: null }, [comment({ id: 2 })]),
            root({ id: 3, state: 'HIDDEN', content: null, author: null }),
            root({ id: 4, state: 'HIDDEN', content: '내가 쓴 숨긴 글', mine: true }),
            root({ id: 5, state: 'WITHDRAWN_AUTHOR', content: null, author: null }),
            root({
              id: 6,
              content: '글쓴이 댓글',
              author: {
                handle: 'kim',
                nickname: '김민서',
                profileImageUrl: null,
                isPostAuthor: true,
              },
              edited: true,
            }),
          ]),
        ),
    });

    renderSection({ commentCount: 5 });

    expect(await screen.findByText('삭제된 댓글이에요')).toBeInTheDocument();
    expect(screen.getByText('운영 정책에 따라 숨겨진 댓글이에요')).toBeInTheDocument();
    expect(screen.getByText('숨겨졌어요 (나만 보여요)')).toBeInTheDocument();
    expect(screen.getByText('내가 쓴 숨긴 글')).toBeInTheDocument();
    expect(screen.getByText('탈퇴한 사용자의 댓글이에요')).toBeInTheDocument();
    expect(screen.getByText('탈퇴한 사용자')).toBeInTheDocument();
    const mine = document.getElementById('comment-6');
    expect(mine).not.toBeNull();
    expect(within(mine as HTMLElement).getByText('작성자')).toBeInTheDocument();
    expect(within(mine as HTMLElement).getByText('· 수정됨')).toBeInTheDocument();
    expect(within(mine as HTMLElement).getByRole('link', { name: /김민서/ })).toHaveAttribute(
      'href',
      '/@kim',
    );
    // 삭제된 자리 아래 답글은 그대로
    expect(document.getElementById('comment-2')).not.toBeNull();
  });

  it('댓글이 없으면 "첫 댓글을 남겨 보세요"', async () => {
    stubFetch({ 'GET /api/posts/7/comments': () => json(200, page([])) });

    renderSection();

    expect(await screen.findByText('첫 댓글을 남겨 보세요')).toBeInTheDocument();
  });

  it('내용은 HTML로 해석하지 않고 줄바꿈을 지킨다', async () => {
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(200, page([root({ id: 1, content: '<b>굵게</b>\n**별표**\nhttps://example.com' })])),
    });

    renderSection({ commentCount: 1 });

    const body = await screen.findByTestId('comment-content');
    expect(body.textContent).toBe('<b>굵게</b>\n**별표**\nhttps://example.com');
    expect(body.querySelector('b')).toBeNull();
    expect(body.querySelector('a')).toBeNull();
    expect(body).toHaveStyle({ whiteSpace: 'pre-line', overflowWrap: 'anywhere' });
  });

  it('[신고] 버튼은 그리지 않는다 (014 전, Q4)', async () => {
    stubFetch({ 'GET /api/posts/7/comments': () => json(200, page([root({ id: 1 })])) });

    renderSection({ commentCount: 1, viewer: MEMBER });

    await waitFor(() => expect(items()).toHaveLength(1));
    expect(screen.queryByRole('button', { name: /신고/ })).toBeNull();
  });

  it('상위에서 미리 시작한 첫 요청이 있으면 다시 부르지 않는다', async () => {
    const mock = stubFetch({ 'GET /api/posts/7/comments': () => json(200, page([])) });

    renderSection({
      commentCount: 1,
      initialPage: Promise.resolve(page([root({ id: 1 })])),
    });

    await waitFor(() => expect(items()).toHaveLength(1));
    expect(requestsTo(mock, 'GET', '/api/posts/7/comments')).toHaveLength(0);
  });
});

/** 내 댓글 바로 붙이기 (007 T030, US2 #1, Clarifications Q5). */
describe('CommentSection 쓰기', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('아직 안 불러온 댓글이 있어도 내 댓글은 끝에 바로 보이고, [댓글 더 보기]로 같은 id가 와도 한 번만', async () => {
    const user = userEvent.setup();
    const mineView = comment({ id: 99, content: '새 댓글', mine: true });
    const mock = stubFetch({
      'POST /api/posts/7/comments': () => json(201, mineView),
    });
    mock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        return json(201, mineView);
      }
      if (url.includes('cursor=c2')) {
        return json(200, page([...roots(4, 21), root({ ...mineView })]));
      }
      return json(200, page(roots(20, 1), { nextCursor: 'c2' }));
    });

    renderSection({ commentCount: 25, viewer: MEMBER });
    await waitFor(() => expect(items()).toHaveLength(20));

    await user.type(screen.getByLabelText('댓글 입력'), '새 댓글');
    await user.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(items()).toHaveLength(21));
    expect(items()[20]).toHaveAttribute('id', 'comment-99');
    expect(screen.getByRole('heading', { name: '댓글 26' })).toBeInTheDocument();
    expect(screen.getByLabelText('댓글 입력')).toHaveValue('');

    await user.click(screen.getByRole('button', { name: '댓글 더 보기' }));
    await waitFor(() => expect(items()).toHaveLength(25));
    expect(document.querySelectorAll('#comment-99')).toHaveLength(1);
  });

  it('[답글]은 그 댓글 아래 입력칸을 열고, 새 답글은 그 최상위 답글 끝에 붙는다', async () => {
    const user = userEvent.setup();
    const reply = comment({
      id: 50,
      parentId: 10,
      content: '답글이에요',
      mine: true,
      replyTo: { handle: 'user11', nickname: '회원11' },
    });
    const mock = stubFetch({
      'GET /api/posts/7/comments': () =>
        json(200, page([root({ id: 10 }, [comment({ id: 11 })]), root({ id: 20 })])),
      'POST /api/posts/7/comments': () => json(201, reply),
    });

    renderSection({ commentCount: 3, viewer: MEMBER });

    await user.click(await screen.findByRole('button', { name: '회원11님 댓글에 답글' }));
    const box = screen.getByLabelText('답글 입력');
    await user.type(box, '답글이에요');
    await user.click(
      within(box.closest('form') as HTMLElement).getByRole('button', { name: '등록' }),
    );

    const thread = await screen.findByTestId('replies-10');
    await waitFor(() => expect(within(thread).getAllByTestId('comment-item')).toHaveLength(2));
    expect(within(thread).getAllByTestId('comment-item')[1]).toHaveAttribute('id', 'comment-50');
    expect(screen.getByRole('heading', { name: '댓글 4' })).toBeInTheDocument();
    expect(screen.queryByLabelText('답글 입력')).toBeNull();
    const post = mock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      content: '답글이에요',
      replyToCommentId: 11,
    });
  });

  it('같은 요청이 처음 댓글(같은 id)로 돌아오면 다시 붙이지 않고 수도 늘리지 않는다', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/posts/7/comments': () => json(200, page([root({ id: 1, mine: true })])),
      'POST /api/posts/7/comments': () => json(200, comment({ id: 1, mine: true })),
    });

    renderSection({ commentCount: 1, viewer: MEMBER });
    await waitFor(() => expect(items()).toHaveLength(1));
    await user.type(screen.getByLabelText('댓글 입력'), '댓글 1');
    await user.click(screen.getByRole('button', { name: '등록' }));

    await waitFor(() => expect(screen.getByLabelText('댓글 입력')).toHaveValue(''));
    expect(items()).toHaveLength(1);
    expect(screen.getByRole('heading', { name: '댓글 1' })).toBeInTheDocument();
  });
});
