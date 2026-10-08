import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { Visibility } from '../api/posts';
import type { ViewerFlags } from '../api/types/viewerFlags';
import AuthPrompt from '../features/auth-gate/AuthPrompt';
import { useAuthGate } from '../features/auth-gate/useAuthGate';
import VisibilitySelect from '../features/visibility/VisibilitySelect';

export interface PostActionsProps {
  /** 005 상세 응답의 `viewer` (서버 판정) */
  viewer: ViewerFlags;
  postId: number;
  visibility: Visibility;
  likeCount: number;
  /** [수정] 주소 (002 에디터) */
  editPath: string;
  /** 숨긴 글이면 관리자에게 [숨김 해제] */
  hidden?: boolean;
  /** 공개 범위를 바꾼 뒤 (상세 다시 부르기 등) */
  onVisibilitySaved?: () => void;
  /** [삭제] 자리 — 006 확인 창 부품. 없으면 `onDelete`를 부르는 [삭제] */
  deleteControl?: ReactNode;
  onDelete?: () => void;
  /** [좋아요] (009) */
  onLike?: () => void | Promise<void>;
  /** [신고] (014) */
  onReport?: () => void | Promise<void>;
  /** [팔로우] (010) — 주지 않으면 버튼 없음 */
  onFollow?: () => void | Promise<void>;
  /** [숨김]·[숨김 해제] (014, 관리자) */
  onHide?: () => void | Promise<void>;
  onUnhide?: () => void | Promise<void>;
}

/**
 * 글 상세 버튼 표시 규칙 (004 T061, FR-045, US6, research R-29).
 *
 * - 작성자(관리자도 자기 글이면 작성자): [수정]·[공개 범위 ▾](`VisibilitySelect` 즉시 저장)·[삭제]와 좋아요 수만. [좋아요]·[신고]·
 *   [팔로우]는 없다.
 * - 그 밖(비회원·인증 전 회원 포함): [좋아요]·[신고](·[팔로우]). 비회원이 누르면 로그인 안내, 인증 전 회원이면 이메일 인증 안내만 나오고
 *   요청을 보내지 않는다. 로그인 뒤 돌아와도 자동으로 눌리지 않는다(H6). 서버가 거부하면 `useAuthGate`가 안내한다.
 * - 관리자: [숨김]/[숨김 해제]만 더한다. 남의 글 [수정]·[삭제]는 없다.
 *
 * 버튼 숨김은 보조이고 판정은 항상 서버가 한다(42 P-1).
 */
export default function PostActions({
  viewer,
  postId,
  visibility,
  likeCount,
  editPath,
  hidden = false,
  onVisibilitySaved,
  deleteControl,
  onDelete,
  onLike,
  onReport,
  onFollow,
  onHide,
  onUnhide,
}: PostActionsProps) {
  const gate = useAuthGate({ unauthorized: 'prompt' });

  /** 회원 행동: 비회원·인증 전이면 안내만, 아니면 실행하고 거부는 공통 안내로 */
  function memberAction(action: (() => void | Promise<void>) | undefined) {
    return async () => {
      if (!viewer.loggedIn) {
        gate.show('login');
        return;
      }
      if (!viewer.emailVerified) {
        gate.show('verify-email');
        return;
      }
      try {
        await action?.();
      } catch (caught) {
        if (!gate.handle(caught)) {
          throw caught;
        }
      }
    };
  }

  async function adminAction(action: (() => void | Promise<void>) | undefined) {
    try {
      await action?.();
    } catch (caught) {
      if (!gate.handle(caught)) {
        throw caught;
      }
    }
  }

  return (
    <div className="post-actions" data-testid="post-actions">
      {viewer.isAuthor ? (
        <>
          <Link to={editPath}>수정</Link>
          <VisibilitySelect postId={postId} value={visibility} onSaved={onVisibilitySaved} />
          {deleteControl ?? (
            <button type="button" onClick={onDelete}>
              삭제
            </button>
          )}
          <span data-testid="post-actions-like-count" aria-label={`좋아요 ${likeCount}`}>
            ♥ {likeCount.toLocaleString('ko-KR')}
          </span>
        </>
      ) : (
        <>
          <button type="button" onClick={() => void memberAction(onLike)()}>
            좋아요
          </button>
          <button type="button" onClick={() => void memberAction(onReport)()}>
            신고
          </button>
          {onFollow ? (
            <button type="button" onClick={() => void memberAction(onFollow)()}>
              팔로우
            </button>
          ) : null}
        </>
      )}
      {viewer.isAdmin ? (
        hidden ? (
          <button type="button" onClick={() => void adminAction(onUnhide)}>
            숨김 해제
          </button>
        ) : (
          <button type="button" onClick={() => void adminAction(onHide)}>
            숨김
          </button>
        )
      ) : null}
      {gate.prompt ? (
        <AuthPrompt kind={gate.prompt} loginPath={gate.loginPath} onClose={gate.dismiss} />
      ) : null}
    </div>
  );
}
