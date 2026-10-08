import 'fake-indexeddb/auto';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import type { ServerCopy } from '../../../api/posts';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import DiffDialog from '../DiffDialog';

/** 002 T098 (US5 #3·#4·#5, FR-023·024): 비교 창 — 색과 기호, 화면 너비별 보기, 세 가지 선택과 닫기. */
const SERVER: ServerCopy = {
  title: '서버 제목',
  contentMd: '첫 줄\n지울 줄\n끝 줄',
  version: 7,
  savedAt: '2026-10-07T05:03:00Z',
};
const MINE = { title: '서버 제목', contentMd: '첫 줄\n더한 줄\n끝 줄' };

function setWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
}

function renderDialog(overrides: Partial<Parameters<typeof DiffDialog>[0]> = {}) {
  const props = {
    postId: 42,
    server: SERVER,
    mine: MINE,
    onKeptMine: vi.fn(),
    onLoadServer: vi.fn(async () => undefined),
    onCreated: vi.fn(),
    onServerChanged: vi.fn(),
    onClose: vi.fn(),
    ...overrides,
  };
  render(<DiffDialog {...props} />);
  return props;
}

beforeEach(() => {
  resetClientForTests();
  setWidth(1024);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('DiffDialog', () => {
  it('지운 부분은 빨간 배경 + −, 더한 부분은 초록 배경 + + (색과 기호 함께)', () => {
    renderDialog();
    const removed = screen.getAllByTestId('diff-removed');
    const added = screen.getAllByTestId('diff-added');
    expect(removed[0]).toHaveTextContent('−');
    expect(removed[0]).toHaveTextContent('지울 줄');
    expect(removed[0]).toHaveClass('diff-removed');
    expect(added[0]).toHaveTextContent('+');
    expect(added[0]).toHaveTextContent('더한 줄');
    expect(added[0]).toHaveClass('diff-added');
  });

  it('768px 이상은 좌우 나란히, 그보다 좁으면 위아래 합친 보기', () => {
    renderDialog();
    expect(screen.getByTestId('diff-body')).toHaveAttribute('data-layout', 'split');
  });

  it('375px에서는 위아래 합친 보기', () => {
    setWidth(375);
    renderDialog();
    expect(screen.getByTestId('diff-body')).toHaveAttribute('data-layout', 'unified');
  });

  it('[편집 중인 내용으로 저장]은 확인에 동의할 때만 서버 버전을 기준으로 저장한다', async () => {
    const fetchMock = stubFetch({
      'PUT /api/posts/42/working-copy': () =>
        json(200, { version: 8, savedAt: '2026-10-07T05:05:00Z' }),
    });
    const user = userEvent.setup();
    const props = renderDialog();

    await user.click(screen.getByRole('button', { name: '편집 중인 내용으로 저장' }));
    const confirm = await screen.findByRole('alertdialog');
    expect(confirm).toHaveTextContent(
      '14:03에 저장된 내용이 지금 편집 중인 내용으로 바뀌어요. 정말 저장할까요?',
    );
    await user.click(within(confirm).getByRole('button', { name: '돌아가기' }));
    expect(requestsTo(fetchMock, 'PUT', '/api/posts/42/working-copy')).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: '편집 중인 내용으로 저장' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '저장' }),
    );

    await waitFor(() => expect(props.onKeptMine).toHaveBeenCalled());
    const calls = requestsTo(fetchMock, 'PUT', '/api/posts/42/working-copy');
    expect(JSON.parse(String(calls[0][1]?.body))).toEqual({ ...MINE, baseVersion: 7 });
    expect(props.onKeptMine).toHaveBeenCalledWith(
      { version: 8, savedAt: '2026-10-07T05:05:00Z' },
      MINE,
    );
  });

  it('저장하는 사이 또 바뀌었으면(409) 새 서버 내용으로 비교를 다시 보인다', async () => {
    const newer = { ...SERVER, version: 9, contentMd: '또 바뀜' };
    stubFetch({
      'PUT /api/posts/42/working-copy': () =>
        json(
          409,
          errorBody('VERSION_CONFLICT', '다른 탭이나 기기에서 이 글이 수정되었어요', [], {
            server: newer,
          }),
        ),
    });
    const user = userEvent.setup();
    const props = renderDialog();
    await user.click(screen.getByRole('button', { name: '편집 중인 내용으로 저장' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '저장' }),
    );

    await waitFor(() => expect(props.onServerChanged).toHaveBeenCalledWith(newer));
    expect(props.onKeptMine).not.toHaveBeenCalled();
  });

  it('[저장된 내용 불러오기]는 부모에게 맡긴다', async () => {
    const user = userEvent.setup();
    const props = renderDialog();
    await user.click(screen.getByRole('button', { name: '저장된 내용 불러오기' }));
    expect(props.onLoadServer).toHaveBeenCalled();
  });

  it('[새 임시글로 따로 저장]은 편집 중인 내용으로 새 글을 만들고 그 글로 보낸다', async () => {
    const fetchMock = stubFetch({
      'POST /api/posts': () =>
        json(201, {
          postId: 99,
          status: 'DRAFT',
          editing: false,
          ...MINE,
          version: 0,
          savedAt: '2026-10-07T05:05:00Z',
          visibility: 'PUBLIC',
          tags: [],
          url: null,
        }),
    });
    const user = userEvent.setup();
    const props = renderDialog();
    await user.click(screen.getByRole('button', { name: '새 임시글로 따로 저장' }));

    await waitFor(() => expect(props.onCreated).toHaveBeenCalledWith(99));
    const calls = requestsTo(fetchMock, 'POST', '/api/posts');
    expect(JSON.parse(String(calls[0][1]?.body))).toEqual(MINE);
  });

  it('닫기는 부모에게 알린다 (배너·전송 멈춤은 부모가 유지)', async () => {
    const user = userEvent.setup();
    const props = renderDialog();
    await user.click(screen.getByRole('button', { name: '닫기' }));
    expect(props.onClose).toHaveBeenCalled();
  });

  it('제목이 다르면 제목 비교를 보이고, [다음 차이]로 옮겨 다닌다', async () => {
    const user = userEvent.setup();
    renderDialog({ mine: { ...MINE, title: '내 제목' } });
    expect(screen.getByTestId('diff-title')).toHaveTextContent('서버 제목');
    expect(screen.getByTestId('diff-title')).toHaveTextContent('내 제목');
    expect(screen.getByText('차이 2개')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음 차이' }));
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다음 차이' }));
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '이전 차이' }));
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
  });

  it('바뀌지 않은 긴 구간은 접고 펼칠 수 있다', async () => {
    const same = Array.from({ length: 10 }, (_, i) => `같은 줄 ${i + 1}`).join('\n');
    const user = userEvent.setup();
    renderDialog({
      server: { ...SERVER, contentMd: `처음\n${same}\n끝` },
      mine: { ...MINE, contentMd: `처음 바뀜\n${same}\n끝 바뀜` },
    });
    expect(screen.queryAllByText('같은 줄 5')).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: '같은 줄 6개 펼치기' }));
    expect(screen.getAllByText('같은 줄 5').length).toBeGreaterThan(0);
  });
});
