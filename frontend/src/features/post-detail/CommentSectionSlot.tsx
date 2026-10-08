import type { CommentPage } from '../../api/types/comments';
import type { ViewerFlags } from '../../api/types/viewerFlags';
import CommentSection from '../comments/CommentSection';

/**
 * 댓글 영역 자리 (005 T041, FR-035, research R-33). 007 `CommentSection`을 그린다 — 머리말 "댓글 N"(상세의 `commentCount`),
 * 목록·입력칸. 첫 페이지 요청은 글 상세가 상세 요청과 동시에 시작해 `initialPage`로 넘긴다(007 Clarifications Q1).
 */
export interface CommentSectionSlotProps {
  postId: number;
  commentCount: number;
  viewer: ViewerFlags;
  /** 주소의 `?comment=` 값 (알림 링크로 들어온 댓글, 21 §6 `around`) */
  aroundCommentId?: string | null;
  initialPage?: Promise<CommentPage> | null;
}

export default function CommentSectionSlot({
  postId,
  commentCount,
  viewer,
  aroundCommentId = null,
  initialPage = null,
}: CommentSectionSlotProps) {
  return (
    <CommentSection
      postId={postId}
      commentCount={commentCount}
      viewer={viewer}
      aroundCommentId={aroundCommentId}
      initialPage={initialPage}
    />
  );
}
