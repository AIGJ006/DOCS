import { Link } from 'react-router-dom';
import type { ManagePostItem } from '../../api/managePosts';
import RowError from './RowError';
import { formatSavedAt } from './format';

export interface DraftRowProps {
  row: ManagePostItem;
  busy: boolean;
  error: string | undefined;
  now: Date;
  onTrash: (row: ManagePostItem) => void;
}

/**
 * 임시글 줄 (006 T048, FR-007): 제목(비면 회색 "(제목 없음)"), "마지막 저장 …", [이어 쓰기]·[삭제]. 공개 범위는 보이지
 * 않는다. 제목은 텍스트로만 렌더링한다.
 */
export default function DraftRow({ row, busy, error, now, onTrash }: DraftRowProps) {
  return (
    <li className="manage-row" data-post-id={row.id}>
      <div className="manage-row-main">
        <span className={row.title ? 'manage-title' : 'manage-title manage-title-empty'}>
          {row.title || '(제목 없음)'}
        </span>
        <span className="manage-meta">
          마지막 저장 <time dateTime={row.updatedAt}>{formatSavedAt(row.updatedAt, now)}</time>
        </span>
      </div>
      <div className="manage-row-actions">
        <Link to={`/write/${row.id}`} aria-disabled={busy || undefined}>
          이어 쓰기
        </Link>
        <button type="button" disabled={busy} onClick={() => onTrash(row)}>
          삭제
        </button>
      </div>
      <RowError message={error} />
    </li>
  );
}
