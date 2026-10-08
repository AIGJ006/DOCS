import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getCase, getCases, hideTarget, unhideTarget } from '../../api/admin';
import type { CaseListItem, CasePage, CaseTab, ReportReason } from '../../api/types/moderation';
import { useConfirm } from '../../components/useConfirm';
import { useToast } from '../../components/useToast';
import CaseList from '../../features/admin/CaseList';
import { ADMIN_TEXT, adminErrorMessage, parsePostRef } from '../../features/admin/adminText';
import '../../features/admin/admin.css';
import { REASON_LABELS } from '../../features/moderation/reasonLabels';

/**
 * 신고 관리 `/admin/reports?tab=pending|handled` (014 T039·T049, US2·US4). 004 `AdminRouteGate` 아래에 있다.
 *
 * - 대기 탭은 신고 수 많은 순, 처리됨 탭은 처리 시각 최근 순. [더 보기]는 `nextCursor`, 이미 있는 사건 번호는 건너뛴다(넘기는 사이 순서가
 *   바뀔 수 있음).
 * - 처리됨 탭에서 지금 숨김인 항목은 [숨김 해제] — 목록 응답에 대상 번호가 없어 사건 상세로 대상을 확인한 뒤 해제한다.
 * - 아래 "글 주소로 숨기기"는 신고 없이 바로 숨긴다(직접 숨김).
 */
export default function AdminReportsPage() {
  const [params, setParams] = useSearchParams();
  const tab: CaseTab = params.get('tab') === 'handled' ? 'HANDLED' : 'PENDING';
  return (
    <main className="admin-page" data-route="admin-reports">
      <h1>{ADMIN_TEXT.reportsTitle}</h1>
      <nav className="admin-tabs" aria-label="신고 탭">
        <Link
          to="/admin/reports?tab=pending"
          aria-current={tab === 'PENDING' ? 'page' : undefined}
          onClick={(event) => {
            event.preventDefault();
            setParams({ tab: 'pending' });
          }}
        >
          {ADMIN_TEXT.tabPending}
        </Link>
        <Link
          to="/admin/reports?tab=handled"
          aria-current={tab === 'HANDLED' ? 'page' : undefined}
          onClick={(event) => {
            event.preventDefault();
            setParams({ tab: 'handled' });
          }}
        >
          {ADMIN_TEXT.tabHandled}
        </Link>
      </nav>
      <CaseTabList key={tab} tab={tab} />
      <DirectHide />
    </main>
  );
}

type Status = 'loading' | 'idle' | 'error';

function CaseTabList({ tab }: { tab: CaseTab }) {
  const [items, setItems] = useState<CaseListItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [status, setStatus] = useState<Status>('loading');
  const [busy, setBusy] = useState<number | null>(null);
  const inFlight = useRef(false);
  const { confirm, dialog } = useConfirm();
  const { show, toast } = useToast();

  const receive = useCallback((page: CasePage, append: boolean) => {
    setItems((previous) => {
      if (!append) {
        return page.items;
      }
      const seen = new Set(previous.map((item) => item.caseId));
      return [...previous, ...page.items.filter((item) => !seen.has(item.caseId))];
    });
    setNextCursor(page.nextCursor);
    setStatus('idle');
  }, []);

  const load = useCallback(
    async (cursor: string | null) => {
      if (inFlight.current) {
        return;
      }
      inFlight.current = true;
      setStatus('loading');
      try {
        receive(await getCases(tab, cursor), cursor !== null);
      } catch {
        setStatus('error');
      } finally {
        inFlight.current = false;
      }
    },
    [receive, tab],
  );

  useEffect(() => {
    let cancelled = false;
    inFlight.current = true;
    getCases(tab, null)
      .then((page) => !cancelled && receive(page, false))
      .catch(() => !cancelled && setStatus('error'))
      .finally(() => {
        inFlight.current = false;
      });
    return () => {
      cancelled = true;
    };
  }, [receive, tab]);

  async function unhide(item: CaseListItem) {
    if (!(await confirm({ message: ADMIN_TEXT.unhideConfirm, confirmLabel: ADMIN_TEXT.unhide }))) {
      return;
    }
    setBusy(item.caseId);
    try {
      const detail = await getCase(item.caseId);
      const targetId = detail.targetType === 'POST' ? detail.postId : detail.commentId;
      if (targetId === null) {
        show({ text: ADMIN_TEXT.noTarget });
        return;
      }
      await unhideTarget(detail.targetType, targetId);
      setItems((previous) =>
        previous.map((it) => (it.caseId === item.caseId ? { ...it, targetHiddenNow: false } : it)),
      );
      show({ text: ADMIN_TEXT.unhidden });
    } catch (caught) {
      show({ text: adminErrorMessage(caught) });
    } finally {
      setBusy(null);
    }
  }

  return (
    <section aria-busy={status === 'loading'}>
      {status === 'error' && items.length === 0 ? (
        <p role="alert" className="admin-error">
          {ADMIN_TEXT.loadFailed}{' '}
          <button type="button" className="admin-button" onClick={() => void load(null)}>
            {ADMIN_TEXT.reload}
          </button>
        </p>
      ) : null}
      {status !== 'loading' || items.length > 0 ? (
        <CaseList
          items={items}
          handled={tab === 'HANDLED'}
          onUnhide={(item) => void unhide(item)}
          busyCaseId={busy}
        />
      ) : (
        <p className="admin-muted">{ADMIN_TEXT.loading}</p>
      )}
      {nextCursor ? (
        <button
          type="button"
          className="admin-button"
          disabled={status === 'loading'}
          onClick={() => void load(nextCursor)}
        >
          {status === 'loading' ? ADMIN_TEXT.loading : ADMIN_TEXT.more}
        </button>
      ) : null}
      {status === 'error' && items.length > 0 ? (
        <p role="alert" className="admin-error">
          {ADMIN_TEXT.loadFailed}
        </p>
      ) : null}
      {dialog}
      {toast}
    </section>
  );
}

const REASONS = Object.keys(REASON_LABELS) as ReportReason[];

/** 글 주소로 바로 숨기기 (직접 숨김, research R7). */
function DirectHide() {
  const [ref, setRef] = useState('');
  const [reason, setReason] = useState<ReportReason | ''>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { confirm, dialog } = useConfirm();
  const { show, toast } = useToast();
  const postId = parsePostRef(ref);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (postId === null) {
      setError(ADMIN_TEXT.directInvalid);
      return;
    }
    if (reason === '') {
      return;
    }
    if (!(await confirm({ message: ADMIN_TEXT.hideConfirm, confirmLabel: ADMIN_TEXT.hide }))) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await hideTarget('POST', postId, reason);
      setRef('');
      show({ text: ADMIN_TEXT.hidden });
    } catch (caught) {
      setError(adminErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="admin-direct admin-card" onSubmit={(event) => void submit(event)}>
      <h2 className="case-snapshot-title">{ADMIN_TEXT.directTitle}</h2>
      <label htmlFor="admin-direct-ref">{ADMIN_TEXT.directLabel}</label>
      <input
        id="admin-direct-ref"
        className="admin-input"
        value={ref}
        onChange={(event) => setRef(event.target.value)}
        placeholder="/@handle/posts/123"
      />
      <label htmlFor="admin-direct-reason">{ADMIN_TEXT.hideReasonLegend}</label>
      <select
        id="admin-direct-reason"
        className="admin-input"
        value={reason}
        onChange={(event) => setReason(event.target.value as ReportReason | '')}
      >
        <option value="">사유를 골라 주세요</option>
        {REASONS.map((code) => (
          <option key={code} value={code}>
            {REASON_LABELS[code]}
          </option>
        ))}
      </select>
      {error ? (
        <p role="alert" className="admin-error">
          {error}
        </p>
      ) : null}
      <div className="admin-actions">
        <button
          type="submit"
          className="admin-button admin-button-danger"
          disabled={busy || ref.trim() === '' || reason === ''}
        >
          {ADMIN_TEXT.hide}
        </button>
      </div>
      {dialog}
      {toast}
    </form>
  );
}
