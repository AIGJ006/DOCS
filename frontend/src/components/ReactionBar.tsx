import type { ReactNode } from 'react';
import { formatCount } from './formatCount';

/**
 * 태그 아래 반응 줄 (005 T039, FR-033, 31 W-8).
 *
 * 순서는 좋아요 수(맨 앞) → [좋아요] → "조회 N" + 안내 문구 → [신고]다. 조회수는 1만 이상이면 `1.2만`으로 줄인다.
 *
 * (구현 메모) [좋아요]·[신고] 버튼 자체는 이 기능이 만들지 않는다 — 표시 조건은 004 `PostActions`(research R-29),
 * 좋아요 동작은 009, 신고는 014 소유다. `likeButton`·`reportButton` 자리를 두고 그 기능들이 채운다. 009 `LikeButton`이
 * 자리를 채우면 버튼이 숫자를 함께 보이므로 앞의 "♥ N"은 그리지 않는다(작성자 본인 등 버튼이 없을 때만 보인다).
 * 수 형식은 009 `formatCount`를 좋아요·조회수가 함께 쓴다.
 */
export const VIEW_COUNT_NOTICE = '같은 사람은 하루에 한 번만 세요';

export interface ReactionBarProps {
  likeCount: number;
  viewCount: number;
  /** 내가 좋아요를 눌렀는지 (009가 버튼을 채울 때 쓴다) */
  likedByMe?: boolean;
  likeButton?: ReactNode;
  reportButton?: ReactNode;
}

export default function ReactionBar({
  likeCount,
  viewCount,
  likedByMe = false,
  likeButton = null,
  reportButton = null,
}: ReactionBarProps) {
  return (
    <div
      data-testid="reaction-bar"
      data-liked={likedByMe ? 'true' : 'false'}
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '0.75rem',
        margin: '1.5rem 0 0',
        padding: '0.75rem 0',
        borderTop: '1px solid var(--divider, #e9ecef)',
        borderBottom: '1px solid var(--divider, #e9ecef)',
        color: 'var(--muted, #868e96)',
        fontSize: '0.875rem',
      }}
    >
      {likeButton ?? (
        <span data-testid="like-count" aria-label={`좋아요 ${likeCount}`}>
          ♥ {formatCount(likeCount)}
        </span>
      )}
      <span data-testid="view-count" title={VIEW_COUNT_NOTICE} tabIndex={0}>
        조회 {formatCount(viewCount)}
      </span>
      {reportButton}
    </div>
  );
}
