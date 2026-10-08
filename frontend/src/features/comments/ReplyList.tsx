import type { ReactNode } from 'react';
import type { RepliesState } from './useCommentThread';
import { COMMENT_TEXT } from './commentMessages';
import LoadButton from './LoadButton';

export interface ReplyListProps {
  rootId: number;
  replies: RepliesState | undefined;
  onMore: () => void;
  /** 답글 하나 그리기 */
  renderReply: (id: number) => ReactNode;
}

/**
 * 최상위 댓글 아래 답글 (007 T023, FR-017). 처음 3개(또는 around 대상까지)를 보이고 [답글 N개 더 보기]로 20개씩 펼친다.
 * N = 전체 답글 수(숨긴 답글 포함) − 보이는 수.
 */
export default function ReplyList({ rootId, replies, onMore, renderReply }: ReplyListProps) {
  if (!replies) {
    return null;
  }
  const remaining = replies.total - replies.ids.length;
  const hasMore = replies.nextCursor !== null && remaining > 0;
  if (replies.ids.length === 0 && !hasMore) {
    return null;
  }
  return (
    <div className="comment-replies">
      <ol data-testid={`replies-${rootId}`} className="comment-list comment-reply-list">
        {replies.ids.map((id) => (
          <li key={id}>{renderReply(id)}</li>
        ))}
      </ol>
      {hasMore ? (
        <LoadButton
          label={COMMENT_TEXT.moreReplies(remaining)}
          state={replies.load}
          onClick={onMore}
        />
      ) : null}
    </div>
  );
}
