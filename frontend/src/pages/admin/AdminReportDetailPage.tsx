import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getCase, resolveCase, unhideTarget } from '../../api/admin';
import { ApiError } from '../../api/client';
import type { CaseDetail, ReportReason, ResolutionAction } from '../../api/types/moderation';
import RelativeTime from '../../components/RelativeTime';
import { useConfirm } from '../../components/useConfirm';
import { useToast } from '../../components/useToast';
import CaseSnapshot from '../../features/admin/CaseSnapshot';
import ResolutionForm from '../../features/admin/ResolutionForm';
import {
  ADMIN_TEXT,
  CASE_STATUS_LABELS,
  TARGET_TYPE_LABELS,
  adminErrorMessage,
} from '../../features/admin/adminText';
import '../../features/admin/admin.css';
import { REASON_LABELS } from '../../features/moderation/reasonLabels';
import { formatDate } from '../../features/time/dateFormat';
import NotFoundPage from '../NotFoundPage';

type Load =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'error' }
  | { kind: 'ok'; detail: CaseDetail };

/**
 * 신고 사건 상세 `/admin/reports/:caseId` (014 T039·T045·T049·T054, US2). 신고 당시 스냅샷(텍스트로만), "현재: …", 사유별
 * 수·기타 설명, 작성자 카드([회원 화면]), 처리 칸. 409면 "이미 처리된 신고예요" + [다시 불러오기].
 */
export default function AdminReportDetailPage() {
  const { caseId = '' } = useParams();
  const [state, setState] = useState<Load>({ kind: 'loading' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  const { confirm, dialog } = useConfirm();
  const { show, toast } = useToast();

  const load = useCallback(async () => {
    setStale(false);
    setError(null);
    try {
      setState({ kind: 'ok', detail: await getCase(caseId) });
    } catch (caught) {
      setState(
        caught instanceof ApiError && caught.status === 404
          ? { kind: 'missing' }
          : { kind: 'error' },
      );
    }
  }, [caseId]);

  const validId = /^\d+$/.test(caseId);

  useEffect(() => {
    if (!validId) {
      return;
    }
    let cancelled = false;
    getCase(caseId)
      .then((detail) => !cancelled && setState({ kind: 'ok', detail }))
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
  }, [caseId, validId]);

  if (!validId || state.kind === 'missing') {
    return <NotFoundPage />;
  }
  if (state.kind === 'loading') {
    return <main className="admin-page" data-route="admin-report" aria-busy="true" />;
  }
  if (state.kind === 'error') {
    return (
      <main className="admin-page" data-route="admin-report">
        <p role="alert" className="admin-error">
          {ADMIN_TEXT.loadFailed}
        </p>
        <button type="button" className="admin-button" onClick={() => void load()}>
          {ADMIN_TEXT.reload}
        </button>
      </main>
    );
  }
  const detail = state.detail;

  async function resolve(action: ResolutionAction, reason: ReportReason | null) {
    const message = action === 'HIDE' ? ADMIN_TEXT.hideConfirm : ADMIN_TEXT.rejectConfirm;
    const label = action === 'HIDE' ? ADMIN_TEXT.hide : ADMIN_TEXT.reject;
    if (!(await confirm({ message, confirmLabel: label }))) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const next = await resolveCase(detail.caseId, action, reason);
      setState({ kind: 'ok', detail: next });
      show({ text: action === 'HIDE' ? ADMIN_TEXT.hidden : ADMIN_TEXT.rejected });
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'REPORT_ALREADY_HANDLED') {
        setStale(true);
      }
      setError(adminErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function unhide() {
    const targetId = detail.targetType === 'POST' ? detail.postId : detail.commentId;
    if (targetId === null) {
      return;
    }
    if (!(await confirm({ message: ADMIN_TEXT.unhideConfirm, confirmLabel: ADMIN_TEXT.unhide }))) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await unhideTarget(detail.targetType, targetId);
      show({ text: ADMIN_TEXT.unhidden });
      await load();
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  const reasons = Object.keys(detail.reasonCounts) as ReportReason[];

  return (
    <main className="admin-page" data-route="admin-report">
      <p>
        <Link to="/admin/reports">← {ADMIN_TEXT.reportsTitle}</Link>
      </p>
      <h1>
        <span className="admin-badge">{TARGET_TYPE_LABELS[detail.targetType]}</span> 신고 #
        {detail.caseId}
      </h1>
      <p className="case-item-meta">
        <span data-testid="case-status">{CASE_STATUS_LABELS[detail.status]}</span>
        <span data-testid="current-state">{ADMIN_TEXT.current(detail.currentState)}</span>
        <span>{ADMIN_TEXT.reportCount(detail.reportCount)}</span>
        <RelativeTime value={detail.createdAt} />
      </p>
      {detail.status === 'CLOSED_NO_TARGET' ? (
        <p className="admin-muted">{ADMIN_TEXT.noTarget}</p>
      ) : null}
      <CaseSnapshot detail={detail} />
      {reasons.length > 0 ? (
        <ul className="case-reasons" aria-label="사유별 신고 수">
          {reasons.map((code) => (
            <li key={code}>
              {REASON_LABELS[code]} {detail.reasonCounts[code]}
            </li>
          ))}
        </ul>
      ) : null}
      {detail.otherDetails.length > 0 ? (
        <section className="admin-card" aria-label="기타 설명">
          {detail.otherDetails.map((other, index) => (
            <p key={index} className="admin-pre">
              {other.detail ?? '(보관 기간이 지나 지워졌어요)'}
            </p>
          ))}
        </section>
      ) : null}
      <section className="admin-card" aria-label="작성자">
        <p>
          {detail.author.nickname ?? '(알 수 없음)'}{' '}
          {detail.author.handle ? `@${detail.author.handle}` : null}
        </p>
        <p className="case-item-meta">
          <span>가입 {formatDate(detail.author.joinedAt)}</span>
          <span>숨겨진 콘텐츠 {detail.author.hiddenCount}</span>
          <span>정지 이력 {detail.author.suspensionCount}</span>
          {detail.author.suspendedNow ? <span>정지 중</span> : null}
        </p>
        {detail.author.handle ? (
          <Link to={`/admin/members/${encodeURIComponent(detail.author.handle)}`}>
            {ADMIN_TEXT.memberScreen}
          </Link>
        ) : null}
      </section>
      {detail.status === 'PENDING' ? (
        <ResolutionForm
          disabled={detail.onlyMyReport}
          busy={busy || stale}
          onSubmit={(action, reason) => void resolve(action, reason)}
        />
      ) : null}
      {detail.status === 'HIDDEN' && detail.currentState === 'HIDDEN' ? (
        <div className="admin-actions">
          <button
            type="button"
            className="admin-button"
            disabled={busy}
            onClick={() => void unhide()}
          >
            {ADMIN_TEXT.unhide}
          </button>
        </div>
      ) : null}
      {error ? (
        <p role="alert" className="admin-error">
          {error}{' '}
          {stale ? (
            <button type="button" className="admin-button" onClick={() => void load()}>
              {ADMIN_TEXT.reload}
            </button>
          ) : null}
        </p>
      ) : null}
      {dialog}
      {toast}
    </main>
  );
}
