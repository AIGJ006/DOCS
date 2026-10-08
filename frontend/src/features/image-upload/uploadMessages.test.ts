import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/client';
import { RETRY_LATER, UPLOAD_MESSAGES, uploadMessageOf } from './uploadMessages';

describe('uploadMessageOf (003 T020, data-model §7)', () => {
  it('아는 code는 data-model §7 문구 그대로', () => {
    expect(uploadMessageOf(new ApiError(409, 'STORAGE_QUOTA_EXCEEDED', 'x'))).toBe(
      '사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요',
    );
    expect(uploadMessageOf(new ApiError(429, 'DAILY_UPLOAD_LIMIT', 'x'))).toBe(
      '오늘은 사진을 200장까지 올릴 수 있어요. 내일 다시 시도해 주세요',
    );
    expect(uploadMessageOf(new ApiError(429, 'TOO_MANY_REQUESTS', 'x'))).toBe(
      '잠시 후 다시 시도해 주세요',
    );
    expect(uploadMessageOf(new ApiError(400, 'IMAGE_REJECTED', 'x'))).toBe(
      '올릴 수 없는 사진이에요',
    );
    expect(uploadMessageOf(new ApiError(400, 'IMAGE_NOT_UPLOADED', 'x'))).toBe(
      '사진이 올라가지 않았어요. 다시 시도해 주세요',
    );
  });

  it('칸 오류는 첫 칸의 code 문구', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
      { field: 'size', code: 'IMAGE_TOO_LARGE', message: '서버 문구' },
    ]);
    expect(uploadMessageOf(error)).toBe('사진은 10MB까지 올릴 수 있어요');
    const type = new ApiError(400, 'VALIDATION_FAILED', 'x', [
      { field: 'contentType', code: 'UNSUPPORTED_IMAGE_TYPE', message: 'y' },
    ]);
    expect(uploadMessageOf(type)).toBe('jpg, png, gif, webp 사진만 올릴 수 있어요');
  });

  it('503은 code와 상관없이 잠시 후 다시 시도 (R9)', () => {
    expect(
      uploadMessageOf(new ApiError(503, 'AUTOSAVE_UNAVAILABLE', '잠시 후 다시 저장할게요')),
    ).toBe(RETRY_LATER);
    expect(uploadMessageOf(new ApiError(500, 'INTERNAL_ERROR', 'x'))).toBe(RETRY_LATER);
    expect(uploadMessageOf(new TypeError('Failed to fetch'))).toBe(RETRY_LATER);
  });

  it('모르는 code는 서버 문구', () => {
    expect(uploadMessageOf(new ApiError(403, 'ACCOUNT_SUSPENDED', '정지된 계정이에요'))).toBe(
      '정지된 계정이에요',
    );
  });

  it('모든 문구 끝에 마침표가 없다', () => {
    for (const message of Object.values(UPLOAD_MESSAGES)) {
      expect(message.endsWith('.')).toBe(false);
    }
  });
});
