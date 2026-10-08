import { apiPatch, apiPost } from '../../api/client';

/**
 * 소셜 사진 복사 (FR-031, R-20, 11 §4-2). 가입을 마친 뒤 브라우저가 한 번만 한다 — 서버는 외부 사진 주소를 요청·저장하지 않는다.
 *
 * 1. `Image`(`crossOrigin="anonymous"`)로 5초 안에 받는다(소셜 가입 마무리 화면만 CSP `img-src`가 두 호스트를 허용).
 * 2. 캔버스로 가운데 정사각형을 잘라 256×256 WebP로 바꾼다(EXIF도 사라진다).
 * 3. 003 업로드 흐름: `POST /api/images/presign {purpose: PROFILE}` → 저장소로 직접 PUT → `POST /api/images/{id}/complete`.
 * 4. `PATCH /api/me/profile {profileImageId}`로 프로필 사진에 연결한다(US6).
 *
 * 어느 단계든 실패하면 예외를 던지지 않고 안내 문구만 돌려준다 — 가입은 그대로 성공이다.
 * presign 응답 모양(`imageId`·`uploadUrl`·`uploadHeaders`)은 003 계약이 아직 없어 이 파일이 가정한 것이다(003 구현 때 맞춘다).
 */

export const SOCIAL_PHOTO_TIMEOUT_MS = 5000;
export const PROFILE_PHOTO_SIZE = 256;
export const SOCIAL_PHOTO_FAILED_MESSAGE =
  '소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요';

export type PhotoImportResult = { ok: true; imageId: number } | { ok: false; message: string };

export interface PhotoImportDeps {
  loadImage: (url: string) => Promise<HTMLImageElement>;
  toSquareWebp: (image: HTMLImageElement) => Promise<Blob>;
}

interface PresignResponse {
  imageId: number;
  uploadUrl: string;
  uploadHeaders?: Record<string, string>;
}

export async function importSocialPhoto(
  url: string,
  deps: PhotoImportDeps = { loadImage, toSquareWebp },
): Promise<PhotoImportResult> {
  try {
    const image = await withTimeout(deps.loadImage(url), SOCIAL_PHOTO_TIMEOUT_MS);
    const blob = await deps.toSquareWebp(image);
    // WebP로 인코딩하지 못하는 브라우저는 PNG를 돌려준다 — 실제 형식으로 신고한다.
    const contentType = blob.type || 'image/webp';
    const presign = await apiPost<PresignResponse>('/api/images/presign', {
      purpose: 'PROFILE',
      contentType,
      size: blob.size,
    });
    const put = await fetch(presign.uploadUrl, {
      method: 'PUT',
      body: blob,
      credentials: 'omit',
      headers: { 'Content-Type': contentType, ...(presign.uploadHeaders ?? {}) },
    });
    if (!put.ok) {
      throw new Error(`upload ${put.status}`);
    }
    await apiPost(`/api/images/${presign.imageId}/complete`);
    await apiPatch('/api/me/profile', { profileImageId: presign.imageId });
    return { ok: true, imageId: presign.imageId };
  } catch {
    return { ok: false, message: SOCIAL_PHOTO_FAILED_MESSAGE };
  }
}

/** 소셜 사진을 익명 CORS로 불러온다(캔버스에서 꺼낼 수 있어야 한다). */
export function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('image load failed'));
    image.src = url;
  });
}

/** 가운데 정사각형 영역. */
export function centerSquare(
  width: number,
  height: number,
): { sx: number; sy: number; side: number } {
  const side = Math.min(width, height);
  return { sx: Math.floor((width - side) / 2), sy: Math.floor((height - side) / 2), side };
}

/** 가운데 정사각형 → 256×256 WebP. */
export function toSquareWebp(image: HTMLImageElement): Promise<Blob> {
  const width = image.naturalWidth || image.width;
  const height = image.naturalHeight || image.height;
  const { sx, sy, side } = centerSquare(width, height);
  const canvas = document.createElement('canvas');
  canvas.width = PROFILE_PHOTO_SIZE;
  canvas.height = PROFILE_PHOTO_SIZE;
  const context = canvas.getContext('2d');
  if (!context) {
    return Promise.reject(new Error('canvas unavailable'));
  }
  context.drawImage(image, sx, sy, side, side, 0, 0, PROFILE_PHOTO_SIZE, PROFILE_PHOTO_SIZE);
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('encode failed'))),
      'image/webp',
      0.9,
    );
  });
}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error: unknown) => {
        clearTimeout(timer);
        reject(error instanceof Error ? error : new Error(String(error)));
      },
    );
  });
}
