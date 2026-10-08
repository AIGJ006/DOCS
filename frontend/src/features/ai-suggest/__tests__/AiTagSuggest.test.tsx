import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import type { TagSuggestResponse, TagSuggestStatus } from '../../../api/types/tagSuggestions';
import AiTagSuggest from '../AiTagSuggest';
import type { SuggestApi } from '../useTagSuggest';

const STATUS: TagSuggestStatus = {
  available: true,
  consentRequired: false,
  consentVersion: '2026-10-08',
  provider: 'GEMINI',
  remainingToday: 20,
};

function response(over: Partial<TagSuggestResponse> = {}): TagSuggestResponse {
  return {
    tags: ['spring', 'hibernate'],
    provider: 'GEMINI',
    cached: false,
    truncated: false,
    remainingToday: 19,
    ...over,
  };
}

function apiOf(over: Partial<SuggestApi> = {}): SuggestApi {
  return {
    getStatus: vi.fn(async () => STATUS),
    suggest: vi.fn(async () => response()),
    agreeAi: vi.fn(async () => ({})),
    ...over,
  };
}

function apiError(status: number, code: string, details: Record<string, unknown> | null = null) {
  return new ApiError(status, code, '서버 문구', [], details);
}

/** 태그 입력을 흉내 내는 부모 (칩을 누르면 태그가 붙는다) */
function Harness({ api, initial = [] }: { api: SuggestApi; initial?: string[] }) {
  const [tags, setTags] = useState<string[]>(initial);
  return (
    <>
      <p data-testid="tags">{tags.join(',')}</p>
      <AiTagSuggest
        postId={42}
        title="JPA 정리"
        contentMd="본문"
        currentTags={tags}
        max={10}
        onAdd={(name) => {
          setTags((t) => [...t, name]);
          return true;
        }}
        api={api}
      />
    </>
  );
}

async function pressSuggest(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'AI 태그 추천' }));
}

describe('AiTagSuggest', () => {
  it('available = false면 영역을 그리지 않는다', async () => {
    const api = apiOf({
      getStatus: vi.fn(async () => ({ ...STATUS, available: false, provider: null })),
    });
    const { container } = render(<Harness api={api} />);
    await waitFor(() => expect(api.getStatus).toHaveBeenCalled());
    expect(container.querySelector('.ai-suggest')).toBeNull();
    expect(screen.queryByRole('button', { name: 'AI 태그 추천' })).toBeNull();
  });

  it('상태 조회가 실패해도 영역을 그리지 않는다', async () => {
    const api = apiOf({ getStatus: vi.fn(async () => Promise.reject(apiError(404, 'NOT_FOUND'))) });
    const { container } = render(<Harness api={api} />);
    await waitFor(() => expect(api.getStatus).toHaveBeenCalled());
    expect(container.querySelector('.ai-suggest')).toBeNull();
  });

  it('칩을 누르면 태그 입력에 들어가고 칩이 사라진다 — 서버 요청은 더 없다', async () => {
    const user = userEvent.setup();
    const api = apiOf();
    render(<Harness api={api} />);
    await pressSuggest(user);

    const chip = await screen.findByRole('button', { name: 'spring 태그 붙이기' });
    expect(chip).toHaveTextContent('+ spring');
    expect(screen.getByText('AI 제안이에요')).toBeInTheDocument();
    expect(screen.getByText('오늘 남은 추천 19회')).toBeInTheDocument();
    expect(api.suggest).toHaveBeenCalledWith(42, {
      title: 'JPA 정리',
      contentMd: '본문',
      currentTags: [],
      refresh: false,
    });

    await user.click(chip);
    expect(screen.getByTestId('tags')).toHaveTextContent('spring');
    expect(screen.queryByRole('button', { name: 'spring 태그 붙이기' })).toBeNull();
    expect(screen.getByRole('button', { name: 'hibernate 태그 붙이기' })).toBeInTheDocument();
    expect(api.suggest).toHaveBeenCalledTimes(1);
  });

  it('잘렸으면 앞부분 안내를 붙인다', async () => {
    const user = userEvent.setup();
    render(<Harness api={apiOf({ suggest: vi.fn(async () => response({ truncated: true })) })} />);
    await pressSuggest(user);
    expect(
      await screen.findByText('본문 앞부분을 보고 추천했어요 · AI 제안이에요'),
    ).toBeInTheDocument();
  });

  it('처음에는 상태의 남은 횟수를 보인다', async () => {
    render(<Harness api={apiOf()} />);
    expect(await screen.findByText('오늘 남은 추천 20회')).toBeInTheDocument();
  });

  it('요청 중에는 버튼을 끄고 "추천 중…"', async () => {
    const user = userEvent.setup();
    let finish: (r: TagSuggestResponse) => void = () => {};
    const api = apiOf({
      suggest: vi.fn(() => new Promise<TagSuggestResponse>((resolve) => (finish = resolve))),
    });
    render(<Harness api={api} />);
    await pressSuggest(user);
    const busy = await screen.findByRole('button', { name: '추천 중…' });
    expect(busy).toBeDisabled();
    await act(async () => finish(response()));
    expect(await screen.findByRole('button', { name: '다시 추천' })).toBeEnabled();
  });

  it('예상 공급자가 자체 AI면 기다림 문구를 보이고 버튼을 끈다', async () => {
    const user = userEvent.setup();
    let finish: (r: TagSuggestResponse) => void = () => {};
    const api = apiOf({
      getStatus: vi.fn(async () => ({ ...STATUS, provider: 'OLLAMA' as const })),
      suggest: vi.fn(() => new Promise<TagSuggestResponse>((resolve) => (finish = resolve))),
    });
    render(<Harness api={api} />);
    await pressSuggest(user);
    const waiting = await screen.findByRole('button', {
      name: '자체 AI로 추천 중이라 조금 걸려요',
    });
    expect(waiting).toBeDisabled();
    await act(async () => finish(response({ provider: 'OLLAMA' })));
  });

  it('태그가 10개면 버튼을 끄고 "태그를 더 붙일 수 없어요"', async () => {
    const ten = Array.from({ length: 10 }, (_, i) => `t${i}`);
    render(<Harness api={apiOf()} initial={ten} />);
    expect(await screen.findByRole('button', { name: 'AI 태그 추천' })).toBeDisabled();
    expect(screen.getByText('태그를 더 붙일 수 없어요')).toBeInTheDocument();
  });

  it.each([
    [
      '422',
      apiError(422, 'CONTENT_TOO_SHORT', { minChars: 100, length: 42 }),
      '글을 조금 더 쓴 뒤 추천받아 보세요',
    ],
    [
      '503 FAILED',
      apiError(503, 'AI_UNAVAILABLE', { reason: 'FAILED' }),
      '지금은 추천할 수 없어요',
    ],
    [
      '503 DISABLED',
      apiError(503, 'AI_UNAVAILABLE', { reason: 'DISABLED' }),
      '지금은 추천할 수 없어요',
    ],
    [
      '503 STORE_UNAVAILABLE',
      apiError(503, 'AI_UNAVAILABLE', { reason: 'STORE_UNAVAILABLE' }),
      '지금은 추천할 수 없어요',
    ],
    ['503 BUSY', apiError(503, 'AI_UNAVAILABLE', { reason: 'BUSY' }), '잠시 후 다시 시도해 주세요'],
    [
      '429',
      apiError(429, 'AI_DAILY_LIMIT', { resetAt: '2026-10-08T15:00:00Z' }),
      '오늘 추천을 모두 썼어요. 내일 다시 써 보세요',
    ],
  ])('%s 문구', async (_name, error, text) => {
    const user = userEvent.setup();
    render(<Harness api={apiOf({ suggest: vi.fn(async () => Promise.reject(error)) })} />);
    await pressSuggest(user);
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 태그 추천' })).toBeEnabled();
  });

  it('하루 한도면 남은 횟수를 0으로 보인다', async () => {
    const user = userEvent.setup();
    render(
      <Harness
        api={apiOf({ suggest: vi.fn(async () => Promise.reject(apiError(429, 'AI_DAILY_LIMIT'))) })}
      />,
    );
    await pressSuggest(user);
    expect(await screen.findByText('오늘 남은 추천 0회')).toBeInTheDocument();
  });

  it('빈 결과면 "추천할 태그를 찾지 못했어요"', async () => {
    const user = userEvent.setup();
    render(<Harness api={apiOf({ suggest: vi.fn(async () => response({ tags: [] })) })} />);
    await pressSuggest(user);
    expect(await screen.findByText('추천할 태그를 찾지 못했어요')).toBeInTheDocument();
    expect(screen.queryByText('AI 제안이에요')).toBeNull();
  });

  it('칩은 글자로만 그린다', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <Harness api={apiOf({ suggest: vi.fn(async () => response({ tags: ['<b>x</b>'] })) })} />,
    );
    await pressSuggest(user);
    expect(await screen.findByRole('button', { name: '<b>x</b> 태그 붙이기' })).toHaveTextContent(
      '+ <b>x</b>',
    );
    expect(container.querySelector('.ai-suggest b')).toBeNull();
  });

  // ---- US3 T043 ----

  it('결과가 있으면 [다시 추천]이 refresh: true로 보낸다', async () => {
    const user = userEvent.setup();
    const api = apiOf();
    render(<Harness api={api} />);
    await pressSuggest(user);
    await user.click(await screen.findByRole('button', { name: '다시 추천' }));
    await waitFor(() => expect(api.suggest).toHaveBeenCalledTimes(2));
    expect(vi.mocked(api.suggest).mock.calls[1][1]).toMatchObject({ refresh: true });
  });

  it('재사용 응답이면 남은 횟수가 그대로다', async () => {
    const user = userEvent.setup();
    const api = apiOf({
      suggest: vi
        .fn<SuggestApi['suggest']>()
        .mockResolvedValueOnce(response({ remainingToday: 19 }))
        .mockResolvedValueOnce(response({ cached: true, remainingToday: 19 })),
    });
    render(<Harness api={api} />);
    await pressSuggest(user);
    expect(await screen.findByText('오늘 남은 추천 19회')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 추천' }));
    await waitFor(() => expect(api.suggest).toHaveBeenCalledTimes(2));
    expect(screen.getByText('오늘 남은 추천 19회')).toBeInTheDocument();
  });

  // ---- 인증 전 회원 ----

  it('상태가 403이면 서버 문구를 보이고 버튼을 끈다', async () => {
    const api = apiOf({
      getStatus: vi.fn(async () =>
        Promise.reject(new ApiError(403, 'EMAIL_NOT_VERIFIED', '이메일 인증 후 이용할 수 있어요')),
      ),
    });
    render(<Harness api={api} />);
    expect(await screen.findByText('이메일 인증 후 이용할 수 있어요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 태그 추천' })).toBeDisabled();
  });
});
