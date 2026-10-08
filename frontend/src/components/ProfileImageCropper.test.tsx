import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ProfileImageCropper from './ProfileImageCropper';
import {
  PROFILE_IMAGE_MAX_BYTES,
  PROFILE_OUTPUT_SIZE,
  cropRect,
  renderSquare,
  validateProfileFile,
} from '../features/profile/profileImageCrop';

function file(name: string, type: string, size: number): File {
  const f = new File(['x'], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
}

describe('profileImageCrop (FR-049·050)', () => {
  it('jpg·png·gif·webp, 10MB 이하만 받는다', () => {
    expect(validateProfileFile(file('a.jpg', 'image/jpeg', 1000))).toBeNull();
    expect(validateProfileFile(file('a.png', 'image/png', 1000))).toBeNull();
    expect(validateProfileFile(file('a.gif', 'image/gif', 1000))).toBeNull();
    expect(validateProfileFile(file('a.webp', 'image/webp', PROFILE_IMAGE_MAX_BYTES))).toBeNull();
    expect(validateProfileFile(file('a.bmp', 'image/bmp', 1000))).toBe(
      'jpg·png·gif·webp 사진만 올릴 수 있어요',
    );
    expect(validateProfileFile(file('a.svg', 'image/svg+xml', 1000))).not.toBeNull();
    expect(validateProfileFile(file('big.jpg', 'image/jpeg', PROFILE_IMAGE_MAX_BYTES + 1))).toBe(
      '10MB 이하 사진만 올릴 수 있어요',
    );
  });

  it('가운데 정사각형, 확대하면 더 작은 영역, 위치는 이미지 안으로 제한', () => {
    expect(cropRect({ width: 400, height: 200 }, { x: 0.5, y: 0.5, zoom: 1 })).toEqual({
      sx: 100,
      sy: 0,
      size: 200,
    });
    expect(cropRect({ width: 400, height: 200 }, { x: 0.5, y: 0.5, zoom: 2 })).toEqual({
      sx: 150,
      sy: 50,
      size: 100,
    });
    expect(cropRect({ width: 400, height: 200 }, { x: 0, y: 0, zoom: 1 })).toEqual({
      sx: 0,
      sy: 0,
      size: 200,
    });
    expect(cropRect({ width: 400, height: 200 }, { x: 1, y: 1, zoom: 1 })).toEqual({
      sx: 200,
      sy: 0,
      size: 200,
    });
  });

  it('256×256 캔버스에 그려 Blob을 만든다 (메타데이터 없는 새 인코딩)', async () => {
    const drawImage = vi.fn();
    const canvas = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage, imageSmoothingQuality: 'low' }),
      toBlob: (cb: (blob: Blob | null) => void, type: string) =>
        cb(new Blob(['png'], { type: type === 'image/webp' ? 'image/webp' : 'image/png' })),
    };
    const source = { width: 800, height: 600 } as unknown as HTMLImageElement;
    const blob = await renderSquare(
      source,
      { x: 0.5, y: 0.5, zoom: 1 },
      () => canvas as unknown as HTMLCanvasElement,
    );
    expect(canvas.width).toBe(PROFILE_OUTPUT_SIZE);
    expect(canvas.height).toBe(256);
    expect(drawImage).toHaveBeenCalledWith(source, 100, 0, 600, 600, 0, 0, 256, 256);
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toBe('image/webp');
  });
});

describe('ProfileImageCropper', () => {
  it('허용되지 않는 파일은 고르지 않고 이유를 보인다', async () => {
    const onCropped = vi.fn();
    const user = userEvent.setup({ applyAccept: false });
    render(<ProfileImageCropper onCropped={onCropped} onCancel={() => undefined} />);
    await user.upload(screen.getByLabelText('사진 고르기'), file('a.bmp', 'image/bmp', 100));
    expect(await screen.findByRole('alert')).toHaveTextContent('jpg·png·gif·webp');
    expect(onCropped).not.toHaveBeenCalled();
  });

  it('고른 사진을 blob: 미리보기로 보이고 [이 영역 사용]으로 256×256 결과를 넘긴다', async () => {
    const createObjectURL = vi.fn(() => 'blob:preview-1');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }));
    const result = new Blob(['out'], { type: 'image/webp' });
    const render256 = vi.fn(async () => result);
    const onCropped = vi.fn();
    const user = userEvent.setup();
    render(
      <ProfileImageCropper onCropped={onCropped} onCancel={() => undefined} renderer={render256} />,
    );
    await user.upload(screen.getByLabelText('사진 고르기'), file('me.png', 'image/png', 2000));
    const preview = await screen.findByRole('img', { name: '고른 사진 미리보기' });
    expect(preview).toHaveAttribute('src', 'blob:preview-1');
    await user.click(screen.getByRole('button', { name: '이 영역 사용' }));
    await waitFor(() => expect(onCropped).toHaveBeenCalledWith(result));
    expect(render256).toHaveBeenCalledWith(preview, { x: 0.5, y: 0.5, zoom: 1 });
    vi.unstubAllGlobals();
  });
});
