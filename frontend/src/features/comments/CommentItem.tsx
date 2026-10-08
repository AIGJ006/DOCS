import { useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { editComment } from '../../api/comments';
import type { CommentView } from '../../api/types/comments';
import DefaultAvatar from '../../components/DefaultAvatar';
import RelativeTime from '../../components/RelativeTime';
import { useConfirm } from '../../components/useConfirm';
import {
  COMMENT_MAX,
  COMMENT_TEXT,
  DELETE_CONFIRM,
  commentErrorMessage,
  countChars,
} from './commentMessages';

export interface CommentItemProps {
  comment: CommentView;
  /** around 대상 강조 (2초) */
  focused?: boolean;
  /** [답글] — 정상 댓글에만 보인다 */
  onReply: (comment: CommentView) => void;
  onEdited: (view: CommentView) => void;
  /** 지우기 요청 + 화면 반영. 실패하면 던진다 */
  onDelete: (id: number) => Promise<void>;
  /** 이 댓글 아래 놓을 것(답글 입력칸·답글 목록) */
  children?: ReactNode;
}

/**
 * 댓글 하나 (007 T023·T042, FR-019~022·024).
 *
 * - 상태별 표시: 정상은 프로필·"닉네임 @주소"(블로그 링크)·시각·"· 수정됨"·[작성자] 배지·내용, 삭제된 자리·남이 보는 숨김·탈퇴한
 *   작성자는 정해진 문구만(작성자·내용 없음), 숨긴 내 댓글은 원문 + "숨겨졌어요 (나만 보여요)".
 * - 내용은 글자 그대로(React 텍스트) — `white-space: pre-line`로 줄바꿈만 살리고 링크로 바꾸지 않는다(C-CMT-1 #6).
 * - 버튼: [답글]은 정상 댓글, [수정]·[삭제]는 내 정상 댓글, 숨긴 내 댓글은 [삭제]만. [신고]는 014 전까지 그리지 않는다
 *   (Clarifications Q4 — 014가 켤 때 `comment-actions` 자리에 넣는다).
 */
export default function CommentItem({
  comment,
  focused = false,
  onReply,
  onEdited,
  onDelete,
  children,
}: CommentItemProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { confirm, dialog } = useConfirm();

  const normal = comment.state === 'NORMAL';
  const hiddenMine = comment.state === 'HIDDEN' && comment.mine && comment.content !== null;
  const showAuthor = (normal || hiddenMine) && comment.author !== null;
  const canEdit = normal && comment.mine;
  const canDelete = comment.mine && (normal || comment.state === 'HIDDEN');

  function startEdit() {
    setDraft(comment.content ?? '');
    setError(null);
    setEditing(true);
  }

  function cancelEdit() {
    setEditing(false);
    setError(null);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (saving) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const view = await editComment(comment.id, draft);
      setSaving(false);
      setEditing(false);
      onEdited(view);
    } catch (caught) {
      setSaving(false);
      setError(commentErrorMessage(caught));
    }
  }

  function onEditKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      cancelEdit();
    }
  }

  async function remove() {
    if (!(await confirm(DELETE_CONFIRM))) {
      return;
    }
    setRemoving(true);
    setError(null);
    try {
      await onDelete(comment.id);
    } catch (caught) {
      setRemoving(false);
      setError(commentErrorMessage(caught));
    }
  }

  const className = ['comment-item', comment.parentId === null ? 'comment-root' : 'comment-reply']
    .concat(focused ? ['comment-focus'] : [])
    .join(' ');

  return (
    <article id={`comment-${comment.id}`} data-testid="comment-item" className={className}>
      <div data-testid="comment-main" className="comment-main">
        <div className="comment-avatar">
          {showAuthor && comment.author?.profileImageUrl ? (
            <img
              src={comment.author.profileImageUrl}
              alt=""
              width={32}
              height={32}
              loading="lazy"
              className="comment-avatar-image"
            />
          ) : (
            <DefaultAvatar
              nickname={showAuthor ? comment.author?.nickname : null}
              handle={showAuthor ? comment.author?.handle : null}
              size={32}
            />
          )}
        </div>
        <div className="comment-body">
          <div className="comment-meta">
            {showAuthor && comment.author ? (
              <>
                <Link to={`/@${comment.author.handle}`} className="comment-author">
                  {comment.author.nickname}
                  <span className="comment-handle"> @{comment.author.handle}</span>
                </Link>
                {comment.author.isPostAuthor ? (
                  <span className="comment-badge">{COMMENT_TEXT.postAuthorBadge}</span>
                ) : null}
              </>
            ) : comment.state === 'WITHDRAWN_AUTHOR' ? (
              <span className="comment-author comment-author-gone">
                {COMMENT_TEXT.withdrawnAuthor}
              </span>
            ) : null}
            {normal || hiddenMine ? (
              <span className="comment-time">
                <RelativeTime value={comment.createdAt} />
                {comment.edited ? <span> {COMMENT_TEXT.edited}</span> : null}
              </span>
            ) : null}
          </div>

          {comment.replyTo && (normal || hiddenMine) ? (
            <p className="comment-reply-to">
              {'withdrawn' in comment.replyTo
                ? COMMENT_TEXT.replyToWithdrawn
                : COMMENT_TEXT.replyTo(comment.replyTo.nickname)}
            </p>
          ) : null}

          {editing ? (
            <form className="comment-form comment-edit" onSubmit={(event) => void save(event)}>
              <textarea
                aria-label="댓글 수정"
                value={draft}
                rows={3}
                autoFocus
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={onEditKey}
              />
              <div className="comment-form-footer">
                <span
                  className="comment-counter"
                  data-over={countChars(draft) > COMMENT_MAX ? 'true' : 'false'}
                >
                  {countChars(draft)}/{COMMENT_MAX}
                </span>
                <button type="button" onClick={cancelEdit} disabled={saving}>
                  {COMMENT_TEXT.cancel}
                </button>
                <button type="submit" disabled={saving}>
                  {saving ? COMMENT_TEXT.saving : COMMENT_TEXT.save}
                </button>
              </div>
            </form>
          ) : (
            <CommentContent comment={comment} hiddenMine={hiddenMine} />
          )}

          {!editing && (normal || canDelete) ? (
            <div className="comment-actions" data-testid="comment-actions">
              {normal ? (
                <button
                  type="button"
                  className="comment-action"
                  aria-label={COMMENT_TEXT.replyLabel(comment.author?.nickname ?? '')}
                  onClick={() => onReply(comment)}
                >
                  {COMMENT_TEXT.reply}
                </button>
              ) : null}
              {canEdit ? (
                <button type="button" className="comment-action" onClick={startEdit}>
                  {COMMENT_TEXT.edit}
                </button>
              ) : null}
              {canDelete ? (
                <button
                  type="button"
                  className="comment-action"
                  disabled={removing}
                  onClick={() => void remove()}
                >
                  {removing ? COMMENT_TEXT.removing : COMMENT_TEXT.remove}
                </button>
              ) : null}
            </div>
          ) : null}
          {error ? (
            <p role="alert" className="comment-error">
              {error}
            </p>
          ) : null}
        </div>
      </div>
      {children}
      {dialog}
    </article>
  );
}

function CommentContent({ comment, hiddenMine }: { comment: CommentView; hiddenMine: boolean }) {
  switch (comment.state) {
    case 'DELETED':
      return <p className="comment-placeholder">{COMMENT_TEXT.deleted}</p>;
    case 'WITHDRAWN_AUTHOR':
      return <p className="comment-placeholder">{COMMENT_TEXT.withdrawn}</p>;
    case 'HIDDEN':
      if (!hiddenMine) {
        return <p className="comment-placeholder">{COMMENT_TEXT.hidden}</p>;
      }
      return (
        <>
          <p className="comment-hidden-note">{COMMENT_TEXT.hiddenMine}</p>
          <ContentText text={comment.content ?? ''} />
        </>
      );
    default:
      return <ContentText text={comment.content ?? ''} />;
  }
}

function ContentText({ text }: { text: string }) {
  return (
    <p
      data-testid="comment-content"
      className="comment-content"
      style={{ whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}
    >
      {text}
    </p>
  );
}
