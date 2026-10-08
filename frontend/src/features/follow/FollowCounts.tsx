import { Link } from 'react-router-dom';
import './follow.css';

export interface FollowCountsProps {
  handle: string;
  publicPostCount: number;
  followerCount: number;
  followingCount: number;
}

function count(value: number): string {
  return value.toLocaleString('ko-KR');
}

/**
 * 블로그 머리말의 수 한 줄 (010 T037, FR-013, 24 §2-1): "공개 글 24 · 팔로워 12 · 팔로잉 30". 팔로워·팔로잉은 목록 링크다.
 * 숫자는 `toLocaleString`(1,234).
 */
export default function FollowCounts({
  handle,
  publicPostCount,
  followerCount,
  followingCount,
}: FollowCountsProps) {
  return (
    <p className="follow-counts" data-testid="follow-counts">
      <span>공개 글 {count(publicPostCount)}</span>
      <span aria-hidden="true">·</span>
      <Link to={`/@${handle}/followers`}>팔로워 {count(followerCount)}</Link>
      <span aria-hidden="true">·</span>
      <Link to={`/@${handle}/following`}>팔로잉 {count(followingCount)}</Link>
    </p>
  );
}
