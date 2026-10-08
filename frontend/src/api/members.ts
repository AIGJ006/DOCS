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

/** 개인 블로그 글 목록. `cursor`는 이전 응답의 `nextCursor`를 그대로 넘긴다. */
export function listBlogPosts(handle: string, cursor?: string | null): Promise<PostCardPage> {
  const path = `/api/members/${encodeURIComponent(handle)}/posts`;
  return apiGet<PostCardPage>(cursor ? `${path}?cursor=${encodeURIComponent(cursor)}` : path);
}
