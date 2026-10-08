import { describe, expect, it, vi } from 'vitest';
import {
  detectFormat,
  limitsFrom,
  processImage,
  type DecodedImage,
  type ProcessorDeps,
} from './imageProcessor';

const JPEG_HEAD = [0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46, 0x49, 0x46, 0, 1];
const PNG_HEAD = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d];
const GIF_HEAD = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 0, 1, 0, 0, 0];
const WEBP_HEAD = [0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50];

function file(head: number[], size = 1000, name = 'IMG_0001.jpg'): File {
  const bytes = new Uint8Array(Math.max(size, head.length));
  bytes.set(head);
  return new File([bytes], name, { type: 'image/jpeg' });
}

interface EncodeCall {
  width: number;
  height: number;
  type: string;
  quality: number;
}

/** 캔버스·createImageBitmap 대신 쓰는 가짜. `encodeAs`로 결과 형식·크기를 정한다. */
function fakeDeps(
  decoded: { width: number; height: number },
  encodeAs: (call: EncodeCall) => { type: string; size: number } | null = (c) => ({
    type: c.type,
    size: 100_000,
  }),
) {
  const calls: EncodeCall[] = [];
  const close = vi.fn();
  const deps: ProcessorDeps = {
    decode: vi.fn(async (): Promise<DecodedImage> => ({ ...decoded, source: {}, close })),
    encode: vi.fn(async (_image, width, height, type, quality) => {
      const call = { width, height, type, quality };
      calls.push(call);
      const result = encodeAs(call);
      return result ? new Blob([new Uint8Array(result.size)], { type: result.type }) : null;
    }),
  };
  return { deps, calls, close };
}

describe('detectFormat', () => {
  it('매직 바이트로 형식을 판별한다 (파일 이름·type은 보지 않는다)', async () => {
    expect(await detectFormat(file(JPEG_HEAD))).toBe('image/jpeg');
    expect(await detectFormat(file(PNG_HEAD))).toBe('image/png');
    expect(await detectFormat(file(GIF_HEAD))).toBe('image/gif');
    expect(await detectFormat(file(WEBP_HEAD))).toBe('image/webp');
    expect(await detectFormat(file([0x3c, 0x73, 0x76, 0x67, 0x20]))).toBeNull();
  });
});

describe('processImage', () => {
  it('긴 변 4000px → 1920px WebP 0.8, 썸네일 가로 640px', async () => {
    const { deps, calls, close } = fakeDeps({ width: 4000, height: 3000 });

    const result = await processImage(file(JPEG_HEAD, 5_000_000), { deps });

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(calls[0]).toEqual({ width: 1920, height: 1440, type: 'image/webp', quality: 0.8 });
    expect(result.image.contentType).toBe('image/webp');
    expect(result.image.width).toBe(1920);
    expect(result.image.height).toBe(1440);
    expect(result.image.thumbContentType).toBe('image/webp');
    expect(calls.find((c) => c.width === 640)).toMatchObject({ height: 480 });
    expect(close).toHaveBeenCalled();
  });

  it('작은 사진은 키우지 않는다', async () => {
    const { deps, calls } = fakeDeps({ width: 800, height: 600 });
    const result = await processImage(file(PNG_HEAD), { deps });
    expect(result.ok).toBe(true);
    expect(calls[0]).toMatchObject({ width: 800, height: 600 });
    expect(calls[1]).toMatchObject({ width: 640, height: 480 });
  });

  it('WebP를 만들지 못하는 브라우저(PNG를 돌려줌)는 JPEG 0.8로 다시 만든다 (Q4)', async () => {
    const { deps, calls } = fakeDeps({ width: 3000, height: 2000 }, (c) => ({
      type: c.type === 'image/webp' ? 'image/png' : c.type,
      size: 200_000,
    }));

    const result = await processImage(file(JPEG_HEAD), { deps });

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.image.contentType).toBe('image/jpeg');
    expect(result.image.thumbContentType).toBe('image/jpeg');
    expect(calls.some((c) => c.type === 'image/jpeg' && c.quality === 0.8)).toBe(true);
    expect(result.image.blob.type).toBe('image/jpeg');
  });

  it('썸네일이 1MB를 넘으면 품질 0.7 → 0.6으로 낮춘다', async () => {
    const { deps, calls } = fakeDeps({ width: 2000, height: 1000 }, (c) => ({
      type: c.type,
      size: c.width === 640 && c.quality > 0.6 ? 1_100_000 : 300_000,
    }));

    const result = await processImage(file(JPEG_HEAD), { deps });

    expect(result.ok).toBe(true);
    expect(calls.filter((c) => c.width === 640).map((c) => c.quality)).toEqual([0.8, 0.7, 0.6]);
  });

  it('그래도 1MB를 넘으면 실패 결과', async () => {
    const { deps } = fakeDeps({ width: 2000, height: 1000 }, (c) => ({
      type: c.type,
      size: c.width === 640 ? 1_100_000 : 300_000,
    }));
    const result = await processImage(file(JPEG_HEAD), { deps });
    expect(result).toEqual({
      ok: false,
      code: 'PROCESSING_FAILED',
      message: '사진을 처리하지 못했어요',
    });
  });

  it('원래 파일 50MB 초과·알 수 없는 형식은 처리 전에 거부한다', async () => {
    const { deps } = fakeDeps({ width: 100, height: 100 });

    const big = await processImage(file(JPEG_HEAD, 50 * 1024 * 1024 + 1), { deps });
    const svg = await processImage(file([0x3c, 0x73, 0x76, 0x67]), { deps });

    expect(big).toMatchObject({ ok: false, code: 'IMAGE_TOO_LARGE' });
    expect(svg).toMatchObject({
      ok: false,
      code: 'UNSUPPORTED_IMAGE_TYPE',
      message: 'jpg, png, gif, webp 사진만 올릴 수 있어요',
    });
    expect(deps.decode).not.toHaveBeenCalled();
  });

  it('GIF는 원본 그대로 + 첫 장면 썸네일, 10MB 넘으면 거부', async () => {
    const { deps, calls } = fakeDeps({ width: 480, height: 270 });
    const gif = file(GIF_HEAD, 3_000_000, 'cat.gif');

    const result = await processImage(gif, { deps });

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.image.blob.size).toBe(gif.size);
    expect(result.image.blob).not.toBeInstanceOf(File);
    expect(result.image.contentType).toBe('image/gif');
    expect(calls).toHaveLength(1);
    expect(calls[0]).toMatchObject({ width: 480, height: 270 });

    const big = await processImage(file(GIF_HEAD, 10_485_761), { deps });
    expect(big).toMatchObject({ ok: false, code: 'IMAGE_TOO_LARGE' });
  });

  it('결과 어디에도 원래 파일 이름이 없다', async () => {
    const { deps } = fakeDeps({ width: 4000, height: 3000 });
    const result = await processImage(file(JPEG_HEAD, 1000, 'IMG_0001_서울.jpg'), { deps });
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.image.blob).not.toBeInstanceOf(File);
    expect(result.image.thumb).not.toBeInstanceOf(File);
    expect(JSON.stringify(result)).not.toContain('IMG_0001');
  });

  it('해독에 실패하면 처리 실패', async () => {
    const deps: ProcessorDeps = {
      decode: vi.fn(async () => {
        throw new Error('broken');
      }),
      encode: vi.fn(),
    };
    expect(await processImage(file(JPEG_HEAD), { deps })).toMatchObject({
      ok: false,
      code: 'PROCESSING_FAILED',
    });
  });

  it('서버 limits를 받으면 그 값으로 판정한다 (T063)', async () => {
    const { deps, calls } = fakeDeps({ width: 4000, height: 3000 });
    const limits = limitsFrom({
      maxUploadBytes: 10_485_760,
      maxThumbBytes: 1_048_576,
      maxSourceBytes: 2_000,
      longSide: 1280,
      thumbMaxWidth: 320,
      gifMaxSide: 1920,
      gifMaxFrames: 300,
    });
    expect(await processImage(file(JPEG_HEAD, 2_001), { deps, limits })).toMatchObject({
      ok: false,
      code: 'IMAGE_TOO_LARGE',
    });
    await processImage(file(JPEG_HEAD, 1_000), { deps, limits });
    expect(calls[0]).toMatchObject({ width: 1280, height: 960 });
    expect(calls[1]).toMatchObject({ width: 320, height: 240 });
  });
});
