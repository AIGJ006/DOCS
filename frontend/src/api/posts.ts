/**
 * 글 작성·임시 저장·발행 API (002, contracts/openapi.yaml). 001 `client.ts`(CSRF 헤더·오류 본문) 위에 둔다.
 * 004의 공개 범위 변경 함수 등 다른 기능의 함수는 이 파일에 더한다.
 */
import { apiDelete, apiGet, apiPost, apiPut, CSRF_COOKIE, CSRF_HEADER } from './client';
import type { PostCardPage, PostDetail } from './types/reading';

export type Visibility = 'PUBLIC' | 'PRIVATE';
export type PostStatus = 'DRAFT' | 'PUBLISHED';

/** 에디터에 여는 내용 — 현재 버전 = max(Redis, post_draft, post)의 출처. */
export interface WorkingCopy {
  postId: number;
  status: PostStatus;
  editing: boolean;
  title: string;
  contentMd: string;
  version: number;
  savedAt: string;
  visibility: Visibility;
  tags: string[];
  url: string | null;
}

export interface CreatePostRequest {
  title?: string;
  contentMd?: string;
}

export interface SaveRequest {
  title: string;
  contentMd: string;
  baseVersion: number;
}

export interface SaveResponse {
  version: number;
  savedAt: string;
}

/** 409 VERSION_CONFLICT의 `details.server`. */
export interface ServerCopy {
  title: string;
  contentMd: string;
  version: number;
  savedAt: string;
}

export interface PublishRequest {
  title: string;
  contentMd: string;
  tags: string[];
  visibility: Visibility;
  baseVersion: number;
}

export interface PublishResponse {
  url: string;
  publishedAt: string;
  firstPublicAt: string | null;
  editedAt: string | null;
  version: number;
}

export interface PreviewResponse {
  html: string;
}

export const IDEMPOTENCY_HEADER = 'Idempotency-Key';

export function createPost(body?: CreatePostRequest): Promise<WorkingCopy> {
  return apiPost<WorkingCopy>('/api/posts', body ?? {});
}

export function getWorkingCopy(postId: number): Promise<WorkingCopy> {
  return apiGet<WorkingCopy>(`/api/posts/${postId}/working-copy`);
}

/** [발행]을 누를 때마다 새 `idempotencyKey`(UUID)를 만든다. 409 IN_PROGRESS 재시도만 같은 키를 쓴다. */
export function publishPost(
  postId: number,
  body: PublishRequest,
  idempotencyKey: string,
): Promise<PublishResponse> {
  return apiPost<PublishResponse>(`/api/posts/${postId}/publish`, body, {
    headers: { [IDEMPOTENCY_HEADER]: idempotencyKey },
  });
}

/** 서버 렌더러 미리보기. 이전 요청은 `signal`로 취소한다. */
export function previewMarkdown(contentMd: string, signal?: AbortSignal): Promise<PreviewResponse> {
  return apiPost<PreviewResponse>('/api/markdown/preview', { contentMd }, { signal });
}

/** 자동 저장(서버 쪽 임시 보관). */
export function autosave(postId: number, body: SaveRequest, signal?: AbortSignal) {
  return apiPut<SaveResponse>(`/api/posts/${postId}/autosave`, body, { signal });
}

/** 수동 저장 — 즉시 DB 반영. */
export function saveWorkingCopy(postId: number, body: SaveRequest) {
  return apiPut<SaveResponse>(`/api/posts/${postId}/working-copy`, body);
}

/** [변경 취소] — 발행 글의 작업본을 버린다(204). 작업본이 없어도 성공, 임시글은 409 NOT_PUBLISHED. */
export function discardWorkingCopy(postId: number): Promise<void> {
  return apiDelete<void>(`/api/posts/${postId}/working-copy`);
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  for (const part of document.cookie.split(';')) {
    const index = part.indexOf('=');
    if (index >= 0 && part.slice(0, index).trim() === name) {
      const raw = part.slice(index + 1).trim();
      try {
        return decodeURIComponent(raw);
      } catch {
        return raw;
      }
    }
  }
  return null;
}

/**
 * 페이지를 떠날 때(`pagehide`) 자동 저장을 한 번 보낸다. `keepalive: true`라 페이지가 닫혀도 요청이 이어진다.
 * 결과는 기다리지 않는다(응답은 다음에 에디터를 열 때 서버 버전으로 확인).
 */
export function autosaveKeepalive(postId: number, body: SaveRequest): void {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  };
  const token = readCookie(CSRF_COOKIE);
  if (token) {
    headers[CSRF_HEADER] = token;
  }
  try {
    void fetch(`/api/posts/${postId}/autosave`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(body),
      credentials: 'same-origin',
      keepalive: true,
    }).catch(() => undefined);
  } catch {
    // 떠나는 중이면 무시한다. 이 기기(IndexedDB)에 남은 내용이 다음에 동기화된다.
  }
}

/**
 * 전체 글 목록 (홈, 005 FR-001~006). `cursor`는 이전 응답의 `nextCursor`를 그대로 넘기고 해석하지 않는다.
 * `size`는 보내지 않는다 — 서버가 설정값(기본 9)으로 고정한다.
 */
export function listHomePosts(cursor?: string | null): Promise<PostCardPage> {
  return apiGet<PostCardPage>(
    cursor ? `/api/posts?cursor=${encodeURIComponent(cursor)}` : '/api/posts',
  );
}

/** 글 상세 (005 FR-026). 없는 글·볼 수 없는 글은 모두 404 `NOT_FOUND`. */
export function getPostDetail(postId: number | string): Promise<PostDetail> {
  return apiGet<PostDetail>(`/api/posts/${postId}`);
}
