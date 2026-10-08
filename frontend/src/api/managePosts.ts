/**
 * 내 글 관리·휴지통 API (006, contracts/openapi.yaml). 001 `client.ts`(CSRF 헤더·오류 본문) 위에 둔다.
 *
 * 상태를 바꾸는 요청(삭제·복구·영구 삭제)은 `notFoundScreen: false`로 보낸다 — 이미 처리된 글의 404는 공통 404
 * 화면으로 바꾸지 않고 그 줄 아래에 이유를 보인다(FR-013, research R24). 상세 화면의 [삭제]도 같은 함수를 쓰며
 * 404면 상세를 다시 불러 공통 404 화면으로 간다.
 */
import { apiDelete, apiGet, apiPost } from './client';
import type { PostStatus, Visibility } from './posts';

export type ManageTab = 'drafts' | 'published' | 'trash';
export type VisibilityFilter = 'public' | 'private';

/** 관리 목록 한 줄. 본문 필드는 없다(FR-012). */
export interface ManagePostItem {
  id: number;
  /** 빈 문자열이면 화면이 "(제목 없음)" */
  title: string;
  status: PostStatus;
  visibility: Visibility;
  /** 작업본(post_draft) 있음 = 수정 중 */
  editing: boolean;
  /** 관리자 숨김 */
  hidden: boolean;
  updatedAt: string;
  publishedAt: string | null;
  editedAt: string | null;
  /** 휴지통 탭에서만 값 */
  deletedAt: string | null;
  /** 휴지통 탭에서만 값 (deletedAt + 30일) */
  purgeAt: string | null;
  viewCount: number;
  likeCount: number;
  commentCount: number;
}

export interface ManageCounts {
  drafts: number;
  published: number;
  trash: number;
}

export interface ManagePostPage {
  items: ManagePostItem[];
  /** 다음 페이지가 없으면 null ([더 보기] 숨김) */
  nextCursor: string | null;
  /** 첫 요청(cursor 없음)에만 값, [더 보기]에서는 null */
  counts: ManageCounts | null;
}

export type TrashResult = { trashed: true; purgeAt: string } | { purged: true };

export interface RestoreResult {
  restored: true;
  status: PostStatus;
  visibility: Visibility;
}

export interface ListMyPostsParams {
  tab: ManageTab;
  visibility?: VisibilityFilter | null;
  cursor?: string | null;
}

const ROW_ACTION = { notFoundScreen: false } as const;

/** `GET /api/me/posts` — 페이지 크기(`size`)는 보내지 않는다(서버가 20개로 고정, FR-006). */
export function listMyPosts(
  { tab, visibility, cursor }: ListMyPostsParams,
  signal?: AbortSignal,
): Promise<ManagePostPage> {
  const query = new URLSearchParams({ tab });
  if (tab === 'published' && visibility) {
    query.set('visibility', visibility);
  }
  if (cursor) {
    query.set('cursor', cursor);
  }
  return apiGet<ManagePostPage>(`/api/me/posts?${query.toString()}`, { signal });
}

/** `DELETE /api/posts/{postId}` — 휴지통으로 (빈 임시글은 바로 완전 삭제). */
export function trashPost(postId: number): Promise<TrashResult> {
  return apiDelete<TrashResult>(`/api/posts/${postId}`, undefined, ROW_ACTION);
}

/** `POST /api/posts/{postId}/restore` — 확인창 없이 복구(FR-027). */
export function restorePost(postId: number): Promise<RestoreResult> {
  return apiPost<RestoreResult>(`/api/posts/${postId}/restore`, undefined, ROW_ACTION);
}

/** `DELETE /api/posts/{postId}/permanent` — 휴지통 글 영구 삭제(FR-029). */
export function purgePost(postId: number): Promise<{ purged: true }> {
  return apiDelete<{ purged: true }>(`/api/posts/${postId}/permanent`, undefined, ROW_ACTION);
}

/**
 * [변경 취소] — 002 `DELETE /api/posts/{postId}/working-copy`를 관리 화면 줄 처리용으로 부른다.
 * 002 `discardWorkingCopy`와 같은 요청이며, 404를 공통 404 화면 대신 줄 아래 이유로 보이려고 `notFoundScreen: false`만 다르다.
 */
export function discardEditing(postId: number): Promise<void> {
  return apiDelete<void>(`/api/posts/${postId}/working-copy`, undefined, ROW_ACTION);
}
