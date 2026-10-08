import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { ViewerFlags } from '../api/types/viewerFlags';
import ResendVerificationButton from '../features/auth-gate/ResendVerificationButton';
import { loginPathFor } from '../features/auth-gate/authGate';
import { noticeText, type LikeNotice } from '../features/like/likeMessages';
import { useLikeToggle } from '../features/like/useLikeToggle';
import '../features/like/like.css';
import { formatCount } from './formatCount';

export interface LikeButtonProps {
  postId: number;
  /** 상세 응답의 `viewer` (서버 판정) */
  viewer: ViewerFlags;
  /** 상세 응답의 `viewer.likedByMe` */
  initialLiked: boolean;
  /** 상세 응답의 `likeCount` */
  initialCount: number;
}

/**
 * 좋아요 버튼 (009 T018·T023, FR-013~017, research R10). 005 `ReactionBar`의 `likeButton` 자리에 들어간다.
 *
 * - 작성자 본인: 버튼 없이 ♥ + 수(관리자도 자기 글이면 작성자).
 * - 그 밖(비회원·인증 전 회원 포함, 01 H6): ♡/♥ 버튼 + 수. `aria-pressed`, 이름 "좋아요 (N)"/"좋아요 취소 (N)", 모양으로도 상태를
 *   구분하고 Enter·Space로 누른다(기본 `<button>`).
 * - 비회원이 누르면 "로그인하고 좋아요를 눌러 보세요 [로그인]"(`/login?returnTo={지금 주소}`), 인증 전이면 "이메일 인증 후 누를 수
 *   있어요" + [인증 메일 다시 보내기]. 요청은 보내지 않고, 로그인 뒤 돌아와도 자동으로 누르지 않는다(FR-014).
 * - 안내는 `role="status"` 한 줄로 알린다.
 *
 * 버튼 숨김·안내는 보조이고 판정은 항상 서버가 한다(42 P-1) — 401·403 응답도 같은 안내로 바꾼다(`useLikeToggle`).
 */
export default function LikeButton({
  postId,
  viewer,
  initialLiked,
  initialCount,
}: LikeButtonProps) {
  const location = useLocation();
  const like = useLikeToggle({ postId, initialLiked, initialCount });
  const [gateNotice, setGateNotice] = useState<LikeNotice | null>(null);

  if (viewer.isAuthor) {
    return (
      <span data-testid="like-count" aria-label={`좋아요 ${initialCount}`}>
        ♥ {formatCount(initialCount)}
      </span>
    );
  }

  function onClick() {
    if (!viewer.loggedIn) {
      setGateNotice({ kind: 'login' });
      return;
    }
    if (!viewer.emailVerified) {
      setGateNotice({ kind: 'verifyEmail' });
      return;
    }
    setGateNotice(null);
    like.toggle();
  }

  const notice = gateNotice ?? like.notice;
  const label = like.liked ? `좋아요 취소 (${like.count})` : `좋아요 (${like.count})`;

  return (
    <span className="like-area" data-testid="like-area">
      <button
        type="button"
        className="like-button"
        aria-pressed={like.liked}
        aria-label={label}
        onClick={onClick}
      >
        <span className="like-button__icon" aria-hidden="true">
          {like.liked ? '♥' : '♡'}
        </span>
        <span data-testid="like-count">{formatCount(like.count)}</span>
      </button>
      <span role="status" className="like-notice">
        {notice ? (
          <>
            <span>{noticeText(notice)}</span>
            {notice.kind === 'login' ? (
              <Link to={loginPathFor(location.pathname + location.search)}>로그인</Link>
            ) : null}
            {notice.kind === 'verifyEmail' ? <ResendVerificationButton /> : null}
          </>
        ) : null}
      </span>
    </span>
  );
}
