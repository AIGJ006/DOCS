import { apiPost } from '../../api/client';

/**
 * 프로필 사진 업로드 (003 흐름): `POST /api/images/presign {purpose: PROFILE}` → 저장소로 직접 PUT → `POST
 * /api/images/{id}/complete` → `imageId`. 연결은 하지 않는다 — `PATCH /api/me/profile {profileImageId}`가 한다(FR-049).
 *
 * presign 응답 모양(`imageId`·`uploadUrl`·`uploadHeaders`)은 003 계약이 아직 없어 001이 가정한 것이다(003 구현 때 맞춘다).
 */
interface PresignResponse {
  imageId: number;
  uploadUrl: string;
  uploadHeaders?: Record<string, string>;
}

export async function uploadProfileImage(blob: Blob): Promise<number> {
  // WebP로 인코딩하지 못하는 브라우저는 PNG를 돌려준다 — 실제 형식으로 신고한다.
  const contentType = blob.type || 'image/webp';
  const presign = await apiPost<PresignResponse>('/api/images/presign', {
    purpose: 'PROFILE',
    contentType,
    size: blob.size,
  });
  const put = await fetch(presign.uploadUrl, {
    method: 'PUT',
    body: blob,
    credentials: 'omit',
    headers: { 'Content-Type': contentType, ...(presign.uploadHeaders ?? {}) },
  });
  if (!put.ok) {
    throw new Error(`upload ${put.status}`);
  }
  await apiPost(`/api/images/${presign.imageId}/complete`);
  return presign.imageId;
}
