/**
 * 태그 API (008 contracts/openapi.yaml). 요청은 공용 `client.ts`를 쓴다.
 *
 * - 경로의 태그 이름은 `tagPathSegment`(= `encodeURIComponent` + `%2B`→`+`)로 만든다. API는 정규화된 이름만 받는다 —
 *   다르면 404.
 * - 목록 크기(`size`·`limit`)는 보내지 않는다(서버 설정값 고정).
 */
import { apiGet, type RequestOptions } from './client';
import type { PostCardPage } from './types/reading';
import type { BlogTags, TagIndex, TagSuggestion, TagSummary } from './types/tags';
import { tagPathSegment } from '../features/tag/tagPath';

/** 태그 페이지 머리말 "#spring-boot · 공개 글 12". 형식이 틀리면 404 `NOT_FOUND`. */
export function getTagSummary(name: string): Promise<TagSummary> {
  return apiGet<TagSummary>(`/api/tags/${tagPathSegment(name)}/summary`);
}

/** 태그별 글 목록. `cursor`는 이전 응답의 `nextCursor` 그대로. */
export function listTagPosts(name: string, cursor?: string | null): Promise<PostCardPage> {
  const path = `/api/tags/${tagPathSegment(name)}/posts`;
  return apiGet<PostCardPage>(cursor ? `${path}?cursor=${encodeURIComponent(cursor)}` : path);
}

/** 전체 태그 목록 (공개 글 수 많은 순 상위 100). */
export function listTopTags(): Promise<TagIndex> {
  return apiGet<TagIndex>('/api/tags');
}

/**
 * 자동완성 (로그인한 회원만). 실패·429는 화면이 조용히 무시한다 — 404 화면 전환을 하지 않는다.
 */
export function suggestTags(q: string, signal?: AbortSignal): Promise<TagSuggestion[]> {
  const options: RequestOptions = { signal, notFoundScreen: false };
  return apiGet<TagSuggestion[]>(`/api/tags/suggest?q=${encodeURIComponent(q)}`, options);
}

/** 블로그 태그 줄. 없는 블로그는 404. */
export function getBlogTags(handle: string): Promise<BlogTags> {
  return apiGet<BlogTags>(`/api/members/${encodeURIComponent(handle)}/tags`);
}
