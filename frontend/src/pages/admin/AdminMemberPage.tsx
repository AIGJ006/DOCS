import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getAdminMember, liftSuspension, suspendMember } from '../../api/admin';
import { ApiError } from '../../api/client';
import type { AdminMemberView, SuspensionDuration } from '../../api/types/moderation';
import { useConfirm } from '../../components/useConfirm';
import { useToast } from '../../components/useToast';
import SuspendForm from '../../features/admin/SuspendForm';
import SuspensionHistory from '../../features/admin/SuspensionHistory';
import { ADMIN_TEXT, adminErrorMessage } from '../../features/admin/adminText';
import '../../features/admin/admin.css';
import { formatDate, formatDateTime } from '../../features/time/dateFormat';
import NotFoundPage from '../NotFoundPage';

type Load =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'error' }
  | { kind: 'ok'; member: AdminMemberView };

const STATUS_LABELS: Record<AdminMemberView['status'], string> = {
  ACTIVE: '정상',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴 신청',
};

/**
 * 관리자 회원 화면 `/admin/members/:handle` (014 T054, US5). 정보·숨겨진 수·정지 이력. 정지 중이면 끝 시각(영구)·사유·[정지
 * 해제], 아니면 기간 4개 + 사유 + [정지](확인 창 "모든 기기에서 로그아웃돼요"). 관리자·탈퇴 신청 회원은 정지 칸이 없다.
 */
export default function AdminMemberPage() {
  const { handle = '' } = useParams();
  const [state, setState] = useState<Load>({ kind: 'loading' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { confirm, dialog } = useConfirm();
  const { show, toast } = useToast();

  useEffect(() => {
    let cancelled = false;
    getAdminMember(handle)
      .then((member) => !cancelled && setState({ kind: 'ok', member }))
      .catch(
        (caught: unknown) =>
          !cancelled &&
          setState(
            caught instanceof ApiError && caught.status === 404
              ? { kind: 'missing' }
              : { kind: 'error' },
          ),
      );
    return () => {
      cancelled = true;
    };
  }, [handle]);

  if (state.kind === 'missing') {
    return <NotFoundPage />;
  }
  if (state.kind === 'loading') {
    return <main className="admin-page" data-route="admin-member" aria-busy="true" />;
  }
  if (state.kind === 'error') {
    return (
      <main className="admin-page" data-route="admin-member">
        <p role="alert" className="admin-error">
          {ADMIN_TEXT.loadFailed}
        </p>
      </main>
    );
  }
  const member = state.member;

  async function suspend(duration: SuspensionDuration, reason: string) {
    if (
      !(await confirm({ message: ADMIN_TEXT.suspendConfirm, confirmLabel: ADMIN_TEXT.suspend }))
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setState({ kind: 'ok', member: await suspendMember(member.handle, duration, reason) });
      show({ text: ADMIN_TEXT.suspended });
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function lift() {
    if (
      !(await confirm({ message: ADMIN_TEXT.liftConfirm, confirmLabel: ADMIN_TEXT.liftSuspension }))
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setState({ kind: 'ok', member: await liftSuspension(member.handle) });
      show({ text: ADMIN_TEXT.lifted });
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  const open = member.openSuspension;
  const canSuspend = member.role !== 'ADMIN' && member.status !== 'WITHDRAWN';

  return (
    <main className="admin-page" data-route="admin-member">
      <h1>
        {member.nickname} <span className="admin-muted">@{member.handle}</span>
      </h1>
      <p className="case-item-meta">
        <span data-testid="member-status">{STATUS_LABELS[member.status]}</span>
        {member.role === 'ADMIN' ? <span>관리자</span> : null}
        <span>가입 {formatDate(member.joinedAt)}</span>
        <span>숨겨진 콘텐츠 {member.hiddenCount}</span>
      </p>
      {open ? (
        <section className="admin-card" aria-label="지금 정지">
          <p>
            정지 끝:{' '}
            <strong>{open.endsAt ? formatDateTime(open.endsAt) : ADMIN_TEXT.permanent}</strong>
          </p>
          <p className="admin-pre">사유: {open.reason}</p>
          <button
            type="button"
            className="admin-button"
            disabled={busy}
            onClick={() => void lift()}
          >
            {ADMIN_TEXT.liftSuspension}
          </button>
        </section>
      ) : canSuspend ? (
        <section className="admin-card" aria-label="정지">
          <SuspendForm
            busy={busy}
            onSubmit={(duration, reason) => void suspend(duration, reason)}
          />
        </section>
      ) : (
        <p className="admin-muted">
          {member.role === 'ADMIN'
            ? ADMIN_TEXT.adminCannotBeSuspended
            : ADMIN_TEXT.withdrawnCannotBeSuspended}
        </p>
      )}
      {error ? (
        <p role="alert" className="admin-error">
          {error}
        </p>
      ) : null}
      <h2 className="case-snapshot-title">{ADMIN_TEXT.history}</h2>
      <SuspensionHistory history={member.history} />
      {dialog}
      {toast}
    </main>
  );
}
