import 'fake-indexeddb/auto';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import type { MeSummary } from '../../api/me';
import type { ServerCopy, WorkingCopy } from '../../api/posts';
import { SessionContext } from '../../features/auth/sessionContext';
import {
  loadBackup,
  resetLocalDraftsForTests,
  saveDraft,
} from '../../features/editor/localDraftStore';
import { ME, errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import EditorPage, { BACKUP_NOTICE } from '../EditorPage';

/** 002 T105 (US5 #1~#5): 충돌 배너·비교 창이 에디터와 이어지는지. */
const SERVER_COPY: WorkingCopy = {
  postId: 42,
  status: 'DRAFT',
  editing: false,
  title: '다른 탭 제목',
  contentMd: '다른 탭 본문',
  version: 5,
  savedAt: '2026-10-07T05:03:00Z',
  visibility: 'PUBLIC',
  tags: [],
  url: null,
};

const SERVER: ServerCopy = {
  title: SERVER_COPY.title,
  contentMd: SERVER_COPY.contentMd,
  version: 5,
  savedAt: SERVER_COPY.savedAt,
};

const SIGNED_IN = ME as unknown as MeSummary;

function Where() {
  return <output data-testid="where">{useLocation().pathname}</output>;
}

function renderEditor() {
  return render(
    <SessionContext.Provider
      value={{ loading: false, me: SIGNED_IN, refresh: async () => SIGNED_IN }}
    >
      <MemoryRouter initialEntries={['/write/42']}>
        <Routes>
          <Route path="/write/:postId" element={<EditorPage />} />
        </Routes>
        <Where />
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

async function localDraft(baseVersion: number) {
  await saveDraft(ME.memberId, 42, {
    title: '내 제목',
    contentMd: '내 본문',
    baseVersion,
    dirty: true,
    pendingImages: [],
    updatedAt: Date.now(),
  });
}

beforeEach(async () => {
  resetClientForTests();
  await resetLocalDraftsForTests();
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('EditorPage 충돌', () => {
  it('열 때 이 기기의 안 보낸 변경이 서버와 갈라졌으면 이 기기 내용으로 열고 바로 비교 창을 연다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, SERVER_COPY),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    await localDraft(3);
    renderEditor();

    const dialog = await screen.findByRole('dialog', { name: '저장된 내용과 비교' });
    expect(screen.getByLabelText('제목')).toHaveValue('내 제목');
    expect(within(dialog).getByTestId('diff-title')).toHaveTextContent('다른 탭 제목');
    expect(screen.getByRole('alert')).toHaveTextContent(
      '⚠ 다른 탭이나 기기에서 이 글이 수정되었어요(14:03). 지금 내용은 이 기기에만 저장되고 있어요.',
    );
    expect(requestsTo(fetchMock, 'PUT', '/api/posts/42/autosave')).toHaveLength(0);
  });

  it('닫으면 배너가 남고, 충돌 중 [발행하기]·[저장]은 비교 창을 다시 연다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, SERVER_COPY),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    await localDraft(3);
    const user = userEvent.setup();
    renderEditor();
    const dialog = await screen.findByRole('dialog', { name: '저장된 내용과 비교' });

    await user.click(within(dialog).getByRole('button', { name: '닫기' }));
    expect(screen.queryByRole('dialog', { name: '저장된 내용과 비교' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(
      '다른 탭이나 기기에서 이 글이 수정되었어요',
    );
    // 편집은 막지 않는다
    await user.type(screen.getByLabelText('본문'), ' 더');
    expect(screen.getByLabelText('본문')).toHaveValue('내 본문 더');

    await user.click(screen.getByRole('button', { name: '발행하기' }));
    expect(await screen.findByRole('dialog', { name: '저장된 내용과 비교' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '발행 설정' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '닫기' }));

    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByRole('dialog', { name: '저장된 내용과 비교' })).toBeInTheDocument();
    expect(requestsTo(fetchMock, 'PUT', '/api/posts/42/working-copy')).toHaveLength(0);
  });

  it('[저장된 내용 불러오기] → 에디터가 서버 내용, 편집 중 내용은 백업 + 안내, 배너 사라짐', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, SERVER_COPY),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
    });
    await localDraft(3);
    const user = userEvent.setup();
    renderEditor();
    await screen.findByRole('dialog', { name: '저장된 내용과 비교' });

    await user.click(screen.getByRole('button', { name: '저장된 내용 불러오기' }));

    await waitFor(() => expect(screen.getByLabelText('제목')).toHaveValue('다른 탭 제목'));
    expect(screen.getByLabelText('본문')).toHaveValue('다른 탭 본문');
    expect(screen.getByText(BACKUP_NOTICE)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(await loadBackup(ME.memberId, 42)).toMatchObject({
      title: '내 제목',
      contentMd: '내 본문',
    });
    expect(screen.getByText('✓ 저장됨 14:03')).toBeInTheDocument();
  });

  it('[저장]이 409면 바로 비교 창, [편집 중인 내용으로 저장]은 서버 버전으로 덮고 충돌을 푼다', async () => {
    let saves = 0;
    const fetchMock = stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, { ...SERVER_COPY, version: 4 }),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
      'PUT /api/posts/42/working-copy': () => {
        saves += 1;
        return saves === 1
          ? json(
              409,
              errorBody('VERSION_CONFLICT', '다른 탭이나 기기에서 이 글이 수정되었어요', [], {
                server: SERVER,
              }),
            )
          : json(200, { version: 6, savedAt: '2026-10-07T05:05:00Z' });
      },
    });
    const user = userEvent.setup();
    renderEditor();
    await screen.findByLabelText('제목');
    await user.clear(screen.getByLabelText('제목'));
    await user.type(screen.getByLabelText('제목'), '내 제목');

    await user.click(screen.getByRole('button', { name: '저장' }));
    const dialog = await screen.findByRole('dialog', { name: '저장된 내용과 비교' });
    await user.click(within(dialog).getByRole('button', { name: '편집 중인 내용으로 저장' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '저장' }),
    );

    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '저장된 내용과 비교' })).not.toBeInTheDocument(),
    );
    const puts = requestsTo(fetchMock, 'PUT', '/api/posts/42/working-copy');
    expect(JSON.parse(String(puts[1][1]?.body))).toMatchObject({
      title: '내 제목',
      baseVersion: 5,
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByText('✓ 저장됨 14:05')).toBeInTheDocument();
  });

  it('[새 임시글로 따로 저장] → 새 글로 이동', async () => {
    stubFetch({
      'GET /api/posts/42/working-copy': () => json(200, SERVER_COPY),
      'GET /api/posts/99/working-copy': () =>
        json(200, {
          ...SERVER_COPY,
          postId: 99,
          title: '내 제목',
          contentMd: '내 본문',
          version: 0,
        }),
      'POST /api/markdown/preview': () => json(200, { html: '' }),
      'POST /api/posts': () =>
        json(201, {
          ...SERVER_COPY,
          postId: 99,
          title: '내 제목',
          contentMd: '내 본문',
          version: 0,
        }),
    });
    await localDraft(3);
    const user = userEvent.setup();
    renderEditor();
    await screen.findByRole('dialog', { name: '저장된 내용과 비교' });

    await user.click(screen.getByRole('button', { name: '새 임시글로 따로 저장' }));

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/write/99'));
    await waitFor(() => expect(screen.getByLabelText('제목')).toHaveValue('내 제목'));
    expect(screen.queryByRole('dialog', { name: '저장된 내용과 비교' })).not.toBeInTheDocument();
  });
});
