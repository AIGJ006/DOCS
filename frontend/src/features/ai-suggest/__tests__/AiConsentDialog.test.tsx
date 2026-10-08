import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import type { TagSuggestResponse, TagSuggestStatus } from '../../../api/types/tagSuggestions';
import AiTagSuggest from '../AiTagSuggest';
import { AI_CONSENT_LINES, AI_CONSENT_VERSION } from '../aiConsentText';
import type { SuggestApi } from '../useTagSuggest';

const STATUS: TagSuggestStatus = {
  available: true,
  consentRequired: true,
  consentVersion: '2026-10-08',
  provider: 'GEMINI',
  remainingToday: 20,
};

const RESPONSE: TagSuggestResponse = {
  tags: ['spring', 'hibernate'],
  provider: 'GEMINI',
  cached: false,
  truncated: false,
  remainingToday: 19,
};

function consentError() {
  return new ApiError(409, 'AI_CONSENT_REQUIRED', '서버 문구', [], { version: '2026-10-08' });
}

function apiOf(over: Partial<SuggestApi> = {}): SuggestApi {
  return {
    getStatus: vi.fn(async () => STATUS),
    suggest: vi.fn(async () => RESPONSE),
    agreeAi: vi.fn(async () => ({})),
    ...over,
  };
}

function Harness({ api }: { api: SuggestApi }) {
  const [tags, setTags] = useState<string[]>([]);
  return (
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
  );
}

async function openDialog(api: SuggestApi) {
  const user = userEvent.setup();
  render(<Harness api={api} />);
  await user.click(await screen.findByRole('button', { name: 'AI 태그 추천' }));
  const dialog = await screen.findByRole('dialog');
  return { user, dialog };
}

describe('AiConsentDialog', () => {
  it('버전 상수는 서버 기본값과 같은 2026-10-08', () => {
    expect(AI_CONSENT_VERSION).toBe('2026-10-08');
    expect(AI_CONSENT_LINES).toHaveLength(5);
  });

  it('동의가 필요하면 창을 열고 문구 다섯 줄과 버전을 보이며 처음 초점은 [동의하고 추천받기]', async () => {
    const api = apiOf();
    const { dialog } = await openDialog(api);
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    for (const line of AI_CONSENT_LINES) {
      expect(screen.getByText(line)).toBeInTheDocument();
    }
    expect(screen.getByText(/2026-10-08/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '동의하고 추천받기' })).toHaveFocus();
    expect(api.suggest).not.toHaveBeenCalled();
  });

  it('추천 요청이 409면 창을 연다', async () => {
    const api = apiOf({
      getStatus: vi.fn(async () => ({ ...STATUS, consentRequired: false })),
      suggest: vi.fn().mockRejectedValueOnce(consentError()).mockResolvedValue(RESPONSE),
    });
    await openDialog(api);
    expect(api.suggest).toHaveBeenCalledTimes(1);
  });

  it('Tab으로 창 밖에 나가지 않는다', async () => {
    const api = apiOf();
    const { user } = await openDialog(api);
    const agree = screen.getByRole('button', { name: '동의하고 추천받기' });
    const cancel = screen.getByRole('button', { name: '취소' });
    await user.tab();
    expect(cancel).toHaveFocus();
    await user.tab({ shift: true });
    expect(agree).toHaveFocus();
  });

  it('Esc는 아무것도 보내지 않고 닫는다', async () => {
    const api = apiOf();
    const { user } = await openDialog(api);
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(api.agreeAi).not.toHaveBeenCalled();
    expect(api.suggest).not.toHaveBeenCalled();
  });

  it('[취소]는 아무것도 보내지 않는다', async () => {
    const api = apiOf();
    const { user } = await openDialog(api);
    await user.click(screen.getByRole('button', { name: '취소' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(api.agreeAi).not.toHaveBeenCalled();
    expect(api.suggest).not.toHaveBeenCalled();
  });

  it('[동의하고 추천받기]는 PUT {version} 뒤 원래 추천 요청을 한 번 다시 보낸다', async () => {
    const api = apiOf({
      getStatus: vi.fn(async () => ({ ...STATUS, consentRequired: false })),
      suggest: vi.fn().mockRejectedValueOnce(consentError()).mockResolvedValue(RESPONSE),
    });
    const { user } = await openDialog(api);
    await user.click(screen.getByRole('button', { name: '동의하고 추천받기' }));
    expect(await screen.findByRole('button', { name: 'spring 태그 붙이기' })).toBeInTheDocument();
    expect(api.agreeAi).toHaveBeenCalledWith('2026-10-08');
    expect(api.suggest).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('PUT 400이면 "동의 문구가 바뀌었어요…"를 보이고 추천은 보내지 않는다', async () => {
    const api = apiOf({
      agreeAi: vi.fn(async () => {
        throw new ApiError(400, 'VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
          { field: 'version', code: 'AGREEMENT_VERSION_MISMATCH', message: '약관이 바뀌었어요' },
        ]);
      }),
    });
    const { user } = await openDialog(api);
    await user.click(screen.getByRole('button', { name: '동의하고 추천받기' }));
    expect(
      await screen.findByText('동의 문구가 바뀌었어요. 새로 고친 뒤 다시 시도해 주세요'),
    ).toBeInTheDocument();
    expect(api.suggest).not.toHaveBeenCalled();
  });
});
