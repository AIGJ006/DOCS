/**
 * 프로필 사진 자르기 (FR-049·050, 11 §4-1). 고른 사진에서 정사각형 영역을 골라 256×256으로 다시 그린다 — 캔버스로 새로 인코딩하므로
 * EXIF 등 메타데이터가 사라지고, GIF는 첫 장면만 남는다. 크기·형식의 최종 검사는 003 업로드 complete 단계가 한다.
 */
export const PROFILE_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
export const PROFILE_IMAGE_MAX_BYTES = 10 * 1024 * 1024;
export const PROFILE_OUTPUT_SIZE = 256;

export const INVALID_TYPE_MESSAGE = 'jpg·png·gif·webp 사진만 올릴 수 있어요';
export const TOO_LARGE_MESSAGE = '10MB 이하 사진만 올릴 수 있어요';

/** 고를 수 있는 파일인가. 아니면 이유. */
export function validateProfileFile(file: File): string | null {
  if (!PROFILE_IMAGE_TYPES.includes(file.type)) {
    return INVALID_TYPE_MESSAGE;
  }
  if (file.size > PROFILE_IMAGE_MAX_BYTES) {
    return TOO_LARGE_MESSAGE;
  }
  return null;
}

/**
 * 자를 영역. `x`·`y`는 남는 여백 안에서의 위치(0 = 왼쪽·위, 0.5 = 가운데, 1 = 오른쪽·아래), `zoom`은 1(짧은 변 전체) 이상.
 */
export interface CropState {
  x: number;
  y: number;
  zoom: number;
}

export const INITIAL_CROP: CropState = { x: 0.5, y: 0.5, zoom: 1 };

export function cropRect(
  source: { width: number; height: number },
  crop: CropState,
): { sx: number; sy: number; size: number } {
  const zoom = Math.max(1, crop.zoom);
  const size = Math.min(source.width, source.height) / zoom;
  const clamp = (v: number) => Math.min(1, Math.max(0, v));
  return {
    sx: Math.round((source.width - size) * clamp(crop.x)),
    sy: Math.round((source.height - size) * clamp(crop.y)),
    size: Math.round(size),
  };
}

type CanvasFactory = () => HTMLCanvasElement;

/** 256×256 WebP Blob. WebP를 못 만드는 브라우저(사파리 — PNG를 돌려줌)는 JPEG로 다시 만든다(003 Q4·R14). */
export function renderSquare(
  image: HTMLImageElement,
  crop: CropState,
  createCanvas: CanvasFactory = () => document.createElement('canvas'),
): Promise<Blob> {
  const width = image.naturalWidth || image.width;
  const height = image.naturalHeight || image.height;
  const { sx, sy, size } = cropRect({ width, height }, crop);
  const canvas = createCanvas();
  canvas.width = PROFILE_OUTPUT_SIZE;
  canvas.height = PROFILE_OUTPUT_SIZE;
  const context = canvas.getContext('2d');
  if (!context) {
    return Promise.reject(new Error('canvas 2d unavailable'));
  }
  context.imageSmoothingQuality = 'high';
  context.drawImage(image, sx, sy, size, size, 0, 0, PROFILE_OUTPUT_SIZE, PROFILE_OUTPUT_SIZE);
  const encode = (type: string) =>
    new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('toBlob failed'))),
        type,
        0.9,
      );
    });
  return encode('image/webp').then((blob) =>
    blob.type === 'image/webp' ? blob : encode('image/jpeg'),
  );
}
