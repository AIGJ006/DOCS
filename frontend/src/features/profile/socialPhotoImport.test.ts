import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import {
  SOCIAL_PHOTO_FAILED_MESSAGE,
  SOCIAL_PHOTO_TIMEOUT_MS,
  centerSquare,
  importSocialPhoto,
  loadImage,
  type PhotoImportDeps,
} from './socialPhotoImport';

const PHOTO = 'https://lh3.googleusercontent.com/a/abc=s256-c';
const UPLOAD_URL = 'http://localhost:9000/blog/profile/1.webp';

function deps(overrides: Partial<PhotoImportDeps> = {}): PhotoImportDeps {
  return {
    loadImage: vi.fn(async () => ({ width: 400, height: 300 }) as unknown as HTMLImageElement),
    toSquareWebp: vi.fn(async () => new Blob([new Uint8Array(1234)], { type: 'image/webp' })),
    ...overrides,
  };
}

function uploadRoutes() {
  return stubFetch({
    'POST /api/images/presign': () =>
      json(201, { imageId: 41, uploadUrl: UPLOAD_URL, uploadHeaders: { 'x-amz-acl': 'private' } }),
    [`PUT ${UPLOAD_URL}`]: () => new Response(null, { status: 200 }),
    'POST /api/images/41/complete': () => json(200, { id: 41 }),
    'PATCH /api/me/profile': () => json(200, { profileImageId: 41 }),
  });
}

beforeEach(() => {
  resetClientForTests();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('importSocialPhoto', () => {
  it('presign → 직접 PUT → complete → PATCH /api/me/profile 순서로 부른다', async () => {
    const fetchMock = uploadRoutes();
    const result = await importSocialPhoto(PHOTO, deps());

    expect(result).toEqual({ ok: true, imageId: 41 });
    const order = fetchMock.mock.calls.map(
      ([input, init]) => `${init?.method ?? 'GET'} ${String(input)}`,
    );
    expect(order).toEqual([
      'POST /api/images/presign',
      `PUT ${UPLOAD_URL}`,
      'POST /api/images/41/complete',
      'PATCH /api/me/profile',
    ]);
    const [presign] = requestsTo(fetchMock, 'POST', '/api/images/presign');
    expect(JSON.parse(String(presign?.[1]?.body))).toEqual({
      purpose: 'PROFILE',
      contentType: 'image/webp',
      size: 1234,
    });
    const [put] = requestsTo(fetchMock, 'PUT', UPLOAD_URL);
    expect(put?.[1]?.body).toBeInstanceOf(Blob);
    expect((put?.[1]?.headers as Record<string, string>)['Content-Type']).toBe('image/webp');
    expect((put?.[1]?.headers as Record<string, string>)['x-amz-acl']).toBe('private');
    expect(put?.[1]?.credentials).toBe('omit');
    const [patch] = requestsTo(fetchMock, 'PATCH', '/api/me/profile');
    expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ profileImageId: 41 });
  });

  it('5초 안에 사진을 받지 못하면 실패 결과(안내 문구)만 돌려주고 업로드하지 않는다', async () => {
    vi.useFakeTimers();
    const fetchMock = uploadRoutes();
    const never = deps({ loadImage: () => new Promise<HTMLImageElement>(() => {}) });
    const pending = importSocialPhoto(PHOTO, never);
    await vi.advanceTimersByTimeAsync(SOCIAL_PHOTO_TIMEOUT_MS + 1);
    await expect(pending).resolves.toEqual({ ok: false, message: SOCIAL_PHOTO_FAILED_MESSAGE });
    expect(SOCIAL_PHOTO_FAILED_MESSAGE).toBe(
      '소셜 사진을 가져오지 못했어요. 설정에서 직접 올릴 수 있어요',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('업로드 중 오류가 나도 예외를 던지지 않고 실패 결과를 돌려준다', async () => {
    stubFetch({
      'POST /api/images/presign': () =>
        json(403, { code: 'EMAIL_NOT_VERIFIED', message: 'x', errors: [], details: null }),
    });
    await expect(importSocialPhoto(PHOTO, deps())).resolves.toEqual({
      ok: false,
      message: SOCIAL_PHOTO_FAILED_MESSAGE,
    });
  });
});

describe('centerSquare', () => {
  it('가로가 긴 사진은 가운데 정사각형을 고른다', () => {
    expect(centerSquare(400, 300)).toEqual({ sx: 50, sy: 0, side: 300 });
  });
  it('세로가 긴 사진', () => {
    expect(centerSquare(200, 500)).toEqual({ sx: 0, sy: 150, side: 200 });
  });
});

describe('loadImage', () => {
  it('crossOrigin="anonymous"로 불러온다', async () => {
    const created: { crossOrigin: string | null; src: string }[] = [];
    class FakeImage {
      crossOrigin: string | null = null;
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      private value = '';
      constructor() {
        created.push(this);
      }
      set src(v: string) {
        this.value = v;
        queueMicrotask(() => this.onload?.());
      }
      get src() {
        return this.value;
      }
    }
    vi.stubGlobal('Image', FakeImage);
    await loadImage(PHOTO);
    expect(created).toHaveLength(1);
    expect(created[0]?.crossOrigin).toBe('anonymous');
    expect(created[0]?.src).toBe(PHOTO);
  });
});

describe('toSquareWebp (기본 구현)', () => {
  it('256×256 캔버스에 가운데를 그리고 WebP로 바꾼다', async () => {
    const drawImage = vi.fn();
    const canvas = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage }),
      toBlob: (cb: (b: Blob | null) => void, type: string) => cb(new Blob(['x'], { type })),
    };
    const spy = vi
      .spyOn(document, 'createElement')
      .mockImplementation(() => canvas as unknown as HTMLElement);
    const { toSquareWebp } = await import('./socialPhotoImport');
    const blob = await toSquareWebp({ width: 400, height: 300 } as HTMLImageElement);
    spy.mockRestore();
    expect(canvas.width).toBe(256);
    expect(canvas.height).toBe(256);
    expect(drawImage).toHaveBeenCalledWith(expect.anything(), 50, 0, 300, 300, 0, 0, 256, 256);
    expect(blob.type).toBe('image/webp');
  });
});
