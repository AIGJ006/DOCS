/**
 * AI 태그 추천·AI 동의 API (013 contracts/openapi.yaml). 요청은 공용 `client.ts`를 쓴다.
 *
 * 실패해도 화면 전체를 바꾸지 않는다 — 404 화면 전환을 하지 않고(`notFoundScreen: false`) 발행 창 안에서 문구로만 알린다.
 */
import { apiDelete, apiGet, apiPost, apiPut, type RequestOptions } from './client';
import type {
  AiConsent,
  TagSuggestRequest,
  TagSuggestResponse,
  TagSuggestStatus,
} from './types/tagSuggestions';

const QUIET: RequestOptions = { notFoundScreen: false };

/** 추천 버튼 상태 (발행 창이 열릴 때와 누르기 직전). */
export function getStatus(postId: number): Promise<TagSuggestStatus> {
  return apiGet<TagSuggestStatus>(`/api/posts/${postId}/tag-suggestions/status`, QUIET);
}

/** 태그 추천 받기 (에디터의 지금 제목·본문, 저장 안 된 것 포함). */
export function suggest(postId: number, body: TagSuggestRequest): Promise<TagSuggestResponse> {
  return apiPost<TagSuggestResponse>(`/api/posts/${postId}/tag-suggestions`, body, QUIET);
}

/** AI 동의 상태 (설정 "AI 동의" 칸). */
export function getAiConsent(): Promise<AiConsent> {
  return apiGet<AiConsent>('/api/me/agreements/ai', QUIET);
}

/** 화면이 보여 준 동의 문구 버전으로 동의한다. 현재와 다르면 400. */
export function agreeAi(version: string): Promise<AiConsent> {
  return apiPut<AiConsent>('/api/me/agreements/ai', { version }, QUIET);
}

/** 동의 취소. 없어도 200. */
export function revokeAi(): Promise<AiConsent> {
  return apiDelete<AiConsent>('/api/me/agreements/ai', undefined, QUIET);
}
