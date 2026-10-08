import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError, type FieldError } from '../api/client';
import { confirmPasswordReset } from '../api/auth';
import PasswordRuleChecklist from '../components/PasswordRuleChecklist';
import { groupFieldErrors } from '../features/auth/fieldErrors';
import '../features/auth/auth.css';

type Phase = 'form' | 'done' | 'expired';

/**
 * 재설정 링크 화면 (`/reset-password?token=…`, FR-013·044). 토큰은 받자마자 주소창에서 지우고(방문 기록·Referer에 남지 않게,
 * R-11) 새 비밀번호와 함께 POST로 보낸다. 성공하면 모든 기기에서 로그아웃된다.
 */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  // 주소창에서 지운 뒤에도 쓰도록 처음 값을 보관한다
  const [token] = useState<string | null>(() => searchParams.get('token'));
  const [phase, setPhase] = useState<Phase>(() => (token ? 'form' : 'expired'));
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, FieldError[]>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (searchParams.get('token') !== null) {
      navigate({ search: '' }, { replace: true });
    }
  }, [navigate, searchParams]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await confirmPasswordReset(token, password, confirm);
      setPhase('done');
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'LINK_EXPIRED') {
        setPhase('expired');
      } else if (caught instanceof ApiError && caught.errors.length > 0) {
        setFieldErrors(groupFieldErrors(caught.errors));
      } else {
        setError(caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요');
      }
      setSubmitting(false);
    }
  }

  if (phase === 'done') {
    return (
      <main className="auth-page" data-route="reset-password">
        <h1>새 비밀번호를 저장했어요</h1>
        <p role="status">모든 기기에서 로그아웃됐어요. 새 비밀번호로 다시 로그인해 주세요.</p>
        <p>
          <a href="/login">로그인하기</a>
        </p>
      </main>
    );
  }

  if (phase === 'expired') {
    return (
      <main className="auth-page" data-route="reset-password">
        <h1>링크가 만료됐어요</h1>
        <p>재설정 링크는 30분 동안 한 번만 쓸 수 있어요. 비밀번호 찾기를 다시 해 주세요.</p>
        <p>
          <Link to="/forgot-password">비밀번호 찾기</Link>
        </p>
      </main>
    );
  }

  const errorsOf = (field: string) => fieldErrors[field] ?? [];
  return (
    <main className="auth-page" data-route="reset-password">
      <h1>새 비밀번호 정하기</h1>
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <div className="field">
          <label htmlFor="reset-password">새 비밀번호</label>
          <input
            id="reset-password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={errorsOf('newPassword').length > 0 || undefined}
            aria-describedby="reset-password-rules"
          />
          <PasswordRuleChecklist id="reset-password-rules" password={password} />
          {errorsOf('newPassword').map((e) => (
            <p key={e.code} className="field-error">
              {e.message}
            </p>
          ))}
        </div>
        <div className="field">
          <label htmlFor="reset-password-confirm">새 비밀번호 확인</label>
          <input
            id="reset-password-confirm"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            aria-invalid={errorsOf('newPasswordConfirm').length > 0 || undefined}
          />
          {errorsOf('newPasswordConfirm').map((e) => (
            <p key={e.code} className="field-error">
              {e.message}
            </p>
          ))}
        </div>
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          새 비밀번호 저장
        </button>
      </form>
    </main>
  );
}
