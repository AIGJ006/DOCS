/**
 * 트렌딩·검색 API (012 contracts/openapi.yaml). 요청은 공용 `client.ts`를 쓴다.
 *
 * - `cursor`는 이전 응답의 `nextCursor`를 그대로 보낸다(해석하지 않는다).
 * - 검색 요청은 404 화면 전환을 하지 않는다 — 블로그 안 검색의 404는 블로그 머리말 요청이 맡는다.
 */
import { apiGet } from './client';
import type {
  PeopleSearchResult,
  PostSearchPage,
  SearchSort,
  TrendingPage,
} from './types/discovery';

function query(params: Record<string, string | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      search.set(key, value);
    }
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

/** 홈 트렌딩 탭. 보던 순위가 만료되면 410 `SNAPSHOT_EXPIRED`. */
export function getTrending(cursor?: string | null): Promise<TrendingPage> {
  return apiGet<TrendingPage>(`/api/posts/trending${query({ cursor })}`);
}

export interface SearchPostsParams {
  q: string;
  sort?: SearchSort;
  cursor?: string | null;
  /** 블로그 안 검색이면 블로그 주소 */
  blog?: string | null;
}

/** 글 검색. 남는 단어가 없으면 400 `SEARCH_QUERY_TOO_SHORT`, 요청 과다면 429 `TOO_MANY_REQUESTS`. */
export function searchPosts({ q, sort, cursor, blog }: SearchPostsParams): Promise<PostSearchPage> {
  return apiGet<PostSearchPage>(
    `/api/search/posts${query({ q, sort: sort === 'relevance' ? null : sort, cursor, blog })}`,
    { notFoundScreen: false },
  );
}

/** 사람 검색 (최대 20명, 커서 없음). */
export function searchPeople(q: string): Promise<PeopleSearchResult> {
  return apiGet<PeopleSearchResult>(`/api/search/people${query({ q })}`);
}
