import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import DefaultAvatar from './DefaultAvatar';
import { AVATAR_COLORS, avatarColorIndex, avatarInitial, fnv1a32 } from './defaultAvatarColor';

/** 기본 아바타 (FR-051, R-34). 색 번호 = 블로그 주소 UTF-8 바이트 FNV-1a 32비트 mod 8. */
describe('DefaultAvatar', () => {
  it.each([
    ['', 2166136261, 5],
    ['a', 3826002220, 4],
    ['kim755030', 2698216738, 2],
    ['go-minseo', 128024899, 3],
    ['gi-octocat', 536447313, 1],
  ])('FNV-1a 고정 예시 %s → %d (색 %d)', (handle, hash, index) => {
    expect(fnv1a32(handle)).toBe(hash);
    expect(avatarColorIndex(handle)).toBe(index);
  });

  it('한글도 UTF-8 바이트로 센다', () => {
    expect(avatarColorIndex('김민서')).toBe(fnv1a32('김민서') % 8);
    expect(fnv1a32('김민서')).not.toBe(fnv1a32('김민'));
  });

  it('닉네임 첫 글자, 영문은 대문자', () => {
    expect(avatarInitial('kim')).toBe('K');
    expect(avatarInitial('김민서')).toBe('김');
    expect(avatarInitial('  minseo')).toBe('M');
    expect(avatarInitial('😀웃음')).toBe('😀');
    expect(avatarInitial(null)).toBe('');
  });

  it('8색 모두 흰 글자와 대비 4.5:1 이상 (라이트·다크 공통)', () => {
    expect(AVATAR_COLORS).toHaveLength(8);
    for (const color of AVATAR_COLORS) {
      expect(contrastWithWhite(color)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('SVG로 그리고 파일을 요청하지 않는다', () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const { container } = render(<DefaultAvatar nickname="kim" handle="kim755030" size={48} />);
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg).toHaveAttribute('width', '48');
    expect(svg?.querySelector('circle')).toHaveAttribute('fill', AVATAR_COLORS[2]);
    expect(svg?.querySelector('text')?.textContent).toBe('K');
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByTestId('default-avatar')).toHaveAttribute('aria-hidden', 'true');
    expect(fetchSpy).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('닉네임·주소가 없으면(탈퇴한 회원 등) 글자 없이 회색 원', () => {
    const { container } = render(<DefaultAvatar />);
    expect(container.querySelector('text')).toBeNull();
    expect(container.querySelector('circle')).toBeInTheDocument();
  });
});

function contrastWithWhite(hex: string): number {
  const channels = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  const luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
  return 1.05 / (luminance + 0.05);
}
