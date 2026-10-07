/**
 * 프로필 사진이 없을 때의 기본 아이콘 (005 임시).
 *
 * (구현 메모) 001 T120 `DefaultAvatar`가 아직 없어 005가 임시로 둔다 — 001이 만들면 이 파일을 지우고 그것을 쓴다.
 * 글자 없는 장식이므로 화면 읽기 도구에서 숨긴다.
 */
export interface DefaultAvatarProps {
  size?: number;
}

export default function DefaultAvatar({ size = 24 }: DefaultAvatarProps) {
  return (
    <span
      data-testid="default-avatar"
      aria-hidden="true"
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        flex: `0 0 ${size}px`,
        borderRadius: '50%',
        background: 'var(--card-avatar-bg, #dee2e6)',
      }}
    />
  );
}
