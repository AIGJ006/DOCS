import 'fake-indexeddb/auto';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import type { MeSummary } from '../api/me';
import type { WorkingCopy } from '../api/posts';
import { SessionContext } from '../features/auth/sessionContext';
import { loadDraft, resetLocalDraftsForTests, saveDraft } from '../features/editor/localDraftStore';
import { ME, errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import EditorPage, { NewPostPage } from './EditorPage';

const COPY: WorkingCopy = {
  postId: 42,
  status: 'DRAFT',
  editing: false,
  title: '임시 제목',
  contentMd: '임시 본문',
  version: 3,
  savedAt: '2026-10-07T05:03:00Z',
  visibility: 'PRIVATE',
  tags: [],
  url: null,
};

let assign: ReturnType<typeof vi.fn>;

function Where() {
  const location = useLocation();
  return <output data-testid="where">{location.pathname}</output>;
}

const SIGNED_IN = ME as unknown as MeSummary;

function renderAt(path: string, me: MeSummary | null = SIGNED_IN) {
  return render(
    <SessionContext.Provider value={{ loading: false, me, refresh: async () => me }}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/write/new" element={<NewPostPage />} />
          <Route path="/write/:postId" element={<EditorPage />} />
          <Route path="/login" element={<p>로그인 화면</p>} />
        </Routes>
        <Where />
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

beforeEach(async () => {
  resetClientForTests();
  await resetLocalDraftsForTests();
  assign = vi.fn();
  vi.stubGlobal('location', { ...window.location, assign });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('EditorPage', () => {
  it('/write/new는 임시글을 만들고 /write/{postId}로 바꾼다', async () => {
    const fetchMock = stubFetch({
      'POST /api/posts': () => json(201, { ...COPY, title: '', contentMd: '', version: 0 }),
      'GET /api/posts/42/working-copy': () =>
        json(200, { ...COPY, title: '', contentMd: '', version: 0 }),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    renderAt('/write/new');

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/write/42'));
    expect(requestsTo(fetchMock, 'POST', '/api/posts')).toHaveLength(1);
    expect(await screen.findByLabelText('제목')).toHaveValue('');
  });

  it('/write/{postId}는 서버 내용으로 연다', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, COPY),
      'POST /api/markdown/preview': () => json(200, { html: '<p>임시 본문</p>' }),
    });
    renderAt('/write/42');

    expect(await screen.findByLabelText('제목')).toHaveValue('임시 제목');
    expect(screen.getByLabelText('본문')).toHaveValue('임시 본문');
    expect(screen.getByLabelText('제목')).toHaveAttribute('maxLength', '100');
  });

  it('비로그인이면 로그인 화면으로 간다', async () => {
    stubFetch({});
    renderAt('/write/42', null);
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/login'));
  });

  it('발행에 성공하면 이 기기 임시 글을 지우고 글 주소로 간다', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, COPY),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
      'POST /api/posts/42/publish': () =>
        json(200, {
          url: '/@kim755030/posts/42',
          publishedAt: '2026-10-07T05:10:00Z',
          firstPublicAt: null,
          editedAt: null,
          version: 4,
        }),
    });
    await saveDraft(ME.memberId, 42, {
      title: '임시 제목',
      contentMd: '임시 본문',
      baseVersion: 3,
      dirty: false,
      pendingImages: [],
      updatedAt: Date.now(),
    });
    const user = userEvent.setup();
    renderAt('/write/42');
    await screen.findByLabelText('제목');

    await user.click(screen.getByRole('button', { name: '글 등록' }));
    await user.click(await screen.findByRole('button', { name: '발행' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('/@kim755030/posts/42'));
    expect(await loadDraft(ME.memberId, 42)).toBeNull();
  });

  it('발행 검증 오류의 제목·본문 항목은 입력칸 옆에 보인다', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, COPY),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
      'POST /api/posts/42/publish': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
            { field: 'title', code: 'TITLE_REQUIRED', message: '제목을 입력해 주세요' },
            {
              field: 'contentMd',
              code: 'PENDING_IMAGES',
              message: '업로드가 끝나지 않은 사진이 있어요',
            },
          ]),
        ),
    });
    const user = userEvent.setup();
    renderAt('/write/42');
    await screen.findByLabelText('제목');
    await user.click(screen.getByRole('button', { name: '글 등록' }));
    await user.click(await screen.findByRole('button', { name: '발행' }));

    expect(await screen.findByText('제목을 입력해 주세요')).toBeInTheDocument();
    expect(screen.getByText('업로드가 끝나지 않은 사진이 있어요')).toBeInTheDocument();
    expect(screen.getByLabelText('제목')).toHaveAttribute('aria-invalid', 'true');
  });
});
