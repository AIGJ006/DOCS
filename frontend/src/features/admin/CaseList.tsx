import { Link } from 'react-router-dom';
import type { CaseListItem, ReportReason } from '../../api/types/moderation';
import RelativeTime from '../../components/RelativeTime';
import { REASON_LABELS } from '../moderation/reasonLabels';
import { ADMIN_TEXT, CASE_STATUS_LABELS, TARGET_TYPE_LABELS } from './adminText';
import './admin.css';

export interface CaseListProps {
  items: CaseListItem[];
  /** 처리됨 탭이면 결과·처리자·[숨김 해제]를 보인다 */
  handled: boolean;
  /** [숨김 해제] (처리됨 탭, 지금 숨김인 항목만) */
  onUnhide?: (item: CaseListItem) => void;
  /** 해제 요청 중인 사건 번호 */
  busyCaseId?: number | null;
}

/** 사건 목록 (014 T039·T045·T049). 제목·작성자는 텍스트로만. 댓글 제목은 서버가 준 내용 앞 40자. */
export default function CaseList({ items, handled, onUnhide, busyCaseId = null }: CaseListProps) {
  if (items.length === 0) {
    return <p className="admin-muted">{ADMIN_TEXT.empty}</p>;
  }
  return (
    <ul className="case-list" data-testid="case-list">
      {items.map((item) => (
        <li key={item.caseId} className="case-item" data-testid="case-item">
          <div className="case-item-head">
            <span className="admin-badge" data-type={item.targetType}>
              {TARGET_TYPE_LABELS[item.targetType]}
            </span>
            <Link to={`/admin/reports/${item.caseId}`} className="case-item-title">
              {item.title ?? '(내용 없음)'}
            </Link>
          </div>
          <div className="case-item-meta">
            <span>{item.authorHandle ? `@${item.authorHandle}` : '(알 수 없음)'}</span>
            <span>{ADMIN_TEXT.reportCount(item.reportCount)}</span>
            <span className="case-reasons">
              {(Object.keys(item.reasonCounts) as ReportReason[]).map((code) => (
                <span key={code}>
                  {REASON_LABELS[code]} {item.reasonCounts[code]}
                </span>
              ))}
            </span>
            {item.lastReportedAt && !handled ? <RelativeTime value={item.lastReportedAt} /> : null}
            {handled ? (
              <>
                <span data-testid="case-status">{CASE_STATUS_LABELS[item.status]}</span>
                <span>{item.handledByNickname ?? ADMIN_TEXT.automatic}</span>
                {item.handledAt ? <RelativeTime value={item.handledAt} /> : null}
              </>
            ) : null}
          </div>
          {handled && item.targetHiddenNow && onUnhide ? (
            <button
              type="button"
              className="admin-button"
              disabled={busyCaseId === item.caseId}
              onClick={() => onUnhide(item)}
            >
              {ADMIN_TEXT.unhide}
            </button>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
