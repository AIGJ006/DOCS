import {
  AVATAR_COLORS,
  AVATAR_FALLBACK_COLOR,
  avatarColorIndex,
  avatarInitial,
} from './defaultAvatarColor';

/**
 * 프로필 사진이 없을 때의 기본 아바타 (001 T120, FR-051, R-34). 원 + 닉네임 첫 글자를 SVG로 그린다 — 파일을 요청하지 않는다.
 * 색은 블로그 주소로 정한 8색 중 하나. 닉네임이 바로 옆에 보이므로 화면 읽기 도구에서는 숨긴다.
 *
 * 005가 쓰던 임시 아이콘(`size`만 받음)과 같은 자리에 쓴다 — `nickname`·`handle`이 없으면 글자 없는 회색 원.
 */
export interface DefaultAvatarProps {
  nickname?: string | null;
  handle?: string | null;
  size?: number;
}

export default function DefaultAvatar({ nickname, handle, size = 24 }: DefaultAvatarProps) {
  const initial = avatarInitial(nickname);
  const fill = handle ? AVATAR_COLORS[avatarColorIndex(handle)] : AVATAR_FALLBACK_COLOR;
  return (
    <svg
      data-testid="default-avatar"
      aria-hidden="true"
      focusable="false"
      width={size}
      height={size}
      viewBox="0 0 100 100"
      style={{ display: 'inline-block', flex: `0 0 ${size}px` }}
    >
      {/* SVG fill 속성은 var()를 못 읽어 style로 칠한다 */}
      <circle cx="50" cy="50" r="50" style={{ fill }} />
      {initial && (
        <text
          x="50"
          y="50"
          dy="0.35em"
          textAnchor="middle"
          style={{ fill: 'var(--color-on-fill)' }}
          fontSize="46"
          fontWeight="600"
          fontFamily="system-ui, -apple-system, 'Segoe UI', sans-serif"
        >
          {initial}
        </text>
      )}
    </svg>
  );
}
