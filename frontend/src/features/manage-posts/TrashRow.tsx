import type { ManagePostItem } from '../../api/managePosts';
import RowError from './RowError';
import { formatDeletedOn, originalStatusLabel, purgeText } from './format';

export interface TrashRowProps {
  row: ManagePostItem;
  busy: boolean;
  error: string | undefined;
  now: Date;
  onRestore: (row: ManagePostItem) => void;
  onPurge: (row: ManagePostItem) => void;
}

/**
 * 휴지통 줄 (006 T050·T065, FR-011·027·029): 제목 + 원래 상태, "삭제 10월 2일 · 27일 뒤 완전 삭제", [복구]·[영구 삭제].
 */
export default function TrashRow({ row, busy, error, now, onRestore, onPurge }: TrashRowProps) {
  return (
    <li className="manage-row" data-post-id={row.id}>
      <div className="manage-row-main">
        <span className="manage-title-line">
          <span className={row.title ? 'manage-title' : 'manage-title manage-title-empty'}>
            {row.title || '(제목 없음)'}
          </span>
          <span className="manage-original">{originalStatusLabel(row.status)}</span>
        </span>
        {row.deletedAt && row.purgeAt ? (
          <span className="manage-meta">
            <time dateTime={row.deletedAt}>{formatDeletedOn(row.deletedAt)}</time> ·{' '}
            {purgeText(row.purgeAt, now)}
          </span>
        ) : null}
      </div>
      <div className="manage-row-actions">
        <button type="button" disabled={busy} onClick={() => onRestore(row)}>
          복구
        </button>
        <button type="button" disabled={busy} onClick={() => onPurge(row)}>
          영구 삭제
        </button>
      </div>
      <RowError message={error} />
    </li>
  );
}
