import { complete, presign, putToStorage } from '../../api/images';
import type { ImageContentType } from '../../api/types/images';

/**
 * 프로필 사진 업로드 (003 흐름, contracts/openapi.yaml): `POST /api/images/presign {purpose: PROFILE}` → 저장소로 직접
 * PUT(응답의 `upload.headers` 그대로, 자격 증명 없음) → `POST /api/images/{id}/complete`(256×256 검사) → `imageId`.
 * 연결은 하지 않는다 — `PATCH /api/me/profile {profileImageId}`가 한다(FR-049).
 *
 * 형식은 WebP, 사파리처럼 WebP를 못 만드는 브라우저는 JPEG다(`renderSquare`가 대체한다).
 */
export async function uploadProfileImage(blob: Blob): Promise<number> {
  const contentType: ImageContentType = blob.type === 'image/jpeg' ? 'image/jpeg' : 'image/webp';
  const ticket = await presign({ purpose: 'PROFILE', contentType, size: blob.size });
  await putToStorage(ticket.upload, blob);
  await complete(ticket.imageId);
  return ticket.imageId;
}
