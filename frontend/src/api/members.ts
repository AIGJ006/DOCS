/**
 * 회원·개인 블로그 API (005 contracts/openapi.yaml). 요청은 공용 `client.ts`(001 T042)를 쓴다.
 * 목록 크기 `size`는 보내지 않는다 — 서버가 설정값으로 고정한다(10 §4-2).
 */
import { apiGet } from './client';
import type { BlogHeader, PostCardPage } from './types/reading';

/** 개인 블로그 머리말. 없는 주소·탈퇴 유예·익명 처리는 404 `NOT_FOUND`. */
export function getBlogHeader(handle: string): Promise<BlogHeader> {
  return apiGet<BlogHeader>(`/api/members/${encodeURIComponent(handle)}`);
}

/**
 * 개인 블로그 글 목록. `cursor`는 이전 응답의 `nextCursor`를 그대로 넘긴다.
 * `tag`(008)가 있으면 그 태그의 글만 — 정규화된 이름만 받는다(쿼리 값이라 `+`도 `%2B`로 인코딩).
 */
export function listBlogPosts(
  handle: string,
  cursor?: string | null,
  tag?: string | null,
  category?: string | null,
): Promise<PostCardPage> {
  const query: string[] = [];
  // 017: 카테고리 필터가 있으면 태그는 보내지 않는다 (둘은 함께 쓰지 않음)
  if (category) {
    query.push(`category=${encodeURIComponent(category)}`);
  } else if (tag) {
    query.push(`tag=${encodeURIComponent(tag)}`);
  }
  if (cursor) {
    query.push(`cursor=${encodeURIComponent(cursor)}`);
  }
  const path = `/api/members/${encodeURIComponent(handle)}/posts`;
  return apiGet<PostCardPage>(query.length > 0 ? `${path}?${query.join('&')}` : path);
}
