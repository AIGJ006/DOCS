import { useEffect, useState, type ReactNode } from 'react';
import type { CommentPage, CommentView } from '../../api/types/comments';
import type { ViewerFlags } from '../../api/types/viewerFlags';
import CommentForm from './CommentForm';
import CommentItem from './CommentItem';
import { COMMENT_TEXT } from './commentMessages';
import LoadButton, { LoadFailed } from './LoadButton';
import ReplyList from './ReplyList';
import { useCommentThread } from './useCommentThread';
import './comments.css';

export interface CommentSectionProps {
  postId: number;
  /** 상세 응답의 `commentCount` — 머리말 "댓글 N"의 시작값 */
  commentCount: number;
  viewer: ViewerFlags;
  /** 주소의 `?comment=` 값 (알림 링크, FR-025) */
  aroundCommentId?: string | null;
  /** 글 상세가 상세 요청과 동시에 시작한 첫 페이지 요청 (Clarifications Q1) */
  initialPage?: Promise<CommentPage> | null;
}

/** 강조 시간 (FR-025 "잠깐") */
export const FOCUS_MS = 2000;

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * 글 상세의 댓글 영역 (007 T023·T024·T034·T053, US1·2·3·5).
 *
 * 머리말 "댓글 N" → [이전 댓글 보기](around로 들어왔을 때) → 최상위 목록(각 답글 목록) → [댓글 더 보기] → 입력칸 순서다. 첫 페이지
 * 불러오기가 실패해도 이 영역만 "불러오지 못했어요 [다시 시도]"를 보이고 본문은 그대로다.
 */
export default function CommentSection({
  postId,
  commentCount,
  viewer,
  aroundCommentId = null,
  initialPage = null,
}: CommentSectionProps) {
  const thread = useCommentThread({ postId, commentCount, aroundCommentId, initialPage });
  const { state, clearFocus } = thread;
  const [replyTarget, setReplyTarget] = useState<CommentView | null>(null);

  // around 대상으로 스크롤하고 잠깐 강조한다 (prefers-reduced-motion이면 애니메이션 없이 테두리만 — comments.css)
  const focusId = state.focusId;
  const ready = state.first === 'ready';
  useEffect(() => {
    if (!ready || focusId === null) {
      return undefined;
    }
    const target = document.getElementById(`comment-${focusId}`);
    target?.scrollIntoView({
      block: 'center',
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    });
    const timer = window.setTimeout(clearFocus, FOCUS_MS);
    return () => window.clearTimeout(timer);
  }, [ready, focusId, clearFocus]);

  function onCreated(view: CommentView) {
    thread.addMine(view);
    setReplyTarget(null);
  }

  function replyForm(target: CommentView) {
    if (replyTarget?.id !== target.id) {
      return null;
    }
    return (
      <div className="comment-reply-form">
        <CommentForm
          postId={postId}
          viewer={viewer}
          replyTo={{ id: target.id, nickname: target.author?.nickname ?? '' }}
          onCreated={onCreated}
          onCancel={() => setReplyTarget(null)}
          autoFocus
        />
      </div>
    );
  }

  function renderComment(id: number, children?: ReactNode) {
    const comment = state.byId[id];
    if (!comment) {
      return null;
    }
    return (
      <CommentItem
        comment={comment}
        focused={state.focusId === id}
        onReply={setReplyTarget}
        onEdited={thread.applyEdited}
        onDelete={thread.remove}
      >
        {replyForm(comment)}
        {children}
      </CommentItem>
    );
  }

  return (
    <section
      data-testid="comment-section"
      data-post-id={postId}
      data-around-comment={aroundCommentId ?? undefined}
      aria-labelledby="comments-heading"
      className="comment-section"
    >
      <h2 id="comments-heading" className="comment-heading">
        {COMMENT_TEXT.heading(state.count)}
      </h2>

      {state.first === 'loading' ? (
        <p className="comment-status" aria-busy="true">
          {COMMENT_TEXT.loading}
        </p>
      ) : null}
      {state.first === 'failed' ? <LoadFailed onRetry={thread.reloadFirst} /> : null}

      {ready && state.prevCursor !== null ? (
        <LoadButton
          label={COMMENT_TEXT.previous}
          state={state.prev}
          onClick={() => void thread.loadPrevious()}
        />
      ) : null}

      {ready && state.rootIds.length === 0 ? (
        <p className="comment-status">{COMMENT_TEXT.empty}</p>
      ) : null}

      {state.rootIds.length > 0 ? (
        <ol className="comment-list">
          {state.rootIds.map((id) => (
            <li key={id}>
              {renderComment(
                id,
                <ReplyList
                  rootId={id}
                  replies={state.replies[id]}
                  onMore={() => void thread.loadReplies(id)}
                  renderReply={(replyId) => renderComment(replyId)}
                />,
              )}
            </li>
          ))}
        </ol>
      ) : null}

      {ready && state.nextCursor !== null ? (
        <LoadButton
          label={COMMENT_TEXT.more}
          state={state.more}
          onClick={() => void thread.loadMore()}
        />
      ) : null}

      <div className="comment-new">
        <CommentForm postId={postId} viewer={viewer} onCreated={onCreated} />
      </div>
    </section>
  );
}
