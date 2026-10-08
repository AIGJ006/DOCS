/**
 * 고르는 순간 GIF·움직이는 사진 검사 (003 T075, FR-036·FR-038, research R7). 서버 complete가 같은 검사를 다시 하므로(FR-037)
 * 여기서는 기다림 없이 알려 주는 것이 목적이다.
 *
 * - GIF: 논리 화면 가로·세로(각 `gifMaxSide` 이하)와 이미지 기술자(`0x2C`) 수(`gifMaxFrames` 이하). 상한+1번째에서 멈춘다.
 * - 움직이는 WebP(VP8X ANIM 표시)·APNG(`acTL`)는 거부하지 않고 "첫 장면만 남아요"를 알린다 — 캔버스로 다시 만들면 첫
 *   장면만 남기 때문이다.
 */
import { ANIMATION_NOTICE, UPLOAD_MESSAGES } from './uploadMessages';

export interface GifInfo {
  width: number;
  height: number;
  frames: number;
}

export interface GifLimits {
  gifMaxSide: number;
  gifMaxFrames: number;
}

export const DEFAULT_GIF_LIMITS: GifLimits = { gifMaxSide: 1920, gifMaxFrames: 300 };

export type PickCheck =
  | { ok: true; notice: string | null }
  | { ok: false; code: 'GIF_TOO_LARGE' | 'GIF_TOO_MANY_FRAMES'; message: string };

function isGif(bytes: Uint8Array): boolean {
  return bytes.length >= 6 && bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46;
}

/** 하위 블록(길이 바이트 + 데이터 … 0)을 건너뛴다. 끝을 넘으면 -1. */
function skipSubBlocks(bytes: Uint8Array, at: number): number {
  let i = at;
  while (i < bytes.length) {
    const size = bytes[i];
    i += 1;
    if (size === 0) return i;
    i += size;
  }
  return -1;
}

/**
 * GIF 프레임 수. `max`+1에서 멈추고, 구조가 깨졌으면 -1.
 */
export function countGifFrames(bytes: Uint8Array, max: number): number {
  if (!isGif(bytes) || bytes.length < 13) return -1;
  let i = 13;
  if (bytes[10] & 0x80) {
    i += 3 * (1 << ((bytes[10] & 0x07) + 1));
  }
  let frames = 0;
  while (i < bytes.length) {
    const marker = bytes[i];
    if (marker === 0x3b) {
      return frames;
    }
    if (marker === 0x21) {
      i = skipSubBlocks(bytes, i + 2);
    } else if (marker === 0x2c) {
      if (i + 10 > bytes.length) return -1;
      const packed = bytes[i + 9];
      i += 10;
      if (packed & 0x80) {
        i += 3 * (1 << ((packed & 0x07) + 1));
      }
      i = skipSubBlocks(bytes, i + 1);
      if (i < 0) return -1;
      frames += 1;
      if (frames > max) return frames;
      continue;
    } else {
      return -1;
    }
    if (i < 0) return -1;
  }
  // 끝 표시(0x3B) 없이 끝났다 — 프레임을 다 읽었으면 그 수를 믿는다(브라우저도 보여 준다)
  return frames > 0 ? frames : -1;
}

/** GIF 머리말 + 프레임 수 (상한 300+1에서 멈춤). GIF가 아니거나 깨졌으면 null. */
export function inspectGif(
  bytes: Uint8Array,
  maxFrames = DEFAULT_GIF_LIMITS.gifMaxFrames,
): GifInfo | null {
  if (!isGif(bytes) || bytes.length < 10) return null;
  const width = bytes[6] | (bytes[7] << 8);
  const height = bytes[8] | (bytes[9] << 8);
  const frames = countGifFrames(bytes, maxFrames);
  if (frames < 0) return null;
  return { width, height, frames };
}

function ascii(bytes: Uint8Array, at: number, text: string): boolean {
  for (let k = 0; k < text.length; k++) {
    if (bytes[at + k] !== text.charCodeAt(k)) return false;
  }
  return true;
}

/** 움직이는 WebP(VP8X ANIM)·APNG(IDAT 앞 acTL)인가. */
export function isAnimatedStill(bytes: Uint8Array): boolean {
  if (ascii(bytes, 0, 'RIFF') && ascii(bytes, 8, 'WEBP')) {
    return ascii(bytes, 12, 'VP8X') && (bytes[20] & 0x02) !== 0;
  }
  if (bytes[0] === 0x89 && ascii(bytes, 1, 'PNG')) {
    let i = 8;
    while (i + 8 <= bytes.length) {
      const length =
        ((bytes[i] << 24) | (bytes[i + 1] << 16) | (bytes[i + 2] << 8) | bytes[i + 3]) >>> 0;
      if (ascii(bytes, i + 4, 'acTL')) return true;
      if (ascii(bytes, i + 4, 'IDAT')) return false;
      i += 12 + length;
    }
  }
  return false;
}

async function readBytes(file: Blob, length?: number): Promise<Uint8Array> {
  const part = length === undefined ? file : file.slice(0, length);
  if (typeof part.arrayBuffer === 'function') {
    return new Uint8Array(await part.arrayBuffer());
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(part);
  });
}

/** PNG에서 acTL을 찾을 만큼 앞부분 (IDAT 앞에 온다). */
const STILL_HEAD_BYTES = 64 * 1024;

/**
 * 고른 사진 검사. GIF는 전체(10MB 이하 — 크기는 imageProcessor가 먼저 본다)를, 그 밖에는 앞부분만 읽는다.
 */
export async function checkPickedImage(
  file: Blob,
  limits: GifLimits = DEFAULT_GIF_LIMITS,
): Promise<PickCheck> {
  const head = await readBytes(file, STILL_HEAD_BYTES);
  if (isGif(head)) {
    const width = head[6] | (head[7] << 8);
    const height = head[8] | (head[9] << 8);
    if (Math.max(width, height) > limits.gifMaxSide) {
      return { ok: false, code: 'GIF_TOO_LARGE', message: UPLOAD_MESSAGES.GIF_TOO_LARGE };
    }
    const frames = countGifFrames(await readBytes(file), limits.gifMaxFrames);
    if (frames > limits.gifMaxFrames) {
      return {
        ok: false,
        code: 'GIF_TOO_MANY_FRAMES',
        message: UPLOAD_MESSAGES.GIF_TOO_MANY_FRAMES,
      };
    }
    // 깨진 GIF(-1)는 해독 단계(imageProcessor)와 서버 complete가 판정한다
    return { ok: true, notice: null };
  }
  return { ok: true, notice: isAnimatedStill(head) ? ANIMATION_NOTICE : null };
}
