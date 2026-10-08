import type { LastActive } from '../api/friends';
import { lastActiveText } from '../features/friends/lastActiveText';

/**
 * 친구의 최근 활동 (001 T142, FR-060). 서버가 `lastActive`를 보낸 경우(친구 + 둘 다 공개 + 값 있음)에만 그린다. 블로그 머리말
 * 배치는 specs/005.
 */
export default function LastActiveBadge({ value }: { value: LastActive | undefined | null }) {
  const text = lastActiveText(value);
  if (!text) {
    return null;
  }
  return <span className="last-active-badge">최근 활동 {text}</span>;
}
