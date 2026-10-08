/**
 * 사진 한 장 올리기 (003 T033, research R1·R12): 처리 → presign → 저장소 PUT 2번(각 30초) → complete.
 *
 * 결과는 셋이다.
 * - `uploaded`: 본문에 `![](image.url)`을 넣는다.
 * - `pending`: 오프라인·네트워크 오류·시간 초과·5xx. 호출자가 원래 파일을 기기에 보관하고 나중에 다시 부른다(US3).
 * - `rejected`: 400·401·403·409·429·처리 실패. 보관하지 않고 `message`를 바로 보인다.
 *
 * 저장소 PUT 403(서명 만료 등)은 presign부터 한 번 다시 한다.
 */
import { ApiError } from '../../api/client';
import { complete, presign, putToStorage, StorageUploadError } from '../../api/images';
import type { UploadedImage } from '../../api/types/images';
import { processImage, type ProcessResult } from './imageProcessor';
import { UPLOAD_MESSAGES, uploadMessageOf } from './uploadMessages';

export type UploadResult =
  | { kind: 'uploaded'; image: UploadedImage }
  | { kind: 'pending' }
  | { kind: 'rejected'; code: string; message: string };

export interface UploadOptions {
  /** 사진 처리 (테스트가 바꾼다) */
  process?: (file: Blob) => Promise<ProcessResult>;
  /** 요청 하나의 시간 제한. 기본 30초 */
  timeoutMs?: number;
  isOnline?: () => boolean;
}

const DEFAULT_TIMEOUT_MS = 30_000;

class RetryPresign extends Error {}

function defaultOnline(): boolean {
  return typeof navigator === 'undefined' || navigator.onLine !== false;
}

async function withTimeout<T>(
  timeoutMs: number,
  run: (signal: AbortSignal) => Promise<T>,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await run(controller.signal);
  } finally {
    clearTimeout(timer);
  }
}

function isRetryable(error: unknown): boolean {
  if (error instanceof TypeError) return true;
  if (
    error instanceof DOMException &&
    (error.name === 'AbortError' || error.name === 'TimeoutError')
  ) {
    return true;
  }
  if (error instanceof ApiError) return error.status >= 500;
  if (error instanceof StorageUploadError) return error.status === 0 || error.status >= 500;
  return false;
}

export async function uploadImage(file: Blob, options: UploadOptions = {}): Promise<UploadResult> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const isOnline = options.isOnline ?? defaultOnline;
  if (!isOnline()) {
    return { kind: 'pending' };
  }

  const processed = await (options.process ?? ((f: Blob) => processImage(f)))(file);
  if (!processed.ok) {
    return { kind: 'rejected', code: processed.code, message: processed.message };
  }
  const image = processed.image;

  const attempt = async (): Promise<UploadedImage> => {
    const ticket = await withTimeout(timeoutMs, (signal) =>
      presign(
        {
          purpose: 'POST',
          contentType: image.contentType,
          size: image.blob.size,
          thumbContentType: image.thumbContentType,
          thumbSize: image.thumb.size,
        },
        signal,
      ),
    );
    try {
      await withTimeout(timeoutMs, (signal) => putToStorage(ticket.upload, image.blob, signal));
      if (ticket.thumbUpload) {
        const thumbTarget = ticket.thumbUpload;
        await withTimeout(timeoutMs, (signal) => putToStorage(thumbTarget, image.thumb, signal));
      }
    } catch (error) {
      if (error instanceof StorageUploadError && error.status === 403) {
        throw new RetryPresign();
      }
      throw error;
    }
    return withTimeout(timeoutMs, (signal) => complete(ticket.imageId, signal));
  };

  try {
    try {
      return { kind: 'uploaded', image: await attempt() };
    } catch (error) {
      if (!(error instanceof RetryPresign)) throw error;
      return { kind: 'uploaded', image: await attempt() };
    }
  } catch (error) {
    if (isRetryable(error)) {
      return { kind: 'pending' };
    }
    if (error instanceof ApiError) {
      return { kind: 'rejected', code: error.code, message: uploadMessageOf(error) };
    }
    return {
      kind: 'rejected',
      code: 'IMAGE_NOT_UPLOADED',
      message: UPLOAD_MESSAGES.IMAGE_NOT_UPLOADED,
    };
  }
}
