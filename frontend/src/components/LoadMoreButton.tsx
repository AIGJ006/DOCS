import type { CursorListStatus } from '../features/post-list/useCursorList';

/**
 * [더 보기] (005 FR-004·016). 불러오는 동안 "불러오는 중…" + 비활성, 실패하면 "불러오지 못했어요 [다시 시도]",
 * 더 없으면 버튼 대신 "모든 글을 다 봤어요".
 */
export const ALL_SEEN_TEXT = '모든 글을 다 봤어요';
export const LOAD_FAILED_TEXT = '불러오지 못했어요';

export interface LoadMoreButtonProps {
  status: CursorListStatus;
  done: boolean;
  onLoadMore: () => void;
  onRetry: () => void;
}

export default function LoadMoreButton({ status, done, onLoadMore, onRetry }: LoadMoreButtonProps) {
  if (done) {
    return (
      <p data-testid="all-seen" style={{ textAlign: 'center', color: 'var(--muted, #868e96)' }}>
        {ALL_SEEN_TEXT}
      </p>
    );
  }
  if (status === 'error') {
    return (
      <p style={{ textAlign: 'center' }} role="status">
        {LOAD_FAILED_TEXT}{' '}
        <button type="button" onClick={onRetry}>
          다시 시도
        </button>
      </p>
    );
  }
  return (
    <p style={{ textAlign: 'center' }}>
      <button type="button" onClick={onLoadMore} disabled={status === 'loading'}>
        {status === 'loading' ? '불러오는 중…' : '더 보기'}
      </button>
    </p>
  );
}
