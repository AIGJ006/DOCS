/**
 * 브라우저 사진 처리 (003 research R3, FR-001·002·009, Clarifications Q3·Q4).
 *
 * - 형식은 파일 앞부분(매직 바이트)으로 판별한다. 파일 이름·`File.type`은 보지 않는다.
 * - GIF가 아닌 사진: `createImageBitmap(file, {imageOrientation: 'from-image'})`로 방향을 반영해 읽고 긴 변
 *   1920px 이하로 다시 그린 뒤 WebP 0.8로 만든다. 결과 `type`이 WebP가 아니면(사파리) JPEG 0.8로 다시 만든다.
 *   캔버스로 다시 그리므로 EXIF(GPS 포함)·XMP가 남지 않는다.
 * - 썸네일: 가로 640px 이하(세로 4096px 이하)로 같은 형식. 1MB를 넘으면 품질 0.7 → 0.6, 그래도 넘으면 실패.
 * - GIF는 줄이지 않고 원본 바이트 그대로(10MB 이하), 썸네일은 첫 장면. 해독 전에 gifInspector로 가로·세로·프레임 수를 본다(T075).
 * - 결과에는 원래 파일 이름이 없다(이름 없는 `Blob`).
 */
import type { ImageContentType, StorageLimits, ThumbContentType } from '../../api/types/images';
import { checkPickedImage } from './gifInspector';
import { PROCESSING_FAILED, UPLOAD_MESSAGES } from './uploadMessages';

/** 처리 한도. 서버 `GET /api/me/storage`의 `limits`와 같은 값(T063에서 서버 값으로 바꾼다). */
export interface ProcessorLimits {
  maxSourceBytes: number;
  maxUploadBytes: number;
  maxThumbBytes: number;
  longSide: number;
  thumbMaxWidth: number;
  thumbMaxHeight: number;
  gifMaxSide: number;
  gifMaxFrames: number;
}

export const DEFAULT_LIMITS: ProcessorLimits = {
  maxSourceBytes: 50 * 1024 * 1024,
  maxUploadBytes: 10 * 1024 * 1024,
  maxThumbBytes: 1024 * 1024,
  longSide: 1920,
  thumbMaxWidth: 640,
  thumbMaxHeight: 4096,
  gifMaxSide: 1920,
  gifMaxFrames: 300,
};

/** 서버 한도(`GET /api/me/storage`의 `limits`) → 처리 한도 (헌법 VII: 화면에 숫자를 따로 두지 않는다, T063). */
export function limitsFrom(limits: StorageLimits | null | undefined): Partial<ProcessorLimits> {
  if (!limits) return {};
  return {
    maxSourceBytes: limits.maxSourceBytes,
    maxUploadBytes: limits.maxUploadBytes,
    maxThumbBytes: limits.maxThumbBytes,
    longSide: limits.longSide,
    thumbMaxWidth: limits.thumbMaxWidth,
    gifMaxSide: limits.gifMaxSide,
    gifMaxFrames: limits.gifMaxFrames,
  };
}

/** 해독한 사진 (가짜로 바꿀 수 있게 캔버스 소스를 감춘다). */
export interface DecodedImage {
  width: number;
  height: number;
  source: unknown;
  close?: () => void;
}

/** 캔버스 의존 (테스트가 가짜를 넣는다). */
export interface ProcessorDeps {
  decode(file: Blob): Promise<DecodedImage>;
  encode(
    image: DecodedImage,
    width: number,
    height: number,
    type: string,
    quality: number,
  ): Promise<Blob | null>;
}

export interface ProcessedImage {
  /** 올릴 원본 (이름 없음) */
  blob: Blob;
  contentType: ImageContentType;
  thumb: Blob;
  thumbContentType: ThumbContentType;
  width: number;
  height: number;
  /** 처리는 했지만 알릴 것 (움직이는 WebP·APNG → 첫 장면만, FR-038) */
  notice?: string | null;
}

export type ProcessFailureCode =
  | 'UNSUPPORTED_IMAGE_TYPE'
  | 'IMAGE_TOO_LARGE'
  | 'GIF_TOO_LARGE'
  | 'GIF_TOO_MANY_FRAMES'
  | 'PROCESSING_FAILED';

export type ProcessResult =
  { ok: true; image: ProcessedImage } | { ok: false; code: ProcessFailureCode; message: string };

const QUALITIES = [0.8, 0.7, 0.6];

const defaultDeps: ProcessorDeps = {
  async decode(file) {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    return {
      width: bitmap.width,
      height: bitmap.height,
      source: bitmap,
      close: () => bitmap.close(),
    };
  },
  encode(image, width, height, type, quality) {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) {
      return Promise.resolve(null);
    }
    context.drawImage(image.source as CanvasImageSource, 0, 0, width, height);
    return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
  },
};

async function readHead(file: Blob, length: number): Promise<Uint8Array> {
  const slice = file.slice(0, length);
  if (typeof slice.arrayBuffer === 'function') {
    return new Uint8Array(await slice.arrayBuffer());
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(slice);
  });
}

function startsWith(bytes: Uint8Array, prefix: number[], offset = 0): boolean {
  return prefix.every((value, i) => bytes[offset + i] === value);
}

/** 매직 바이트 형식. jpg·png·gif·webp가 아니면 null. */
export async function detectFormat(file: Blob): Promise<ImageContentType | null> {
  const head = await readHead(file, 12);
  if (startsWith(head, [0xff, 0xd8, 0xff])) return 'image/jpeg';
  if (startsWith(head, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return 'image/png';
  if (startsWith(head, [0x47, 0x49, 0x46, 0x38])) return 'image/gif';
  if (startsWith(head, [0x52, 0x49, 0x46, 0x46]) && startsWith(head, [0x57, 0x45, 0x42, 0x50], 8)) {
    return 'image/webp';
  }
  return null;
}

function fail(code: ProcessFailureCode): ProcessResult {
  const message =
    code === 'PROCESSING_FAILED'
      ? PROCESSING_FAILED
      : (UPLOAD_MESSAGES as Record<string, string>)[code];
  return { ok: false, code, message };
}

function fit(width: number, height: number, maxWidth: number, maxHeight: number) {
  const scale = Math.min(1, maxWidth / width, maxHeight / height);
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

/**
 * WebP로 만들고, 결과가 WebP가 아니면 JPEG로 다시 만든다. `maxBytes`를 넘으면 품질을 낮춘다.
 * `forceJpeg`면 처음부터 JPEG(원본이 JPEG로 대체됐으면 썸네일도 같게).
 */
async function encodeFitting(
  deps: ProcessorDeps,
  image: DecodedImage,
  width: number,
  height: number,
  maxBytes: number,
  forceJpeg: boolean,
): Promise<{ blob: Blob; type: ThumbContentType } | null> {
  let type: ThumbContentType = forceJpeg ? 'image/jpeg' : 'image/webp';
  for (const quality of QUALITIES) {
    let blob = await deps.encode(image, width, height, type, quality);
    if (blob && type === 'image/webp' && blob.type !== 'image/webp') {
      type = 'image/jpeg';
      blob = await deps.encode(image, width, height, type, quality);
    }
    if (!blob || blob.type !== type) {
      return null;
    }
    if (blob.size <= maxBytes) {
      return { blob: new Blob([blob], { type }), type };
    }
  }
  return null;
}

export async function processImage(
  file: Blob,
  options: { deps?: ProcessorDeps; limits?: Partial<ProcessorLimits> } = {},
): Promise<ProcessResult> {
  const deps = options.deps ?? defaultDeps;
  const limits = { ...DEFAULT_LIMITS, ...options.limits };

  const format = await detectFormat(file);
  if (!format) {
    return fail('UNSUPPORTED_IMAGE_TYPE');
  }
  const gif = format === 'image/gif';
  if (gif ? file.size > limits.maxUploadBytes : file.size > limits.maxSourceBytes) {
    return fail('IMAGE_TOO_LARGE');
  }

  // 고르는 순간 검사 (T075): GIF 가로·세로·프레임 수, 움직이는 WebP·APNG 안내 — 해독 전에
  const picked = await checkPickedImage(file, limits);
  if (!picked.ok) {
    return fail(picked.code);
  }

  let decoded: DecodedImage;
  try {
    decoded = await deps.decode(file);
  } catch {
    return fail('PROCESSING_FAILED');
  }
  try {
    if (gif && Math.max(decoded.width, decoded.height) > limits.gifMaxSide) {
      return fail('GIF_TOO_LARGE');
    }

    let original: Blob;
    let contentType: ImageContentType;
    let width = decoded.width;
    let height = decoded.height;
    if (gif) {
      original = new Blob([file], { type: 'image/gif' });
      contentType = 'image/gif';
    } else {
      ({ width, height } = fit(decoded.width, decoded.height, limits.longSide, limits.longSide));
      const encoded = await encodeFitting(
        deps,
        decoded,
        width,
        height,
        limits.maxUploadBytes,
        false,
      );
      if (!encoded) {
        return fail('PROCESSING_FAILED');
      }
      original = encoded.blob;
      contentType = encoded.type;
    }

    const thumbSize = fit(width, height, limits.thumbMaxWidth, limits.thumbMaxHeight);
    const thumb = await encodeFitting(
      deps,
      decoded,
      thumbSize.width,
      thumbSize.height,
      limits.maxThumbBytes,
      contentType === 'image/jpeg' && !gif,
    );
    if (!thumb) {
      return fail('PROCESSING_FAILED');
    }
    return {
      ok: true,
      image: {
        blob: original,
        contentType,
        thumb: thumb.blob,
        thumbContentType: thumb.type,
        width,
        height,
        notice: picked.notice,
      },
    };
  } finally {
    decoded.close?.();
  }
}
