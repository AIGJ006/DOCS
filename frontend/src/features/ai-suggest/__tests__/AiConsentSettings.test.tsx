import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import type { AiConsent } from '../../../api/types/tagSuggestions';
import AiConsentSettings, { type ConsentApi } from '../AiConsentSettings';

const NONE: AiConsent = {
  agreed: false,
  version: null,
  currentVersion: '2026-10-08',
  agreedAt: null,
};
const AGREED: AiConsent = {
  agreed: true,
  version: '2026-10-08',
  currentVersion: '2026-10-08',
  agreedAt: '2026-10-08T03:12:00Z',
};
const OUTDATED: AiConsent = {
  agreed: false,
  version: '2026-09-01',
  currentVersion: '2026-10-08',
  agreedAt: '2026-09-02T01:00:00Z',
};

function apiOf(initial: AiConsent, over: Partial<ConsentApi> = {}): ConsentApi {
  return {
    getAiConsent: vi.fn(async () => initial),
    revokeAi: vi.fn(async () => NONE),
    ...over,
  };
}

describe('AiConsentSettings', () => {
  it('동의함: 날짜와 [동의 취소] → DELETE 뒤 "동의하지 않았어요"', async () => {
    const user = userEvent.setup();
    const api = apiOf(AGREED);
    render(<AiConsentSettings api={api} />);
    expect(await screen.findByText(/2026\.10\.08에 동의했어요/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '동의 취소' }));
    expect(api.revokeAi).toHaveBeenCalledTimes(1);
    expect(await screen.findByText(/동의하지 않았어요/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '동의 취소' })).toBeNull();
  });

  it('동의 안 함: 안내 문구, [동의 취소] 없음', async () => {
    render(<AiConsentSettings api={apiOf(NONE)} />);
    expect(await screen.findByText(/동의하지 않았어요/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '동의 취소' })).toBeNull();
  });

  it('옛 버전: "다시 동의가 필요해요"와 [동의 취소]', async () => {
    render(<AiConsentSettings api={apiOf(OUTDATED)} />);
    expect(await screen.findByText(/다시 동의가 필요해요/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '동의 취소' })).toBeInTheDocument();
  });

  it('취소가 실패하면 상태를 그대로 두고 "잠시 후 다시 시도해 주세요"', async () => {
    const user = userEvent.setup();
    const api = apiOf(AGREED, {
      revokeAi: vi.fn(async () => {
        throw new ApiError(500, 'INTERNAL_ERROR', '오류', []);
      }),
    });
    render(<AiConsentSettings api={api} />);
    await user.click(await screen.findByRole('button', { name: '동의 취소' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(screen.getByText(/2026\.10\.08에 동의했어요/)).toBeInTheDocument();
  });

  it('불러오지 못하면 칸 안에서만 알린다', async () => {
    const api = apiOf(NONE, {
      getAiConsent: vi.fn(async () => {
        throw new ApiError(503, 'SERVICE_UNAVAILABLE', '오류', []);
      }),
    });
    render(<AiConsentSettings api={api} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('AI 동의 상태를 불러오지 못했어요');
  });
});
