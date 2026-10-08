import { useId, useState, type FormEvent, type KeyboardEvent } from 'react';
import { createComment } from '../../api/comments';
import type { CommentView } from '../../api/types/comments';
import type { ViewerFlags } from '../../api/types/viewerFlags';
import CommentInputGate from '../../components/CommentInputGate';
import { COMMENT_MAX, COMMENT_TEXT, commentErrorMessage, countChars } from './commentMessages';

export interface CommentFormProps {
  postId: number;
  viewer: ViewerFlags;
  /** 답글 대상 (없으면 최상위 댓글) */
  replyTo?: { id: number; nickname: string } | null;
  onCreated: (view: CommentView) => void;
  /** 답글 칸의 [취소] */
  onCancel?: () => void;
  autoFocus?: boolean;
}

/**
 * 댓글·답글 입력칸 (007 T034, US2, FR-023·024). 비회원·인증 전 회원에게는 004 `CommentInputGate` 안내가 대신 보인다.
 *
 * - 글자 수는 화면에 보이는 문자 단위(`[...text].length`)로 세어 보여 주고, 1000자를 넘어도 막지 않는다 — 서버가 정리 후 다시
 *   세어 판정한다(정리로 줄어들 수 있음).
 * - 등록 중에는 버튼을 "등록 중…"으로 바꿔 비활성화하고, 실패하면 입력을 지우지 않고 문구를 보인다(503은 code와 상관없이
 *   "잠시 후 다시 시도해 주세요", research R17).
 */
export default function CommentForm({
  postId,
  viewer,
  replyTo = null,
  onCreated,
  onCancel,
  autoFocus = false,
}: CommentFormProps) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const counterId = useId();
  const length = countChars(text);
  const over = length > COMMENT_MAX;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const view = await createComment(postId, {
        content: text,
        replyToCommentId: replyTo ? replyTo.id : null,
      });
      setText('');
      setBusy(false);
      onCreated(view);
    } catch (caught) {
      setBusy(false);
      setError(commentErrorMessage(caught));
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Escape' && onCancel) {
      event.preventDefault();
      onCancel();
    }
  }

  return (
    <CommentInputGate viewer={viewer}>
      <form
        className={replyTo ? 'comment-form comment-form-reply' : 'comment-form'}
        onSubmit={(event) => void onSubmit(event)}
      >
        <textarea
          aria-label={replyTo ? '답글 입력' : '댓글 입력'}
          aria-describedby={counterId}
          placeholder={replyTo ? COMMENT_TEXT.replyPlaceholder : COMMENT_TEXT.placeholder}
          value={text}
          rows={3}
          autoFocus={autoFocus}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
        />
        <div className="comment-form-footer">
          <span
            id={counterId}
            data-testid="comment-counter"
            data-over={over ? 'true' : 'false'}
            className="comment-counter"
          >
            {length}/{COMMENT_MAX}
          </span>
          {onCancel ? (
            <button type="button" onClick={onCancel} disabled={busy}>
              {COMMENT_TEXT.cancel}
            </button>
          ) : null}
          <button type="submit" disabled={busy}>
            {busy ? COMMENT_TEXT.submitting : COMMENT_TEXT.submit}
          </button>
        </div>
        {error ? (
          <p role="alert" className="comment-error">
            {error}
          </p>
        ) : null}
      </form>
    </CommentInputGate>
  );
}
