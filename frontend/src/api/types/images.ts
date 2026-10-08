/** 사진 업로드 API 타입 (003 contracts/openapi.yaml). */

export type ImagePurpose = 'POST' | 'PROFILE';
export type ImageContentType = 'image/jpeg' | 'image/png' | 'image/gif' | 'image/webp';
export type ThumbContentType = 'image/webp' | 'image/jpeg';

/** 업로드 준비 요청. 원래 파일 이름 칸은 없다(FR-009). */
export interface PresignRequest {
  purpose: ImagePurpose;
  contentType: ImageContentType;
  /** 실제로 올릴 원본 바이트 수 (Q3) */
  size: number;
  /** POST면 필수, PROFILE이면 없음 */
  thumbContentType?: ThumbContentType;
  thumbSize?: number;
}

/** 브라우저가 올릴 주소. `headers`는 서명에 포함되어 있어 그대로 붙인다. */
export interface UploadTarget {
  url: string;
  method: 'PUT';
  headers: Record<string, string>;
}

export interface ImageUploadTicket {
  imageId: number;
  upload: UploadTarget;
  thumbUpload: UploadTarget | null;
  expiresAt: string;
}

export interface UploadedImage {
  imageId: number;
  /** 본문에 넣는 절대 주소 (지금 공개 주소 + 키) */
  url: string;
  thumbUrl: string | null;
  contentType: ImageContentType;
  width: number;
  height: number;
  sizeBytes: number;
}

export interface StorageLimits {
  maxUploadBytes: number;
  maxThumbBytes: number;
  maxSourceBytes: number;
  longSide: number;
  thumbMaxWidth: number;
  gifMaxSide: number;
  gifMaxFrames: number;
}

export interface StorageUsage {
  usedBytes: number;
  quotaBytes: number;
  /** Redis 장애면 null */
  todayCount: number | null;
  dailyLimit: number;
  limits: StorageLimits;
}
