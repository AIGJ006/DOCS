import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import {
  INITIAL_CROP,
  PROFILE_IMAGE_TYPES,
  cropRect,
  renderSquare,
  validateProfileFile,
  type CropState,
} from '../features/profile/profileImageCrop';

const FRAME = 200;

export interface ProfileImageCropperProps {
  /** 256×256으로 다시 그린 결과. 업로드·연결은 부른 쪽이 [저장] 때 한다(FR-049 — 저장 전까지 연결하지 않음). */
  onCropped: (blob: Blob) => void;
  onCancel: () => void;
  /** 테스트용: 결과 그리기. 기본은 캔버스. */
  renderer?: (image: HTMLImageElement, crop: CropState) => Promise<Blob>;
}

/**
 * 프로필 사진 자르기 (001 T121, FR-049·050, 11 §4-1). jpg·png·gif·webp, 10MB 이하 파일을 고르면 `blob:` 주소로 미리 보고,
 * 정사각형 위치(가로·세로)와 확대를 고른 뒤 256×256으로 다시 그린다.
 */
export default function ProfileImageCropper({
  onCropped,
  onCancel,
  renderer = renderSquare,
}: ProfileImageCropperProps) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [natural, setNatural] = useState<{ width: number; height: number } | null>(null);
  const [crop, setCrop] = useState<CropState>(INITIAL_CROP);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const imageRef = useRef<HTMLImageElement>(null);

  useEffect(
    () => () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    },
    [previewUrl],
  );

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    const invalid = validateProfileFile(file);
    if (invalid) {
      setError(invalid);
      return;
    }
    setError(null);
    setNatural(null);
    setCrop(INITIAL_CROP);
    setPreviewUrl(URL.createObjectURL(file));
  }

  async function onUse() {
    const image = imageRef.current;
    if (!image) {
      return;
    }
    setWorking(true);
    try {
      onCropped(await renderer(image, crop));
    } catch {
      setError('사진을 읽지 못했어요. 다른 사진을 골라 주세요');
    } finally {
      setWorking(false);
    }
  }

  const rect = natural ? cropRect(natural, crop) : null;
  const scale = rect ? FRAME / rect.size : 1;

  return (
    <div className="profile-cropper">
      <label className="file-button">
        사진 고르기
        <input
          type="file"
          accept={PROFILE_IMAGE_TYPES.join(',')}
          onChange={onFile}
          className="visually-hidden"
        />
      </label>
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
      {previewUrl && (
        <>
          <div
            className="cropper-frame"
            style={{
              width: FRAME,
              height: FRAME,
              overflow: 'hidden',
              position: 'relative',
              borderRadius: '50%',
            }}
          >
            <img
              ref={imageRef}
              src={previewUrl}
              alt="고른 사진 미리보기"
              onLoad={(e) =>
                setNatural({
                  width: e.currentTarget.naturalWidth,
                  height: e.currentTarget.naturalHeight,
                })
              }
              style={
                rect && natural
                  ? {
                      position: 'absolute',
                      maxWidth: 'none',
                      width: natural.width * scale,
                      height: natural.height * scale,
                      left: -rect.sx * scale,
                      top: -rect.sy * scale,
                    }
                  : { width: '100%', height: '100%', objectFit: 'cover' }
              }
            />
          </div>
          <div className="cropper-controls">
            <label>
              가로 위치
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={crop.x}
                onChange={(e) => setCrop({ ...crop, x: Number(e.target.value) })}
              />
            </label>
            <label>
              세로 위치
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={crop.y}
                onChange={(e) => setCrop({ ...crop, y: Number(e.target.value) })}
              />
            </label>
            <label>
              확대
              <input
                type="range"
                min={1}
                max={4}
                step={0.05}
                value={crop.zoom}
                onChange={(e) => setCrop({ ...crop, zoom: Number(e.target.value) })}
              />
            </label>
          </div>
          <div className="cropper-actions">
            <button type="button" className="primary" onClick={onUse} disabled={working}>
              이 영역 사용
            </button>
            <button type="button" onClick={onCancel}>
              취소
            </button>
          </div>
        </>
      )}
    </div>
  );
}
