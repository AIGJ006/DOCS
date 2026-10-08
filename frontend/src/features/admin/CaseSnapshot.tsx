import type { CaseDetail } from '../../api/types/moderation';
import { ADMIN_TEXT } from './adminText';
import './admin.css';

/**
 * 신고 당시 스냅샷 (014 T039·T045). 원문 글자 그대로 텍스트 노드로만 그린다 — HTML로 해석하지 않는다(헌법 IV,
 * `dangerouslySetInnerHTML` 금지). 글은 제목 + 앞부분, 댓글은 제목 없이 내용 전체. 30일 정리 뒤에는 비어 있다.
 */
export default function CaseSnapshot({ detail }: { detail: CaseDetail }) {
  const empty = detail.snapshotTitle === null && detail.snapshotContent === null;
  return (
    <section className="case-snapshot" aria-label={ADMIN_TEXT.snapshotNote}>
      <p className="admin-muted">{ADMIN_TEXT.snapshotNote}</p>
      {detail.targetType === 'POST' && detail.snapshotTitle !== null ? (
        <h2 className="case-snapshot-title">{detail.snapshotTitle}</h2>
      ) : null}
      {empty ? (
        <p className="admin-muted">보관 기간이 지나 내용이 지워졌어요</p>
      ) : (
        <div className="case-snapshot-content" data-testid="snapshot-content">
          {detail.snapshotContent ?? ''}
        </div>
      )}
    </section>
  );
}
