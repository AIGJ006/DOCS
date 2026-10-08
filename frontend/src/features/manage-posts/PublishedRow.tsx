import { Link } from 'react-router-dom';
import type { ManagePostItem } from '../../api/managePosts';
import RowError from './RowError';
import VisibilityMark from './VisibilityMark';
import { formatPublished } from './format';

export interface PublishedRowProps {
  row: ManagePostItem;
  /** 내 블로그 주소의 handle (상세 링크 `/@{handle}/posts/{id}`) */
  handle: string | null;
  busy: boolean;
  error: string | undefined;
  onTrash: (row: ManagePostItem) => void;
  onDiscard: (row: ManagePostItem) => void;
}

/**
 * 발행 글 줄 (006 T049·T069, FR-008·010·016): 공개 범위 표시, [수정 중]·숨김 배지, "발행 … · 수정됨 …", 반응 숫자,
 * [보기]·[수정]/[이어서 수정]·[변경 취소]·[삭제]. 공개 범위를 바꿔도 "수정됨"은 생기지 않는다(서버 `editedAt` 그대로).
 *
 * (구현 메모) [공개 범위 ▾](T068)는 004 `VisibilitySelect`·`PUT /api/posts/{postId}/visibility`가 이 브랜치에 없어
 * 붙이지 않았다. 004가 생기면 `onVisibilityChange` 자리를 더해 `MAKE_PUBLIC_CONFIRM`(비공개 → 공개만)과 함께 붙인다.
 */
export default function PublishedRow({
  row,
  handle,
  busy,
  error,
  onTrash,
  onDiscard,
}: PublishedRowProps) {
  return (
    <li className="manage-row" data-post-id={row.id}>
      <div className="manage-row-main">
        <span className="manage-title-line">
          <VisibilityMark visibility={row.visibility} />
          <span className="manage-title">{row.title}</span>
          {row.editing ? <span className="manage-badge">수정 중</span> : null}
          {row.hidden ? (
            <span className="manage-badge manage-badge-hidden">운영 정책에 따라 숨겨짐</span>
          ) : null}
        </span>
        <span className="manage-meta">{formatPublished(row.publishedAt, row.editedAt)}</span>
        <span className="manage-meta">
          조회 {row.viewCount.toLocaleString('ko-KR')} · 좋아요{' '}
          {row.likeCount.toLocaleString('ko-KR')} · 댓글 {row.commentCount.toLocaleString('ko-KR')}
        </span>
      </div>
      <div className="manage-row-actions">
        {handle ? <Link to={`/@${handle}/posts/${row.id}`}>보기</Link> : null}
        <Link to={`/write/${row.id}`}>{row.editing ? '이어서 수정' : '수정'}</Link>
        {row.editing ? (
          <button type="button" disabled={busy} onClick={() => onDiscard(row)}>
            변경 취소
          </button>
        ) : null}
        <button type="button" disabled={busy} onClick={() => onTrash(row)}>
          삭제
        </button>
      </div>
      <RowError message={error} />
    </li>
  );
}
