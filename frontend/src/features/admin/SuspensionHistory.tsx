import type { SuspensionRecord } from '../../api/types/moderation';
import { formatDateTime } from '../time/dateFormat';
import { ADMIN_TEXT } from './adminText';
import './admin.css';

/** 정지 이력 표 (014 T054). 사유는 텍스트로만. 기한 지남 자동 해제는 해제자 "자동". */
export default function SuspensionHistory({ history }: { history: SuspensionRecord[] }) {
  if (history.length === 0) {
    return <p className="admin-muted">{ADMIN_TEXT.noHistory}</p>;
  }
  return (
    <div className="admin-table-wrap">
      <table className="admin-table" aria-label={ADMIN_TEXT.history}>
        <thead>
          <tr>
            <th scope="col">시작</th>
            <th scope="col">끝</th>
            <th scope="col">사유</th>
            <th scope="col">정지한 관리자</th>
            <th scope="col">해제</th>
          </tr>
        </thead>
        <tbody>
          {history.map((record) => (
            <tr key={record.id}>
              <td>{formatDateTime(record.startedAt)}</td>
              <td>{record.endsAt ? formatDateTime(record.endsAt) : ADMIN_TEXT.permanent}</td>
              <td className="admin-pre">{record.reason}</td>
              <td>{record.suspendedByHandle ? `@${record.suspendedByHandle}` : '-'}</td>
              <td>
                {record.liftedAt
                  ? `${formatDateTime(record.liftedAt)} · ${
                      record.liftedByHandle ? `@${record.liftedByHandle}` : ADMIN_TEXT.automatic
                    }`
                  : '정지 중'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
