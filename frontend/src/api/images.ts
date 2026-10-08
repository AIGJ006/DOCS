/**
 * 사진 업로드 API (003 contracts/openapi.yaml, research R1·R2). 001 `client.ts`(CSRF 헤더·오류 본문) 위에 둔다.
 *
 * - presign → 저장소 직접 PUT(원본·썸네일) → complete.
 * - 저장소 PUT은 서명 주소로 가므로 쿠키·자격 증명을 보내지 않는다(`credentials: 'omit'`). 서명에 포함된 헤더
 *   (`Content-Type`·`Cache-Control`)는 응답의 `headers` 그대로 붙인다.
 * - 서버 경유 방식(PROXY)이면 `upload.url`이 같은 출처의 `/api/images/{id}/content`다. 그때만 세션 쿠키와 CSRF 헤더를
 *   붙인다 — 화면 코드는 방식을 몰라도 된다.
 * - 이 API들의 404는 공통 404 화면으로 바꾸지 않는다(에디터는 그대로, 업로드 실패로만 다룬다).
 */
import { apiGet, apiPost, CSRF_COOKIE, CSRF_HEADER } from './client';
import type {
  ImageUploadTicket,
  PresignRequest,
  StorageUsage,
  UploadedImage,
  UploadTarget,
} from './types/images';

export type {
  ImageUploadTicket,
  PresignRequest,
  StorageUsage,
  UploadedImage,
  UploadTarget,
} from './types/images';

/** 저장소 PUT 실패. `status`가 0이면 응답을 받지 못했다(네트워크). */
export class StorageUploadError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`저장소 업로드 실패: ${status}`);
    this.name = 'StorageUploadError';
    this.status = status;
  }
}

export function presign(request: PresignRequest, signal?: AbortSignal): Promise<ImageUploadTicket> {
  return apiPost<ImageUploadTicket>('/api/images/presign', request, {
    signal,
    notFoundScreen: false,
  });
}

export function complete(imageId: number, signal?: AbortSignal): Promise<UploadedImage> {
  return apiPost<UploadedImage>(`/api/images/${imageId}/complete`, undefined, {
    signal,
    notFoundScreen: false,
  });
}

export function getStorageUsage(): Promise<StorageUsage> {
  return apiGet<StorageUsage>('/api/me/storage', { notFoundScreen: false });
}

function isSameOrigin(url: string): boolean {
  if (url.startsWith('/') && !url.startsWith('//')) {
    return true;
  }
  if (typeof window === 'undefined') {
    return false;
  }
  try {
    return new URL(url).origin === window.location.origin;
  } catch {
    return false;
  }
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  for (const part of document.cookie.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === name) {
      return decodeURIComponent(rest.join('='));
    }
  }
  return null;
}

/** 업로드 주소로 PUT한다. 2xx가 아니면 {@link StorageUploadError}. 네트워크 오류는 그대로(TypeError) 던진다. */
export async function putToStorage(
  target: UploadTarget,
  blob: Blob,
  signal?: AbortSignal,
): Promise<void> {
  const sameOrigin = isSameOrigin(target.url);
  const headers: Record<string, string> = { ...target.headers };
  if (sameOrigin) {
    const token = readCookie(CSRF_COOKIE);
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }
  const response = await fetch(target.url, {
    method: target.method,
    headers,
    body: blob,
    credentials: sameOrigin ? 'same-origin' : 'omit',
    signal,
  });
  if (!response.ok) {
    throw new StorageUploadError(response.status);
  }
}
