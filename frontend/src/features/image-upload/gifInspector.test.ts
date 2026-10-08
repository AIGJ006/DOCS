import { describe, expect, it } from 'vitest';
import { checkPickedImage, countGifFrames, inspectGif, isAnimatedStill } from './gifInspector';
import { ANIMATION_NOTICE } from './uploadMessages';

/** 최소 GIF: 논리 화면 w×h, 전역 색표 2색, 프레임 n개(각 1×1). */
function gif(width: number, height: number, frames: number, { trailer = true } = {}): Uint8Array {
  const bytes: number[] = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61];
  bytes.push(width & 0xff, width >> 8, height & 0xff, height >> 8, 0x80, 0, 0);
  bytes.push(0, 0, 0, 0xff, 0xff, 0xff); // 전역 색표 2색
  // 반복 확장 (NETSCAPE2.0)
  bytes.push(0x21, 0xff, 0x0b, ...Array.from('NETSCAPE2.0', (c) => c.charCodeAt(0)), 3, 1, 0, 0, 0);
  for (let i = 0; i < frames; i++) {
    bytes.push(0x21, 0xf9, 4, 0, 10, 0, 0, 0); // 그래픽 제어 확장
    bytes.push(0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0); // 이미지 기술자 1×1
    bytes.push(2, 2, 0x44, 0x01, 0); // LZW 최소 코드 2, 데이터 블록 1개
  }
  if (trailer) bytes.push(0x3b);
  return new Uint8Array(bytes);
}

function file(bytes: Uint8Array, type: string): Blob {
  return new Blob([new Uint8Array(bytes)], { type });
}

/** VP8X 머리말만 있는 WebP. animated면 ANIM 표시(0x02). */
function webp(animated: boolean): Uint8Array {
  const head = [0x52, 0x49, 0x46, 0x46, 30, 0, 0, 0, 0x57, 0x45, 0x42, 0x50];
  const vp8x = [0x56, 0x50, 0x38, 0x58, 10, 0, 0, 0, animated ? 0x02 : 0x00, 0, 0, 0];
  return new Uint8Array([...head, ...vp8x, 9, 0, 0, 9, 0, 0]);
}

/** PNG 서명 + IHDR + (acTL) + IDAT. */
function png(animated: boolean): Uint8Array {
  const chunk = (type: string, data: number[]) => [
    0,
    0,
    0,
    data.length,
    ...Array.from(type, (c) => c.charCodeAt(0)),
    ...data,
    0,
    0,
    0,
    0,
  ];
  return new Uint8Array([
    0x89,
    0x50,
    0x4e,
    0x47,
    0x0d,
    0x0a,
    0x1a,
    0x0a,
    ...chunk('IHDR', [0, 0, 0, 10, 0, 0, 0, 10, 8, 6, 0, 0, 0]),
    ...(animated ? chunk('acTL', [0, 0, 0, 2, 0, 0, 0, 0]) : []),
    ...chunk('IDAT', [1, 2, 3]),
  ]);
}

describe('gifInspector (003 T071, FR-036·038)', () => {
  it('머리말에서 가로·세로와 프레임 수를 읽는다', () => {
    expect(inspectGif(gif(40, 30, 3))).toEqual({ width: 40, height: 30, frames: 3 });
  });

  it('프레임은 상한+1에서 멈춰 센다', () => {
    expect(countGifFrames(gif(1, 1, 301), 300)).toBe(301);
    expect(countGifFrames(gif(1, 1, 400), 300)).toBe(301);
    expect(countGifFrames(gif(1, 1, 300), 300)).toBe(300);
  });

  it('잘린 GIF는 프레임을 셀 수 없다(-1)', () => {
    const cut = gif(1, 1, 2).slice(0, 40);
    expect(countGifFrames(cut, 300)).toBe(-1);
    expect(inspectGif(new Uint8Array([0x47, 0x49, 0x46]))).toBeNull();
  });

  it('1921px GIF는 고르는 순간 안내한다', async () => {
    const result = await checkPickedImage(file(gif(1921, 10, 1), 'image/gif'));
    expect(result).toEqual({
      ok: false,
      code: 'GIF_TOO_LARGE',
      message: 'GIF는 가로·세로 1920px까지 올릴 수 있어요',
    });
    expect((await checkPickedImage(file(gif(10, 1921, 1), 'image/gif'))).ok).toBe(false);
  });

  it('301프레임 GIF는 고르는 순간 안내하고 300프레임은 통과한다', async () => {
    const over = await checkPickedImage(file(gif(1920, 1920, 301), 'image/gif'));
    expect(over).toEqual({
      ok: false,
      code: 'GIF_TOO_MANY_FRAMES',
      message: 'GIF는 프레임 300장까지 올릴 수 있어요',
    });
    expect(await checkPickedImage(file(gif(1920, 1920, 300), 'image/gif'))).toEqual({
      ok: true,
      notice: null,
    });
  });

  it('한도는 서버 값(limits)을 따른다', async () => {
    const result = await checkPickedImage(file(gif(40, 30, 5), 'image/gif'), {
      gifMaxSide: 1920,
      gifMaxFrames: 4,
    });
    expect(result.ok).toBe(false);
  });

  it('움직이는 WebP·APNG면 첫 장면만 남는다고 알린다(거부하지 않음)', async () => {
    expect(isAnimatedStill(webp(true))).toBe(true);
    expect(isAnimatedStill(webp(false))).toBe(false);
    expect(isAnimatedStill(png(true))).toBe(true);
    expect(isAnimatedStill(png(false))).toBe(false);
    expect(await checkPickedImage(file(webp(true), 'image/webp'))).toEqual({
      ok: true,
      notice: ANIMATION_NOTICE,
    });
    expect(await checkPickedImage(file(png(true), 'image/png'))).toEqual({
      ok: true,
      notice: ANIMATION_NOTICE,
    });
    expect(ANIMATION_NOTICE).toBe(
      '움직이는 WebP·APNG는 첫 장면만 남아요. 움직이는 사진은 GIF로 올려 주세요',
    );
  });

  it('GIF가 아닌 보통 사진은 그대로 통과한다', async () => {
    expect(await checkPickedImage(file(png(false), 'image/png'))).toEqual({
      ok: true,
      notice: null,
    });
  });
});
