import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { confirmVerification, resendVerification } from '../api/auth';
import { formatWait } from '../features/auth/fieldErrors';
import { useSession } from '../features/auth/useSession';
import '../features/auth/auth.css';

type Phase = 'verifying' | 'verified' | 'expired' | 'failed';
type ResendState =
  { kind: 'idle' } | { kind: 'sending' } | { kind: 'sent' } | { kind: 'error'; message: string };

/**
 * 메일 인증 링크 화면 (07 §3, FR-005·006). 토큰은 GET으로 소모하지 않고 화면이 POST로 보낸다(R-11).
 */
export default function VerifyEmailPage() {
  const { me, loading, refresh } = useSession();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  // 토큰이 없는 주소로 들어오면 만료된 링크와 같게 안내한다
  const [phase, setPhase] = useState<Phase>(() =>
    searchParams.get('token') ? 'verifying' : 'expired',
  );
  const [failMessage, setFailMessage] = useState('');
  const [resend, setResend] = useState<ResendState>({ kind: 'idle' });
  const started = useRef(false);

  useEffect(() => {
    // StrictMode에서 효과가 두 번 돌아도 토큰은 한 번만 보낸다(두 번째는 LINK_EXPIRED가 된다)
    if (started.current) {
      return;
    }
    started.current = true;
    const token = searchParams.get('token');
    if (token !== null) {
      // 주소창에서 토큰을 바로 지운다(방문 기록·Referer에 남지 않게, R-11). BrowserRouter는 history.replaceState를 쓴다
      navigate({ search: '' }, { replace: true });
    }
    if (!token) {
      return;
    }
    confirmVerification(token)
      .then(() => {
        setPhase('verified');
        void refresh();
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.code === 'LINK_EXPIRED') {
          setPhase('expired');
        } else {
          setFailMessage(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요');
          setPhase('failed');
        }
      });
  }, [navigate, refresh, searchParams]);

  async function onResend() {
    setResend({ kind: 'sending' });
    try {
      await resendVerification();
      setResend({ kind: 'sent' });
    } catch (error) {
      if (error instanceof ApiError && error.status === 429 && error.retryAfter !== null) {
        setResend({
          kind: 'error',
          message: `${formatWait(error.retryAfter)} 뒤에 다시 보낼 수 있어요`,
        });
      } else {
        setResend({
          kind: 'error',
          message: error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요',
        });
      }
    }
  }

  if (phase === 'verifying') {
    return (
      <main className="auth-page">
        <p aria-live="polite">인증하는 중이에요</p>
      </main>
    );
  }

  if (phase === 'verified') {
    return (
      <main className="auth-page">
        <h1>인증이 완료됐어요</h1>
        <p>이제 글쓰기·댓글·사진 올리기를 할 수 있어요.</p>
        <p>
          <Link to="/">홈으로</Link>
        </p>
      </main>
    );
  }

  if (phase === 'failed') {
    return (
      <main className="auth-page">
        <h1>인증하지 못했어요</h1>
        <p role="alert">{failMessage}</p>
      </main>
    );
  }

  const canResend = me !== null && !me.emailVerified;
  return (
    <main className="auth-page">
      <h1>링크가 만료됐어요</h1>
      <p>
        인증 링크는 24시간 동안 한 번만 쓸 수 있어요. 새 메일을 보내면 이전 링크는 쓸 수 없어요.
      </p>
      {canResend && (
        <>
          <button
            type="button"
            className="primary"
            onClick={onResend}
            disabled={resend.kind === 'sending' || resend.kind === 'sent'}
          >
            인증 메일 다시 보내기
          </button>
          {resend.kind === 'sent' && <p aria-live="polite">인증 메일을 다시 보냈어요</p>}
          {resend.kind === 'error' && (
            <p role="alert" className="form-error">
              {resend.message}
            </p>
          )}
        </>
      )}
      {!loading && me === null && (
        <p>
          <Link to="/login">로그인</Link>한 뒤 인증 메일을 다시 보낼 수 있어요
        </p>
      )}
      {me?.emailVerified && (
        <p>
          이미 인증된 계정이에요. <Link to="/">홈으로</Link>
        </p>
      )}
    </main>
  );
}
