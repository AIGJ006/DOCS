import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import { MEMBER, comment, page, renderSection, root } from './commentFixtures';

function item(id: number): HTMLElement {
  const el = document.getElementById(`comment-${id}`);
  if (!el) {
    throw new Error(`comment-${id} 없음`);
  }
  return el;
}

/** 댓글 하나의 버튼·수정·삭제 (007 T039, US3, FR-022·024). */
describe('CommentItem', () => {
  beforeEach(() => {
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('본인 정상 댓글에만 [수정]·[삭제], 숨긴 본인 댓글은 [삭제]만, 남의 댓글엔 [신고] 없음', async () => {
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page([
            root({ id: 1, mine: true }),
            root({ id: 2, mine: true, state: 'HIDDEN', content: '숨김' }),
            root({ id: 3 }),
            root({ id: 4, state: 'DELETED', content: null, author: null }, [comment({ id: 5 })]),
          ]),
        ),
    });

    renderSection({ commentCount: 4, viewer: MEMBER });
    await screen.findAllByTestId('comment-item');

    const mine = within(item(1));
    expect(mine.getByRole('button', { name: '수정' })).toBeInTheDocument();
    expect(mine.getByRole('button', { name: '삭제' })).toBeInTheDocument();
    expect(mine.getByRole('button', { name: /답글$/ })).toBeInTheDocument();

    const hidden = within(item(2));
    expect(hidden.queryByRole('button', { name: '수정' })).toBeNull();
    expect(hidden.queryByRole('button', { name: /답글$/ })).toBeNull();
    expect(hidden.getByRole('button', { name: '삭제' })).toBeInTheDocument();

    const others = within(item(3));
    expect(others.queryByRole('button', { name: '수정' })).toBeNull();
    expect(others.queryByRole('button', { name: '삭제' })).toBeNull();
    expect(others.queryByRole('button', { name: /신고/ })).toBeNull();
    expect(others.getByRole('button', { name: '회원3님 댓글에 답글' })).toBeInTheDocument();

    const placeholder = item(4).querySelector('[data-testid="comment-main"]') as HTMLElement;
    expect(within(placeholder).queryAllByRole('button')).toHaveLength(0);
  });

  it('수정 칸: [취소]·Esc는 원래대로, 저장 중 비활성, 성공하면 내용과 "· 수정됨"', async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const mock = stubFetch({
      'GET /api/posts/7/comments': () =>
        json(200, page([root({ id: 1, mine: true, content: '처음' })])),
      'PATCH /api/comments/1': () =>
        new Promise<Response>((resolve) => {
          release = () =>
            resolve(json(200, comment({ id: 1, mine: true, content: '고침', edited: true })));
        }),
    });

    renderSection({ commentCount: 1, viewer: MEMBER });
    await screen.findByText('처음');

    await user.click(screen.getByRole('button', { name: '수정' }));
    expect(screen.getByLabelText('댓글 수정')).toHaveValue('처음');
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(screen.queryByLabelText('댓글 수정')).toBeNull();

    await user.click(screen.getByRole('button', { name: '수정' }));
    await user.keyboard('{Escape}');
    expect(screen.queryByLabelText('댓글 수정')).toBeNull();

    await user.click(screen.getByRole('button', { name: '수정' }));
    const box = screen.getByLabelText('댓글 수정');
    await user.clear(box);
    await user.type(box, '고침');
    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByRole('button', { name: '저장 중…' })).toBeDisabled();
    await waitFor(() => expect(requestsTo(mock, 'PATCH', '/api/comments/1')).toHaveLength(1));
    release();

    await waitFor(() => expect(screen.queryByRole('button', { name: '저장 중…' })).toBeNull());
    expect(await screen.findByText('고침')).toBeInTheDocument();
    expect(within(item(1)).getByText('· 수정됨')).toBeInTheDocument();
    expect(screen.queryByLabelText('댓글 수정')).toBeNull();
    const patch = requestsTo(mock, 'PATCH', '/api/comments/1');
    expect(JSON.parse(String(patch[0][1]?.body))).toEqual({ content: '고침' });
  });

  it('수정 실패면 입력을 지우지 않고 문구를 보인다 (409 COMMENT_HIDDEN)', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(200, page([root({ id: 1, mine: true, content: '처음' })])),
      'PATCH /api/comments/1': () =>
        json(409, errorBody('COMMENT_HIDDEN', '숨겨진 댓글은 수정할 수 없어요')),
    });

    renderSection({ commentCount: 1, viewer: MEMBER });
    await user.click(await screen.findByRole('button', { name: '수정' }));
    await user.type(screen.getByLabelText('댓글 수정'), '!');
    await user.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('숨겨진 댓글은 수정할 수 없어요');
    expect(screen.getByLabelText('댓글 수정')).toHaveValue('처음!');
  });

  it('답글 있는 최상위 삭제 → 확인 후 "삭제된 댓글이에요" 자리, 머리말 −1', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'GET /api/posts/7/comments': () =>
        json(200, page([root({ id: 1, mine: true }, [comment({ id: 2 })])])),
      'DELETE /api/comments/1': () => new Response(null, { status: 204 }),
    });

    renderSection({ commentCount: 2, viewer: MEMBER });
    await user.click(
      await within(await waitFor(() => item(1))).findByRole('button', { name: '삭제' }),
    );
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: '삭제' }));

    await waitFor(() => expect(within(item(1)).getByText('삭제된 댓글이에요')).toBeInTheDocument());
    expect(item(2)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '댓글 1' })).toBeInTheDocument();
    expect(requestsTo(mock, 'DELETE', '/api/comments/1')).toHaveLength(1);
  });

  it('확인창에서 [취소]면 지우지 않는다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'GET /api/posts/7/comments': () => json(200, page([root({ id: 1, mine: true })])),
    });

    renderSection({ commentCount: 1, viewer: MEMBER });
    await user.click(await screen.findByRole('button', { name: '삭제' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '취소' }),
    );

    expect(item(1)).toBeInTheDocument();
    expect(requestsTo(mock, 'DELETE', '/api/comments/1')).toHaveLength(0);
  });

  it('답글 없는 최상위는 사라지고, 자리의 마지막 답글을 지우면 자리도 사라진다', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page([
            root({ id: 1, mine: true }),
            root({ id: 3, state: 'DELETED', content: null, author: null }, [
              comment({ id: 4, mine: true }),
            ]),
          ]),
        ),
      'DELETE /api/comments/1': () => new Response(null, { status: 204 }),
      'DELETE /api/comments/4': () => new Response(null, { status: 204 }),
    });

    renderSection({ commentCount: 2, viewer: MEMBER });
    await screen.findAllByTestId('comment-item');

    await user.click(within(item(1)).getByRole('button', { name: '삭제' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '삭제' }),
    );
    await waitFor(() => expect(document.getElementById('comment-1')).toBeNull());
    expect(screen.getByRole('heading', { name: '댓글 1' })).toBeInTheDocument();

    await user.click(within(item(4)).getByRole('button', { name: '삭제' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '삭제' }),
    );
    await waitFor(() => expect(document.getElementById('comment-3')).toBeNull());
    expect(document.getElementById('comment-4')).toBeNull();
    expect(screen.getByRole('heading', { name: '댓글 0' })).toBeInTheDocument();
    expect(screen.getByText('첫 댓글을 남겨 보세요')).toBeInTheDocument();
  });

  it('숨긴 내 댓글을 지우면 머리말 수는 그대로 (이미 빠짐)', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page([root({ id: 1, mine: true, state: 'HIDDEN', content: '숨김' }), root({ id: 2 })]),
        ),
      'DELETE /api/comments/1': () => new Response(null, { status: 204 }),
    });

    renderSection({ commentCount: 1, viewer: MEMBER });
    await user.click(
      await within(await waitFor(() => item(1))).findByRole('button', { name: '삭제' }),
    );
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '삭제' }),
    );

    await waitFor(() => expect(document.getElementById('comment-1')).toBeNull());
    expect(screen.getByRole('heading', { name: '댓글 1' })).toBeInTheDocument();
  });

  it('삭제 실패면 그대로 두고 문구를 보인다', async () => {
    const user = userEvent.setup();
    stubFetch({
      'GET /api/posts/7/comments': () => json(200, page([root({ id: 1, mine: true })])),
      'DELETE /api/comments/1': () =>
        json(503, errorBody('AUTOSAVE_UNAVAILABLE', '잠시 후 다시 저장할게요')),
    });

    renderSection({ commentCount: 1, viewer: MEMBER });
    await user.click(await screen.findByRole('button', { name: '삭제' }));
    await user.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: '삭제' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(item(1)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '댓글 1' })).toBeInTheDocument();
  });
  it('탈퇴한 작성자는 글자 없는 회색 아이콘과 "탈퇴한 사용자", 숨긴 내 댓글은 원문 + "숨겨졌어요 (나만 보여요)" (T049)', async () => {
    stubFetch({
      'GET /api/posts/7/comments': () =>
        json(
          200,
          page([
            root({ id: 1, state: 'WITHDRAWN_AUTHOR', content: null, author: null }),
            root({ id: 2, state: 'HIDDEN', content: '원문', mine: true }),
          ]),
        ),
    });

    renderSection({ commentCount: 2, viewer: MEMBER });
    await screen.findAllByTestId('comment-item');

    const gone = within(item(1));
    expect(gone.getByText('탈퇴한 사용자')).toBeInTheDocument();
    expect(gone.getByText('탈퇴한 사용자의 댓글이에요')).toBeInTheDocument();
    const avatar = gone.getByTestId('default-avatar');
    expect(avatar.querySelector('text')).toBeNull();
    expect(gone.queryByRole('link')).toBeNull();

    const hidden = within(item(2));
    expect(hidden.getByText('숨겨졌어요 (나만 보여요)')).toBeInTheDocument();
    expect(hidden.getByText('원문')).toBeInTheDocument();
  });
});
