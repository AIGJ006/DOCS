import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import ManagePostsPage from '../../pages/ManagePostsPage';
import { json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import PublishedRow from './PublishedRow';
import { manageItem } from './testItems';

function editingRow() {
  return manageItem(41, {
    title: '고치는 중인 글',
    status: 'PUBLISHED',
    visibility: 'PRIVATE',
    editing: true,
    publishedAt: '2026-10-01T01:00:00Z',
    editedAt: '2026-10-03T05:03:00Z',
    viewCount: 1200,
    likeCount: 12,
    commentCount: 3,
  });
}

/** 발행 글 줄 (006 T066, US5, FR-008·010·015·016). */
describe('PublishedRow', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetClientForTests();
  });

  it('공개 범위 표시·[수정 중]·발행/수정 날짜·반응 숫자를 보이고 스크린 리더 글자가 있다', () => {
    render(
      <MemoryRouter>
        <ul>
          <PublishedRow
            row={editingRow()}
            handle="kim755030"
            busy={false}
            error={undefined}
            onTrash={() => {}}
            onDiscard={() => {}}
          />
        </ul>
      </MemoryRouter>,
    );

    const row = screen.getByRole('listitem');
    expect(within(row).getByText('비공개')).toHaveClass('sr-only');
    expect(within(row).getByText('수정 중')).toBeInTheDocument();
    expect(row).toHaveTextContent('발행 2026.10.01 · 수정됨 10월 3일');
    expect(row).toHaveTextContent('조회 1,200 · 좋아요 12 · 댓글 3');
    expect(within(row).getByRole('link', { name: '이어서 수정' })).toHaveAttribute(
      'href',
      '/write/41',
    );
    expect(within(row).getByRole('button', { name: '변경 취소' })).toBeInTheDocument();
  });

  it('숨긴 글은 "운영 정책에 따라 숨겨짐" 배지, 수정 중이 아니면 [수정]만', () => {
    render(
      <MemoryRouter>
        <ul>
          <PublishedRow
            row={manageItem(42, {
              status: 'PUBLISHED',
              hidden: true,
              publishedAt: '2026-10-01T01:00:00Z',
            })}
            handle={null}
            busy={true}
            error="이유"
            onTrash={() => {}}
            onDiscard={() => {}}
          />
        </ul>
      </MemoryRouter>,
    );

    expect(screen.getByText('운영 정책에 따라 숨겨짐')).toBeInTheDocument();
    expect(screen.getByText('공개')).toHaveClass('sr-only');
    expect(screen.getByRole('link', { name: '수정' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '변경 취소' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제' })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('이유');
  });

  it.todo(
    'US5_1 공개 → 비공개는 확인창 없이 PUT /api/posts/{id}/visibility — 004 VisibilitySelect·공개 범위 API가 생기면 (T068)',
  );
  it.todo(
    'US5_2 비공개 → 공개는 "모든 사람이 볼 수 있게 돼요" 확인 뒤 호출, [취소]면 호출 없음 — 004 대기 (T068)',
  );

  it('US5_3 [변경 취소]는 확인 뒤 작업본을 버리고 [수정 중] 배지가 사라지며 [수정]으로 바뀐다', async () => {
    const user = userEvent.setup();
    const mock = stubFetch({
      'GET /api/me/posts': () =>
        json(200, {
          items: [editingRow()],
          nextCursor: null,
          counts: { drafts: 0, published: 1, trash: 0 },
        }),
      'DELETE /api/posts/41/working-copy': () => new Response(null, { status: 204 }),
    });
    render(
      <MemoryRouter initialEntries={['/manage/posts?tab=published']}>
        <Routes>
          <Route path="/manage/posts" element={<ManagePostsPage />} />
        </Routes>
      </MemoryRouter>,
    );
    await screen.findByText('고치는 중인 글');

    await user.click(screen.getByRole('button', { name: '변경 취소' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('수정 중인 내용을 버리고 발행된 글로 되돌릴까요?');
    await user.click(within(dialog).getByRole('button', { name: '변경 취소' }));

    await waitFor(() => expect(screen.queryByText('수정 중')).not.toBeInTheDocument());
    expect(screen.getByRole('link', { name: '수정' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '변경 취소' })).not.toBeInTheDocument();
    expect(requestsTo(mock, 'DELETE', '/api/posts/41/working-copy')).toHaveLength(1);
    expect(screen.getByText('발행 2026.10.01 · 수정됨 10월 3일')).toBeInTheDocument();
  });
});
