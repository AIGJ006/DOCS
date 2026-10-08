import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  getWithdrawalPreview,
  type WithdrawalPreview,
  type WithdrawResult,
} from '../api/withdrawal';
import { clearLocalAccountData } from '../features/auth/logout';
import { useSession } from '../features/auth/useSession';
import WithdrawForm from '../features/withdraw/WithdrawForm';
import { WITHDRAW_LOAD_FAILED, WITHDRAW_TITLE } from '../features/withdraw/withdrawMessages';
import '../features/auth/auth.css';
import '../features/withdraw/withdraw.css';

/**
 * 회원 탈퇴 화면 `/settings/withdraw` (015 T030, docs/44 §2, research R12). 비로그인은
 * `/login?returnTo=%2Fsettings%2Fwithdraw`로. 열 때 `GET /api/me/withdrawal`로 안내 숫자를 읽는다.
 *
 * 성공하면 서버가 세션을 이미 끊었으므로 이 브라우저의 임시 글만 지우고(`clearLocalAccountData`) `/withdrawn`으로 옮긴 뒤 로그인
 * 상태를 새로 읽는다(비로그인이 된다).
 */
export default function WithdrawPage() {
  const { me, loading, refresh } = useSession();
  const navigate = useNavigate();
  const [preview, setPreview] = useState<WithdrawalPreview | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const memberId = me?.memberId;

  useEffect(() => {
    if (memberId === undefined) {
      return;
    }
    let active = true;
    getWithdrawalPreview().then(
      (loaded) => {
        if (active) {
          setPreview(loaded);
        }
      },
      (error: unknown) => {
        if (active) {
          setFailed(error instanceof ApiError ? error.message : WITHDRAW_LOAD_FAILED);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [memberId]);

  if (loading) {
    return null;
  }
  if (!me) {
    return <Navigate to="/login?returnTo=%2Fsettings%2Fwithdraw" replace />;
  }

  async function onWithdrawn(result: WithdrawResult) {
    await clearLocalAccountData(me!.memberId).catch(() => undefined);
    navigate('/withdrawn', { replace: true, state: { restoreDeadline: result.restoreDeadline } });
    void refresh();
  }

  return (
    <main className="withdraw-page" data-route="withdraw">
      <h1>{WITHDRAW_TITLE}</h1>
      {failed && (
        <p role="alert" className="form-error">
          {failed}
        </p>
      )}
      {preview && <WithdrawForm preview={preview} onWithdrawn={onWithdrawn} />}
    </main>
  );
}
