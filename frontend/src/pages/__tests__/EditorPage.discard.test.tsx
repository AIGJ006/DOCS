import 'fake-indexeddb/auto';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import type { MeSummary } from '../../api/me';
import type { WorkingCopy } from '../../api/posts';
import { SessionContext } from '../../features/auth/sessionContext';
import {
  loadDraft,
  resetLocalDraftsForTests,
  saveDraft,
} from '../../features/editor/localDraftStore';
import { ME, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import EditorPage from '../EditorPage';

/** 002 T092 (US4 #3·#4): 발행 글을 고치는 중이면 "수정 중"과 [변경 취소], 확인 후 작업본을 버리고 발행본으로 다시 연다. */
const EDITING: WorkingCopy = {
  postId: 42,
  status: 'PUBLISHED',
  editing: true,
  title: '고치던 제목',
  contentMd: '고치던 본문',
  version: 5,
  savedAt: '2026-10-07T05:03:00Z',
  visibility: 'PUBLIC',
  tags: ['spring'],
  url: '/@kim755030/posts/42',
};

const PUBLISHED: WorkingCopy = {
  ...EDITING,
  editing: false,
  title: '발행한 제목',
  contentMd: '발행한 본문',
  version: 6,
  savedAt: '2026-10-07T05:10:00Z',
};

const SIGNED_IN = ME as unknown as MeSummary;

function renderEditor() {
  return render(
    <SessionContext.Provider
      value={{ loading: false, me: SIGNED_IN, refresh: async () => SIGNED_IN }}
    >
      <MemoryRouter initialEntries={['/write/42']}>
        <Routes>
          <Route path="/write/:postId" element={<EditorPage />} />
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

beforeEach(async () => {
  resetClientForTests();
  await resetLocalDraftsForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('EditorPage 변경 취소', () => {
  it('수정 중인 발행 글은 "수정 중"과 [변경 취소]가 보이고, 확인하면 작업본을 버리고 발행본으로 다시 연다', async () => {
    let discarded = false;
    const fetchMock = stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, discarded ? PUBLISHED : EDITING),
      'DELETE /api/posts/42/working-copy': () => {
        discarded = true;
        return new Response(null, { status: 204 });
      },
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    await saveDraft(ME.memberId, 42, {
      title: '고치던 제목',
      contentMd: '고치던 본문',
      baseVersion: 5,
      dirty: false,
      pendingImages: [],
      updatedAt: Date.now(),
    });
    const user = userEvent.setup();
    renderEditor();

    expect(await screen.findByLabelText('제목')).toHaveValue('고치던 제목');
    expect(screen.getByText('수정 중')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '변경 취소' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('고치던 내용을 버리고 발행한 내용으로 돌아갈까요?');
    await user.click(screen.getByRole('button', { name: '버리기' }));

    await waitFor(() => expect(screen.getByLabelText('제목')).toHaveValue('발행한 제목'));
    expect(screen.getByLabelText('본문')).toHaveValue('발행한 본문');
    expect(requestsTo(fetchMock, 'DELETE', '/api/posts/42/working-copy')).toHaveLength(1);
    expect(screen.queryByText('수정 중')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '변경 취소' })).not.toBeInTheDocument();
    const local = await loadDraft(ME.memberId, 42);
    expect(local === null || local.title === '발행한 제목').toBe(true);
  });

  it('확인 창에서 계속 고치기를 고르면 아무것도 버리지 않는다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, EDITING),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    const user = userEvent.setup();
    renderEditor();

    await screen.findByLabelText('제목');
    await user.click(screen.getByRole('button', { name: '변경 취소' }));
    await user.click(await screen.findByRole('button', { name: '계속 고치기' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(requestsTo(fetchMock, 'DELETE', '/api/posts/42/working-copy')).toHaveLength(0);
    expect(screen.getByLabelText('제목')).toHaveValue('고치던 제목');
  });

  it('임시글에는 [변경 취소]가 없다', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () =>
        json(200, { ...EDITING, status: 'DRAFT', editing: false, url: null }),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    renderEditor();

    await screen.findByLabelText('제목');
    expect(screen.queryByRole('button', { name: '변경 취소' })).not.toBeInTheDocument();
    expect(screen.queryByText('수정 중')).not.toBeInTheDocument();
  });

  it('고치지 않은 발행 글에는 [변경 취소]가 없다', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, PUBLISHED),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    renderEditor();

    await screen.findByLabelText('제목');
    expect(screen.queryByRole('button', { name: '변경 취소' })).not.toBeInTheDocument();
  });
});
