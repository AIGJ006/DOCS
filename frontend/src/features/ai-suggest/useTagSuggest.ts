import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { agreeAi, getStatus, suggest } from '../../api/tagSuggestions';
import type {
  Provider,
  TagSuggestRequest,
  TagSuggestResponse,
  TagSuggestStatus,
} from '../../api/types/tagSuggestions';
import { AI_CONSENT_VERSION } from './aiConsentText';
import { AI_MESSAGES } from './messages';

/**
 * AI 태그 추천 상태 (013 T031·T036·T043). 상태 조회 → (동의 창) → 요청 → 결과·오류 문구.
 *
 * - 발행 창이 열릴 때 상태 API를 부른다. 실패(404·Redis 장애 등)면 `available = false`로 보고 영역을 그리지 않는다.
 *   403(인증 전·정지)은 영역을 두고 서버 문구를 보인다.
 * - 409 `AI_CONSENT_REQUIRED`(또는 상태의 `consentRequired`)면 동의 창을 연다. 동의하면 `PUT {version}` 뒤 원래 요청을 한 번 다시 보낸다.
 * - 오류 문구: 422·429·503(`details.reason` BUSY는 "잠시 후", 그 밖은 "지금은 추천할 수 없어요").
 * - 서버는 태그를 저장하지 않는다 — 결과는 화면에만 있다.
 */
export interface SuggestApi {
  getStatus: (postId: number) => Promise<TagSuggestStatus>;
  suggest: (postId: number, body: TagSuggestRequest) => Promise<TagSuggestResponse>;
  agreeAi: (version: string) => Promise<unknown>;
}

const DEFAULT_API: SuggestApi = { getStatus, suggest, agreeAi };

export type SuggestPhase = 'idle' | 'loading' | 'consent' | 'agreeing';

export interface TagSuggestState {
  /** 상태 조회 전이면 null */
  status: TagSuggestStatus | null;
  /** 403 등으로 쓸 수 없는 이유 (영역은 보인다) */
  blocked: string | null;
  phase: SuggestPhase;
  result: TagSuggestResponse | null;
  message: string | null;
  /** 동의 창 안의 오류 문구 */
  consentError: string | null;
  remainingToday: number | null;
  /** 요청 중 예상 공급자 (대기 문구) */
  pendingProvider: Provider | null;
}

const INITIAL: TagSuggestState = {
  status: null,
  blocked: null,
  phase: 'idle',
  result: null,
  message: null,
  consentError: null,
  remainingToday: null,
  pendingProvider: null,
};

export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return AI_MESSAGES.unavailable;
  }
  switch (error.code) {
    case 'CONTENT_TOO_SHORT':
      return AI_MESSAGES.tooShort;
    case 'AI_DAILY_LIMIT':
      return AI_MESSAGES.dailyLimit;
    case 'AI_UNAVAILABLE':
      return error.details?.reason === 'BUSY' ? AI_MESSAGES.busy : AI_MESSAGES.unavailable;
    default:
      return error.status === 403 && error.message ? error.message : AI_MESSAGES.unavailable;
  }
}

export function useTagSuggest(postId: number, api: SuggestApi = DEFAULT_API) {
  const [state, setState] = useState<TagSuggestState>(INITIAL);
  const pending = useRef<TagSuggestRequest | null>(null);
  const inFlight = useRef(false);

  useEffect(() => {
    let alive = true;
    api.getStatus(postId).then(
      (status) => {
        if (alive) {
          setState((s) => ({ ...s, status, remainingToday: status.remainingToday }));
        }
      },
      (error: unknown) => {
        if (!alive) {
          return;
        }
        if (error instanceof ApiError && error.status === 403) {
          setState((s) => ({
            ...s,
            blocked: error.message,
            status: {
              available: true,
              consentRequired: false,
              consentVersion: AI_CONSENT_VERSION,
              provider: null,
              remainingToday: 0,
            },
          }));
          return;
        }
        setState((s) => ({
          ...s,
          status: {
            available: false,
            consentRequired: false,
            consentVersion: AI_CONSENT_VERSION,
            provider: null,
            remainingToday: 0,
          },
        }));
      },
    );
    return () => {
      alive = false;
    };
  }, [postId, api]);

  /** 추천 요청 하나 (동의 확인 뒤). */
  const send = useCallback(
    async (body: TagSuggestRequest) => {
      try {
        const result = await api.suggest(postId, body);
        setState((s) => ({
          ...s,
          phase: 'idle',
          result,
          remainingToday: result.remainingToday,
          message: result.tags.length === 0 ? AI_MESSAGES.empty : null,
          pendingProvider: null,
          status: s.status ? { ...s.status, consentRequired: false } : s.status,
        }));
      } catch (error) {
        if (error instanceof ApiError && error.code === 'AI_CONSENT_REQUIRED') {
          pending.current = body;
          setState((s) => ({ ...s, phase: 'consent', consentError: null, pendingProvider: null }));
          return;
        }
        setState((s) => ({
          ...s,
          phase: 'idle',
          message: errorMessage(error),
          pendingProvider: null,
          remainingToday:
            error instanceof ApiError && error.code === 'AI_DAILY_LIMIT' ? 0 : s.remainingToday,
        }));
      }
    },
    [api, postId],
  );

  /**
   * [AI 태그 추천]·[다시 추천]. 누르기 직전에 상태를 다시 보고(대기 문구·동의 필요 여부), 동의가 필요하면 창부터 연다.
   * 응답 전에는 다시 누를 수 없다.
   */
  const request = useCallback(
    async (body: TagSuggestRequest) => {
      if (inFlight.current) {
        return;
      }
      inFlight.current = true;
      setState((s) => ({
        ...s,
        phase: 'loading',
        message: null,
        pendingProvider: s.status?.provider ?? null,
      }));
      try {
        let status: TagSuggestStatus | null = null;
        try {
          status = await api.getStatus(postId);
        } catch {
          // 상태를 못 읽어도 추천 요청이 이유를 알려 준다
        }
        if (status) {
          const fresh = status;
          setState((s) => ({
            ...s,
            status: fresh,
            pendingProvider: fresh.provider,
            remainingToday: s.result ? s.remainingToday : fresh.remainingToday,
          }));
          if (!fresh.available) {
            setState((s) => ({ ...s, phase: 'idle', message: AI_MESSAGES.unavailable }));
            return;
          }
          if (fresh.consentRequired) {
            pending.current = body;
            setState((s) => ({ ...s, phase: 'consent', consentError: null }));
            return;
          }
        }
        await send(body);
      } finally {
        inFlight.current = false;
      }
    },
    [api, postId, send],
  );

  /** 동의 창 [동의하고 추천받기]: `PUT {version}` 뒤 원래 요청을 한 번 다시 보낸다. */
  const agree = useCallback(async () => {
    setState((s) => ({ ...s, phase: 'agreeing', consentError: null }));
    try {
      await api.agreeAi(AI_CONSENT_VERSION);
    } catch (error) {
      setState((s) => ({
        ...s,
        phase: 'consent',
        consentError:
          error instanceof ApiError && error.status === 400
            ? AI_MESSAGES.consentChanged
            : AI_MESSAGES.unavailable,
      }));
      return;
    }
    const body = pending.current;
    pending.current = null;
    setState((s) => ({
      ...s,
      phase: body ? 'loading' : 'idle',
      pendingProvider: body ? (s.status?.provider ?? null) : null,
      status: s.status ? { ...s.status, consentRequired: false } : s.status,
    }));
    if (body) {
      inFlight.current = true;
      try {
        await send(body);
      } finally {
        inFlight.current = false;
      }
    }
  }, [api, send]);

  /** 동의 창 [취소]·Esc: 아무것도 보내지 않는다. */
  const cancelConsent = useCallback(() => {
    pending.current = null;
    setState((s) => ({ ...s, phase: 'idle', consentError: null }));
  }, []);

  return { state, request, agree, cancelConsent };
}
