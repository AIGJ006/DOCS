import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import type { ProcessResult } from './imageProcessor';
import { uploadImage } from './uploadImage';

const STORAGE = 'http://localhost:9000/blog/images/2026/10/abc';
const ORIGINAL_URL = `${STORAGE}.webp`;
const THUMB_URL = `${STORAGE}_thumb.webp`;

const processed: ProcessResult = {
  ok: true,
  image: {
    blob: new Blob([new Uint8Array(4000)], { type: 'image/webp' }),
    contentType: 'image/webp',
    thumb: new Blob([new Uint8Array(400)], { type: 'image/webp' }),
    thumbContentType: 'image/webp',
    width: 1920,
    height: 1440,
  },
};

function ticket(id = 9001) {
  const headers = {
    'Content-Type': 'image/webp',
    'Cache-Control': 'public, max-age=31536000, immutable',
  };
  return {
    imageId: id,
    upload: { url: `${ORIGINAL_URL}?X-Amz-Signature=a`, method: 'PUT', headers },
    thumbUpload: { url: `${THUMB_URL}?X-Amz-Signature=b`, method: 'PUT', headers },
    expiresAt: '2026-10-08T00:05:00Z',
  };
}

const uploaded = {
  imageId: 9001,
  url: ORIGINAL_URL,
  thumbUrl: THUMB_URL,
  contentType: 'image/webp',
  width: 1920,
  height: 1440,
  sizeBytes: 4000,
};

const process = vi.fn(async () => processed);
const file = new File([new Uint8Array(10)], 'IMG_0001.jpg', { type: 'image/jpeg' });

beforeEach(() => {
  resetClientForTests();
  process.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('uploadImage', () => {
  it('presign → 원본·썸네일 PUT(헤더 그대로, 자격 증명 없음) → complete → uploaded', async () => {
    const fetchMock = stubFetch({
      'POST /api/images/presign': () => json(201, ticket()),
      [`PUT ${ORIGINAL_URL}`]: () => new Response(null, { status: 200 }),
      [`PUT ${THUMB_URL}`]: () => new Response(null, { status: 200 }),
      'POST /api/images/9001/complete': () => json(200, uploaded),
    });

    const result = await uploadImage(file, { process });

    expect(result).toEqual({ kind: 'uploaded', image: uploaded });
    const presignBody = JSON.parse(
      String(requestsTo(fetchMock, 'POST', '/api/images/presign')[0][1]?.body),
    );
    expect(presignBody).toEqual({
      purpose: 'POST',
      contentType: 'image/webp',
      size: 4000,
      thumbContentType: 'image/webp',
      thumbSize: 400,
    });
    expect(JSON.stringify(presignBody)).not.toContain('IMG_0001');
    const [, init] = requestsTo(fetchMock, 'PUT', ORIGINAL_URL)[0];
    expect(init?.credentials).toBe('omit');
    expect(init?.headers).toEqual(ticket().upload.headers);
    expect(requestsTo(fetchMock, 'PUT', THUMB_URL)).toHaveLength(1);
  });

  it('저장소 PUT 403(서명 만료)이면 presign부터 한 번 다시 한다', async () => {
    let puts = 0;
    let presigns = 0;
    const fetchMock = stubFetch({
      'POST /api/images/presign': () => json(201, ticket(9000 + ++presigns)),
      [`PUT ${ORIGINAL_URL}`]: () => new Response(null, { status: ++puts === 1 ? 403 : 200 }),
      [`PUT ${THUMB_URL}`]: () => new Response(null, { status: 200 }),
      'POST /api/images/9002/complete': () => json(200, { ...uploaded, imageId: 9002 }),
    });

    const result = await uploadImage(file, { process });

    expect(result.kind).toBe('uploaded');
    expect(requestsTo(fetchMock, 'POST', '/api/images/presign')).toHaveLength(2);
  });

  it('두 번째도 403이면 즉시 안내', async () => {
    stubFetch({
      'POST /api/images/presign': () => json(201, ticket()),
      [`PUT ${ORIGINAL_URL}`]: () => new Response(null, { status: 403 }),
    });
    const result = await uploadImage(file, { process });
    expect(result).toMatchObject({
      kind: 'rejected',
      message: '사진이 올라가지 않았어요. 다시 시도해 주세요',
    });
  });

  it.each([
    [400, 'VALIDATION_FAILED', 'jpg, png, gif, webp 사진만 올릴 수 있어요'],
    [
      409,
      'STORAGE_QUOTA_EXCEEDED',
      '사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요',
    ],
    [429, 'DAILY_UPLOAD_LIMIT', '오늘은 사진을 200장까지 올릴 수 있어요. 내일 다시 시도해 주세요'],
    [429, 'TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'],
    [403, 'EMAIL_NOT_VERIFIED', '이메일 인증 후 이용할 수 있어요'],
    [401, 'LOGIN_REQUIRED', '로그인이 필요해요'],
  ])('presign %i %s → 즉시 안내(보관 안 함)', async (status, code, message) => {
    const errors =
      code === 'VALIDATION_FAILED'
        ? [{ field: 'contentType', code: 'UNSUPPORTED_IMAGE_TYPE', message: 'x' }]
        : [];
    stubFetch({
      'POST /api/images/presign': () =>
        json(status, errorBody(code, 'x', errors), { 'Retry-After': '30' }),
    });
    const result = await uploadImage(file, { process });
    expect(result).toEqual({ kind: 'rejected', code, message });
  });

  it('complete 400 IMAGE_REJECTED → 즉시 안내', async () => {
    stubFetch({
      'POST /api/images/presign': () => json(201, ticket()),
      [`PUT ${ORIGINAL_URL}`]: () => new Response(null, { status: 200 }),
      [`PUT ${THUMB_URL}`]: () => new Response(null, { status: 200 }),
      'POST /api/images/9001/complete': () =>
        json(
          400,
          errorBody('IMAGE_REJECTED', '올릴 수 없는 사진이에요', [], { reason: 'TYPE_MISMATCH' }),
        ),
    });
    expect(await uploadImage(file, { process })).toEqual({
      kind: 'rejected',
      code: 'IMAGE_REJECTED',
      message: '올릴 수 없는 사진이에요',
    });
  });

  it('처리 실패는 서버에 가지 않고 즉시 안내', async () => {
    const fetchMock = stubFetch({});
    const result = await uploadImage(file, {
      process: async () => ({
        ok: false,
        code: 'UNSUPPORTED_IMAGE_TYPE',
        message: 'jpg, png, gif, webp 사진만 올릴 수 있어요',
      }),
    });
    expect(result).toEqual({
      kind: 'rejected',
      code: 'UNSUPPORTED_IMAGE_TYPE',
      message: 'jpg, png, gif, webp 사진만 올릴 수 있어요',
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  describe('보관 후 다시 시도 (R12)', () => {
    it('오프라인이면 요청 없이 pending', async () => {
      const fetchMock = stubFetch({});
      expect(await uploadImage(file, { process, isOnline: () => false })).toEqual({
        kind: 'pending',
      });
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('네트워크 오류(TypeError)면 pending', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => {
          throw new TypeError('Failed to fetch');
        }),
      );
      expect(await uploadImage(file, { process })).toEqual({ kind: 'pending' });
    });

    it.each([
      ['presign', 503],
      ['put', 500],
      ['complete', 502],
    ])('%s 5xx면 pending', async (step, status) => {
      stubFetch({
        'POST /api/images/presign': () =>
          step === 'presign'
            ? json(status, errorBody('TEMPORARILY_UNAVAILABLE', 'x'))
            : json(201, ticket()),
        [`PUT ${ORIGINAL_URL}`]: () =>
          new Response(null, { status: step === 'put' ? status : 200 }),
        [`PUT ${THUMB_URL}`]: () => new Response(null, { status: 200 }),
        'POST /api/images/9001/complete': () =>
          json(status, errorBody('TEMPORARILY_UNAVAILABLE', 'x')),
      });
      expect(await uploadImage(file, { process })).toEqual({ kind: 'pending' });
    });

    it('30초 안에 끝나지 않으면 pending', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
          if (String(input).startsWith('/api/images/presign')) {
            return json(201, ticket());
          }
          return new Promise<Response>((_, reject) => {
            init?.signal?.addEventListener('abort', () =>
              reject(new DOMException('aborted', 'AbortError')),
            );
          });
        }),
      );
      expect(await uploadImage(file, { process, timeoutMs: 20 })).toEqual({ kind: 'pending' });
    });
  });
});
