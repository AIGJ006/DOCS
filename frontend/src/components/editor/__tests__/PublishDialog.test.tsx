import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import PublishDialog from '../PublishDialog';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const PUBLISHED = {
  url: '/@kim755030/posts/42',
  publishedAt: '2026-10-07T05:00:00Z',
  firstPublicAt: '2026-10-07T05:00:00Z',
  editedAt: null,
  version: 4,
};

function renderDialog(overrides: Partial<Parameters<typeof PublishDialog>[0]> = {}) {
  const props = {
    postId: 42,
    getContent: async () => ({ title: '제목', contentMd: '본문', baseVersion: 3 }),
    initialTags: ['spring'],
    initialVisibility: 'PRIVATE' as const,
    onPublished: vi.fn(),
    onFieldErrors: vi.fn(),
    onConflict: vi.fn(),
    onClose: vi.fn(),
    ...overrides,
  };
  render(<PublishDialog {...props} />);
  return props;
}

function sentBody(call: unknown[] | undefined) {
  return JSON.parse(String((call?.[1] as RequestInit).body));
}

function sentKey(call: unknown[] | undefined) {
  return new Headers((call?.[1] as RequestInit).headers).get('Idempotency-Key');
}

beforeEach(() => {
  resetClientForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('PublishDialog', () => {
  it('태그·공개 범위·기준 버전과 새 요청 키로 발행하고 결과를 알린다', async () => {
    const fetchMock = stubFetch({ 'POST /api/posts/42/publish': () => json(200, PUBLISHED) });
    const user = userEvent.setup();
    const props = renderDialog();

    expect(screen.getByLabelText('공개 범위')).toHaveValue('PRIVATE');
    expect(screen.getByText('#spring')).toBeInTheDocument();
    await user.type(screen.getByLabelText('태그 입력'), 'JPA{Enter}');
    await user.selectOptions(screen.getByLabelText('공개 범위'), 'PUBLIC');
    await user.click(screen.getByRole('button', { name: '발행' }));

    await waitFor(() => expect(props.onPublished).toHaveBeenCalledWith(PUBLISHED));
    const [call] = requestsTo(fetchMock, 'POST', '/api/posts/42/publish');
    expect(sentBody(call)).toEqual({
      title: '제목',
      contentMd: '본문',
      tags: ['spring', 'jpa'],
      visibility: 'PUBLIC',
      baseVersion: 3,
    });
    expect(sentKey(call)).toMatch(UUID);
  });

  it('누를 때마다 새 요청 키를 만든다', async () => {
    const fetchMock = stubFetch({
      'POST /api/posts/42/publish': () =>
        json(400, errorBody('VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [])),
    });
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole('button', { name: '발행' }));
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/posts/42/publish')).toHaveLength(1),
    );
    await user.click(screen.getByRole('button', { name: '발행' }));
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/posts/42/publish')).toHaveLength(2),
    );
    const [a, b] = requestsTo(fetchMock, 'POST', '/api/posts/42/publish');
    expect(sentKey(a)).not.toBe(sentKey(b));
  });

  it('400 칸 오류를 모두 보인다 — 태그는 그 칩 옆, 제목·본문은 에디터로 넘긴다', async () => {
    stubFetch({
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
            { field: 'tags[1]', code: 'INVALID_TAG', message: '쓸 수 없는 글자가 있어요' },
          ]),
        ),
    });
    const user = userEvent.setup();
    const props = renderDialog({ initialTags: ['spring', '🔥hot'] });
    await user.click(screen.getByRole('button', { name: '발행' }));

    const tagError = await screen.findByText('쓸 수 없는 글자가 있어요');
    expect(tagError.closest('li')).toHaveTextContent('🔥hot');
    expect(props.onFieldErrors).toHaveBeenCalledWith([
      { field: 'title', code: 'TITLE_REQUIRED', message: '제목을 입력해 주세요' },
      { field: 'contentMd', code: 'PENDING_IMAGES', message: '업로드가 끝나지 않은 사진이 있어요' },
      { field: 'tags[1]', code: 'INVALID_TAG', message: '쓸 수 없는 글자가 있어요' },
    ]);
    expect(props.onPublished).not.toHaveBeenCalled();
  });

  it('008 태그 입력(TagInput)을 쓰고 보내는 tags는 칩의 정규화된 이름 순서 그대로다', async () => {
    const fetchMock = stubFetch({ 'POST /api/posts/42/publish': () => json(200, PUBLISHED) });
    const user = userEvent.setup();
    renderDialog({ initialTags: [] });

    expect(screen.getByRole('list', { name: '붙인 태그' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('태그 입력'), 'Spring Boot{Enter}#JPA,Node.JS{Enter}');
    await user.click(screen.getByRole('button', { name: '발행' }));

    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/posts/42/publish')).toHaveLength(1),
    );
    const [call] = requestsTo(fetchMock, 'POST', '/api/posts/42/publish');
    expect(sentBody(call).tags).toEqual(['spring-boot', 'jpa', 'node.js']);
  });

  it('400 TAG_BANNED_WORD는 그 칩에만 보이고 다른 칩은 정상이다', async () => {
    stubFetch({
      'POST /api/posts/42/publish': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
            {
              field: 'tags[2]',
              code: 'TAG_BANNED_WORD',
              message: '쓸 수 없는 단어가 들어 있어요',
            },
          ]),
        ),
    });
    const user = userEvent.setup();
    renderDialog({ initialTags: ['spring', 'jpa', 'nope'] });
    await user.click(screen.getByRole('button', { name: '발행' }));

    const error = await screen.findByText('쓸 수 없는 단어가 들어 있어요');
    const chip = error.closest('li')!;
    expect(chip).toHaveTextContent('#nope');
    expect(chip).toHaveAttribute('aria-invalid', 'true');
    expect(chip).toHaveTextContent('⚠');
    expect(screen.getByText('#spring').closest('li')).not.toHaveAttribute('aria-invalid');
    expect(screen.getByText('#jpa').closest('li')).not.toHaveAttribute('aria-invalid');
  });

  it('발행하지 않고 닫으면 지금 칩을 알린다(같은 화면에서 다시 열면 그대로)', async () => {
    stubFetch({});
    const user = userEvent.setup();
    const onTagsChange = vi.fn();
    renderDialog({ initialTags: ['spring'], onTagsChange });
    await user.type(screen.getByLabelText('태그 입력'), 'jpa{Enter}');
    expect(onTagsChange).toHaveBeenLastCalledWith(['spring', 'jpa']);
  });

  it('태그는 최대 10개까지 넣는다', async () => {
    stubFetch({});
    const user = userEvent.setup();
    renderDialog({ initialTags: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'] });
    expect(screen.getByLabelText('태그 입력')).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'j 태그 빼기' }));
    expect(screen.getByLabelText('태그 입력')).toBeEnabled();
  });

  it('409 VERSION_CONFLICT면 서버 내용을 넘긴다', async () => {
    const server = {
      title: '다른 곳',
      contentMd: '본문',
      version: 9,
      savedAt: '2026-10-07T05:00:00Z',
    };
    stubFetch({
      'POST /api/posts/42/publish': () =>
        json(
          409,
          errorBody('VERSION_CONFLICT', '다른 탭이나 기기에서 이 글이 수정되었어요', [], {
            server,
          }),
        ),
    });
    const user = userEvent.setup();
    const props = renderDialog();
    await user.click(screen.getByRole('button', { name: '발행' }));
    await waitFor(() => expect(props.onConflict).toHaveBeenCalledWith(server));
    expect(screen.getByRole('alert')).toHaveTextContent(
      '다른 탭이나 기기에서 이 글이 수정되었어요',
    );
  });

  it('응답 전에는 [발행]을 끄고 "발행 중…"으로 보이며 두 번 눌러도 한 번만 보낸다', async () => {
    let finish: (r: Response) => void = () => undefined;
    const fetchMock = stubFetch({
      'POST /api/posts/42/publish': () => new Promise<Response>((resolve) => (finish = resolve)),
    });
    const user = userEvent.setup();
    const props = renderDialog();

    await user.click(screen.getByRole('button', { name: '발행' }));

    const busy = await screen.findByRole('button', { name: '발행 중…' });
    expect(busy).toBeDisabled();
    await user.click(busy);
    expect(requestsTo(fetchMock, 'POST', '/api/posts/42/publish')).toHaveLength(1);
    finish(json(200, PUBLISHED));
    await waitFor(() => expect(props.onPublished).toHaveBeenCalledWith(PUBLISHED));
  });

  it('409 IN_PROGRESS면 같은 키로 다시 보낸다', async () => {
    const replies = [
      () => json(409, errorBody('IN_PROGRESS', '발행을 처리하고 있어요')),
      () => json(200, PUBLISHED),
    ];
    const fetchMock = stubFetch({ 'POST /api/posts/42/publish': () => replies.shift()!() });
    const user = userEvent.setup();
    const props = renderDialog();

    await user.click(screen.getByRole('button', { name: '발행' }));

    await waitFor(() => expect(props.onPublished).toHaveBeenCalledWith(PUBLISHED), {
      timeout: 3000,
    });
    const [a, b] = requestsTo(fetchMock, 'POST', '/api/posts/42/publish');
    expect(sentKey(a)).toMatch(UUID);
    expect(sentKey(b)).toBe(sentKey(a));
  });
  it('013: AI 추천 칩을 누르기 전에는 태그가 그대로이고, 누르면 발행 태그에 들어간다', async () => {
    const fetchMock = stubFetch({
      'GET /api/posts/42/tag-suggestions/status': () =>
        json(200, {
          available: true,
          consentRequired: false,
          consentVersion: '2026-10-08',
          provider: 'GEMINI',
          remainingToday: 20,
        }),
      'POST /api/posts/42/tag-suggestions': () =>
        json(200, {
          tags: ['jpa', 'hibernate'],
          provider: 'GEMINI',
          cached: false,
          truncated: false,
          remainingToday: 19,
        }),
      'POST /api/posts/42/publish': () => json(200, PUBLISHED),
    });
    const user = userEvent.setup();
    renderDialog({ title: '제목', contentMd: '본문' });

    await user.click(await screen.findByRole('button', { name: 'AI 태그 추천' }));
    expect(await screen.findByRole('button', { name: 'jpa 태그 붙이기' })).toBeInTheDocument();
    // 칩을 누르기 전: 태그는 그대로
    expect(screen.queryByText('#jpa')).toBeNull();
    expect(screen.getByText('#spring')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'jpa 태그 붙이기' }));
    expect(screen.getByText('#jpa')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '발행' }));
    await waitFor(() =>
      expect(requestsTo(fetchMock, 'POST', '/api/posts/42/publish')).toHaveLength(1),
    );
    expect(sentBody(requestsTo(fetchMock, 'POST', '/api/posts/42/publish')[0]).tags).toEqual([
      'spring',
      'jpa',
    ]);
  });
});
