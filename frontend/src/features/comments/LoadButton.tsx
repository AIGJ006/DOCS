import type { LoadState } from './useCommentThread';
import { COMMENT_TEXT } from './commentMessages';

/**
 * [댓글 더 보기]·[답글 N개 더 보기]·[이전 댓글 보기] 공용 (FR-024). 불러오는 중에는 "불러오는 중…" 비활성, 실패하면 그 자리에
 * "불러오지 못했어요 [다시 시도]" — 다시 시도는 같은 커서로 부른다(커서는 성공했을 때만 바뀐다).
 */
export default function LoadButton({
  label,
  state,
  onClick,
}: {
  label: string;
  state: LoadState;
  onClick: () => void;
}) {
  if (state === 'failed') {
    return <LoadFailed onRetry={onClick} />;
  }
  return (
    <button type="button" className="comment-load" disabled={state === 'loading'} onClick={onClick}>
      {state === 'loading' ? COMMENT_TEXT.loading : label}
    </button>
  );
}

export function LoadFailed({ onRetry }: { onRetry: () => void }) {
  return (
    <p className="comment-load-failed" role="status">
      <span>{COMMENT_TEXT.loadFailed}</span>{' '}
      <button type="button" onClick={onRetry}>
        {COMMENT_TEXT.retry}
      </button>
    </p>
  );
}
