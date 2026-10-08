/**
 * 댓글 영역 자리 (005 T041, FR-035, research R-33).
 *
 * (구현 메모) 007 댓글 기능이 댓글 목록 컴포넌트를 만들면 이 자리에서 `postId`·`aroundCommentId`를 넘겨
 * `GET /api/posts/{postId}/comments[?around=]`를 상세 API와 동시에 부르게 한다. 그때까지는 머리말만 보여준다.
 */
export interface CommentSectionSlotProps {
  postId: number;
  commentCount: number;
  /** 주소의 `?comment=` 값 (알림 링크로 들어온 댓글, 21 §6 `around`) */
  aroundCommentId?: string | null;
}

export default function CommentSectionSlot({
  postId,
  commentCount,
  aroundCommentId = null,
}: CommentSectionSlotProps) {
  return (
    <section
      data-testid="comment-section"
      data-post-id={postId}
      data-around-comment={aroundCommentId ?? undefined}
      aria-labelledby="comments-heading"
      style={{ marginTop: '2rem' }}
    >
      <h2 id="comments-heading" style={{ fontSize: '1rem', margin: '0 0 0.75rem' }}>
        댓글 {commentCount}
      </h2>
    </section>
  );
}
