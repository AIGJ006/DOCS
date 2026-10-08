import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { restore } from '../api/withdrawal';
import { logout } from '../features/auth/logout';
import { useSession } from '../features/auth/useSession';
import { formatDeadline, remainingDays } from '../features/withdraw/formatDeadline';
import {
  GENERIC_FAILED,
  LOGOUT,
  RESTORE_EFFECT_LINE,
  RESTORE_EXPIRED_LINE,
  RESTORE_EXPIRED_TITLE,
  RESTORE_SUBMIT,
  RESTORE_SUBMITTING,
  RESTORE_TITLE,
  restoreDeadlineLine,
} from '../features/withdraw/withdrawMessages';
import '../features/withdraw/withdraw.css';

/**
 * 탈퇴 유예 계정 복구 화면 `/account/restore` (015 T038, docs/44 §3, research R12). `GET /api/me`의 `restoreExpired`로 두
 * 상태를 보인다 — 복구 가능: 기한·남은 날 + [로그아웃]·[복구하기] / 기한 지남: 안내 + [로그아웃].
 *
 * [복구하기]가 성공하면 로그인 상태를 새로 읽고 홈으로 간다(`RestoreGate`가 "다시 오신 걸 환영해요"를 띄운다). 409
 * `RESTORE_PERIOD_EXPIRED`면 기한 지남 상태로 바꾼다. 비로그인은 로그인 화면으로, 활동 중이면 홈으로.
 */
export default function RestorePage() {
  const { me, loading, refresh } = useSession();
  const [expired, setExpired] = useState(false);
  const [restored, setRestored] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (loading) {
    return null;
  }
  if (!me) {
    return <Navigate to="/login" replace />;
  }
  if (me.status !== 'WITHDRAWN') {
    return <Navigate to="/" replace state={restored ? { restored: true } : undefined} />;
  }

  const memberId = me.memberId;
  const isExpired = expired || me.restoreExpired || !me.restoreDeadline;

  async function onRestore() {
    setBusy(true);
    setError(null);
    try {
      await restore();
      setRestored(true);
      await refresh();
    } catch (caught) {
      setBusy(false);
      if (caught instanceof ApiError && caught.code === 'RESTORE_PERIOD_EXPIRED') {
        setExpired(true);
        return;
      }
      setError(caught instanceof ApiError ? caught.message : GENERIC_FAILED);
    }
  }

  async function onLogout() {
    setBusy(true);
    try {
      if (!(await logout(memberId))) {
        setBusy(false);
      }
    } catch {
      setBusy(false);
      setError(GENERIC_FAILED);
    }
  }

  return (
    <main className="withdraw-page" data-route="restore">
      {isExpired ? (
        <>
          <h1>{RESTORE_EXPIRED_TITLE}</h1>
          <p>{RESTORE_EXPIRED_LINE}</p>
        </>
      ) : (
        <>
          <h1>{RESTORE_TITLE}</h1>
          <p>
            {restoreDeadlineLine(
              formatDeadline(me.restoreDeadline!),
              remainingDays(me.restoreDeadline!),
            )}
          </p>
          <p>{RESTORE_EFFECT_LINE}</p>
        </>
      )}
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      <div className="withdraw-actions">
        <button type="button" disabled={busy} onClick={() => void onLogout()}>
          {LOGOUT}
        </button>
        {!isExpired && (
          <button
            type="button"
            className="primary-action"
            disabled={busy}
            onClick={() => void onRestore()}
          >
            {busy ? RESTORE_SUBMITTING : RESTORE_SUBMIT}
          </button>
        )}
      </div>
    </main>
  );
}
