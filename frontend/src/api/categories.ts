/**
 * 카테고리 API (017 contracts/openapi.yaml). 요청은 공용 `client.ts`를 쓴다.
 */
import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from './client';

/** 카테고리 나무 한 칸. 하위의 `children`은 항상 빈 목록. 최상위 `postCount`는 하위 글 포함. */
export interface CategoryNode {
  id: number;
  name: string;
  postCount: number;
  children: CategoryNode[];
}

/** 내 카테고리 (관리 화면). `postCount`는 내 글 전부(휴지통 제외). */
export interface MyCategories {
  maxCount: number;
  items: CategoryNode[];
}

/** 블로그 카테고리 목록. `totalCount`는 "분류 전체보기" 글 수. */
export interface BlogCategories {
  totalCount: number;
  items: CategoryNode[];
}

export interface CategoryItem {
  id: number;
  name: string;
  parentId: number | null;
  position: number;
}

/** 글 상세의 카테고리 경로 (분류 없음이면 null). */
export interface PostCategoryPath {
  id: number;
  name: string;
  parent: { id: number; name: string } | null;
}

export function listMyCategories(): Promise<MyCategories> {
  return apiGet<MyCategories>('/api/me/categories');
}

export function createCategory(name: string, parentId: number | null): Promise<CategoryItem> {
  return apiPost<CategoryItem>('/api/me/categories', { name, parentId });
}

/** 보낸 칸만 바꾼다. `parentId: null`은 최상위로. */
export function updateCategory(
  id: number,
  change: { name?: string; parentId?: number | null },
): Promise<CategoryItem> {
  return apiPatch<CategoryItem>(`/api/me/categories/${id}`, change, { notFoundScreen: false });
}

export function deleteCategory(id: number): Promise<void> {
  return apiDelete<void>(`/api/me/categories/${id}`, undefined, { notFoundScreen: false });
}

/** 같은 상위 안 순서 저장 — 그 상위의 하위 번호 전체를 새 순서로. */
export function reorderCategories(parentId: number | null, ids: number[]): Promise<MyCategories> {
  return apiPut<MyCategories>('/api/me/categories/order', { parentId, ids });
}

export function getPostCategory(postId: number): Promise<{ categoryId: number | null }> {
  return apiGet<{ categoryId: number | null }>(`/api/posts/${postId}/category`, {
    notFoundScreen: false,
  });
}

export function setPostCategory(
  postId: number,
  categoryId: number | null,
): Promise<{ categoryId: number | null }> {
  return apiPut<{ categoryId: number | null }>(
    `/api/posts/${postId}/category`,
    { categoryId },
    { notFoundScreen: false },
  );
}

/** 블로그 카테고리 목록. 404 화면 전환은 머리말 요청이 맡는다. */
export function getBlogCategories(handle: string): Promise<BlogCategories> {
  return apiGet<BlogCategories>(`/api/members/${encodeURIComponent(handle)}/categories`, {
    notFoundScreen: false,
  });
}
