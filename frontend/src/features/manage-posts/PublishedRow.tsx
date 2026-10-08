import { Link } from 'react-router-dom';
import type { ManagePostItem } from '../../api/managePosts';
import type { SetVisibilityResult, Visibility } from '../../api/posts';
import VisibilityBadge from '../visibility/VisibilityBadge';
import VisibilitySelect from '../visibility/VisibilitySelect';
import RowError from './RowError';
import { formatPublished } from './format';

export interface PublishedRowProps {
  row: ManagePostItem;
  /** 내 블로그 주소의 handle (상세 링크 `/@{handle}/posts/{id}`) */
  handle: string | null;
  busy: boolean;
  error: string | undefined;
  onTrash: (row: ManagePostItem) => void;
  onDiscard: (row: ManagePostItem) => void;
  /**
   * [공개 범위 ▾] (T068): 비공개 → 공개면 확인한다. `false`면 바꾸지 않는다. 주지 않으면 확인 없이 바꾼다.
   */
  confirmVisibility?: (from: Visibility, to: Visibility) => boolean | Promise<boolean>;
  /** [공개 범위 ▾] 저장 — 줄 단위 처리(`useRowAction`)로 부르고 실패면 `null`. 주지 않으면 선택 상자를 그리지 않는다 */
  onVisibilityChange?: (
    row: ManagePostItem,
    next: Visibility,
  ) => Promise<SetVisibilityResult | null>;
}

/**
 * 발행 글 줄 (006 T049·T069, FR-008·010·016): 공개 범위 표시, [수정 중]·숨김 배지, "발행 … · 수정됨 …", 반응 숫자,
 * [보기]·[수정]/[이어서 수정]·[변경 취소]·[삭제]. 공개 범위를 바꿔도 "수정됨"은 생기지 않는다(서버 `editedAt` 그대로).
 *
 * [공개 범위 ▾](T068)는 004 `VisibilitySelect`를 즉시 저장 모드로 쓰되 저장은 부르는 쪽(`onVisibilityChange` — 004
 * `PUT /api/posts/{postId}/visibility`를 줄 단위 처리로)이 맡는다. 공개 범위 표시는 004 `VisibilityBadge`(그림 글자 + 스크린
 * 리더 글자 "공개"/"비공개").
 */
export default function PublishedRow({
  row,
  handle,
  busy,
  error,
  onTrash,
  onDiscard,
  confirmVisibility,
  onVisibilityChange,
}: PublishedRowProps) {
  return (
    <li className="manage-row" data-post-id={row.id}>
      <div className="manage-row-main">
        <span className="manage-title-line">
          <VisibilityBadge visibility={row.visibility} compact />
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
        {onVisibilityChange ? (
          <VisibilitySelect
            value={row.visibility}
            disabled={busy}
            confirm={confirmVisibility}
            save={(next) => onVisibilityChange(row, next)}
          />
        ) : null}
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
