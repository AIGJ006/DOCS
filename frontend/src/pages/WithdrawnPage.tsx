import { Link, useLocation } from 'react-router-dom';
import { formatDeadline } from '../features/withdraw/formatDeadline';
import {
  GO_HOME,
  WITHDRAWN_HIDDEN_LINE,
  WITHDRAWN_TITLE,
  withdrawnDeadlineLine,
} from '../features/withdraw/withdrawMessages';
import '../features/withdraw/withdraw.css';

/**
 * 탈퇴 신청 완료 화면 `/withdrawn` (015 T031, docs/44 §2). 로그아웃된 상태로 보인다. 신청 화면이 넘긴 `state.restoreDeadline`이
 * 없으면(새로 고침·직접 입력) 기한 줄 없이 나머지만 보인다.
 */
export default function WithdrawnPage() {
  const location = useLocation();
  const state = location.state as { restoreDeadline?: unknown } | null;
  const deadline = typeof state?.restoreDeadline === 'string' ? state.restoreDeadline : null;

  return (
    <main className="withdraw-page" data-route="withdrawn">
      <h1>{WITHDRAWN_TITLE}</h1>
      {deadline && <p>{withdrawnDeadlineLine(formatDeadline(deadline))}</p>}
      <p>{WITHDRAWN_HIDDEN_LINE}</p>
      <div className="withdraw-actions">
        <Link to="/" className="primary-action">
          {GO_HOME}
        </Link>
      </div>
    </main>
  );
}
