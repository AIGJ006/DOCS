/**
 * 사진 업로드 오류 code → 화면 문구 (003 data-model §7, research R9·R12). 서버 문구와 글자까지 같고 끝 마침표가 없다.
 * 오프라인에서도 같은 문구를 보이도록 화면에 표를 둔다.
 *
 * 503은 code와 상관없이 "잠시 후 다시 시도해 주세요"다 — 002 `RedisGuard`가 Redis 메모리 부족 때
 * `AUTOSAVE_UNAVAILABLE`("잠시 후 다시 저장할게요")을 줄 수 있어서다(R9).
 */
import { ApiError } from '../../api/client';

export const UPLOAD_MESSAGES = {
  UNSUPPORTED_IMAGE_TYPE: 'jpg, png, gif, webp 사진만 올릴 수 있어요',
  IMAGE_TOO_LARGE: '사진은 10MB까지 올릴 수 있어요',
  THUMBNAIL_TOO_LARGE: '사진을 처리하지 못했어요',
  THUMBNAIL_REQUIRED: '사진을 처리하지 못했어요',
  THUMBNAIL_NOT_ALLOWED: '사진을 처리하지 못했어요',
  STORAGE_QUOTA_EXCEEDED:
    '사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요',
  DAILY_UPLOAD_LIMIT: '오늘은 사진을 200장까지 올릴 수 있어요. 내일 다시 시도해 주세요',
  TOO_MANY_REQUESTS: '잠시 후 다시 시도해 주세요',
  IMAGE_NOT_UPLOADED: '사진이 올라가지 않았어요. 다시 시도해 주세요',
  IMAGE_REJECTED: '올릴 수 없는 사진이에요',
  GIF_TOO_LARGE: 'GIF는 가로·세로 1920px까지 올릴 수 있어요',
  /** 화면 전용(고르는 순간 검사). 서버 complete 거부는 IMAGE_REJECTED 문구다 */
  GIF_TOO_MANY_FRAMES: 'GIF는 프레임 300장까지 올릴 수 있어요',
  EMAIL_NOT_VERIFIED: '이메일 인증 후 이용할 수 있어요',
  LOGIN_REQUIRED: '로그인이 필요해요',
} as const;

/** 503·알 수 없는 오류 문구. */
export const RETRY_LATER = '잠시 후 다시 시도해 주세요';
/** 처리(줄이기·썸네일)에 실패했을 때. */
export const PROCESSING_FAILED = '사진을 처리하지 못했어요';
/** 다시 시도 중 즉시 안내 대상 오류가 났을 때 (R12). */
export const PENDING_FAILED = '업로드하지 못한 사진이 있어요';
/** FR-038: 움직임을 살리는 형식은 GIF뿐. */
export const ANIMATION_NOTICE =
  '움직이는 WebP·APNG는 첫 장면만 남아요. 움직이는 사진은 GIF로 올려 주세요';

/** 서버 오류 → 문구. 칸 오류면 첫 칸의 문구, 아는 code면 표의 문구, 아니면 서버 문구. */
export function uploadMessageOf(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503 || error.status >= 500) {
      return RETRY_LATER;
    }
    if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) {
      const first = error.errors[0];
      return (UPLOAD_MESSAGES as Record<string, string>)[first.code] ?? first.message;
    }
    const known = (UPLOAD_MESSAGES as Record<string, string>)[error.code];
    if (known) {
      return known;
    }
    return error.message || RETRY_LATER;
  }
  return RETRY_LATER;
}
