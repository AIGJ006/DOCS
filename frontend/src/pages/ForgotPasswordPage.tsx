import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { requestPasswordReset } from '../api/auth';
import { formatWait } from '../features/auth/fieldErrors';
import '../features/auth/auth.css';

export const RESET_ACCEPTED_MESSAGE = '가입된 이메일이면 안내 메일을 보냈어요';

/**
 * 비밀번호 찾기 (`/forgot-password`, FR-042·043). 결과 문구는 가입 여부와 무관하게 하나다. 같은 이메일 1분 1번·하루 10번,
 * 같은 IP 1시간 20번을 넘으면 기다릴 시간을 보인다.
 */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await requestPasswordReset(email);
      setSent(result.message || RESET_ACCEPTED_MESSAGE);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 429) {
        setError(
          caught.retryAfter !== null
            ? `${caught.message} (${formatWait(caught.retryAfter)} 뒤 다시 보낼 수 있어요)`
            : caught.message,
        );
      } else if (caught instanceof ApiError && caught.errors.length > 0) {
        setError(caught.errors[0]?.message ?? caught.message);
      } else {
        setError(caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page" data-route="forgot-password">
      <h1>비밀번호 찾기</h1>
      {sent ? (
        <p role="status">{sent}</p>
      ) : (
        <form className="auth-form" onSubmit={onSubmit} noValidate>
          <p className="field-help">
            가입한 이메일을 입력하면 비밀번호를 다시 정할 수 있는 링크를 보내요.
          </p>
          <div className="field">
            <label htmlFor="forgot-email">이메일</label>
            <input
              id="forgot-email"
              type="email"
              autoComplete="email"
              maxLength={254}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          {error && (
            <p role="alert" className="form-error">
              {error}
            </p>
          )}
          <button type="submit" className="primary" disabled={submitting}>
            안내 메일 받기
          </button>
        </form>
      )}
      <p className="auth-alt">
        <Link to="/login">로그인으로</Link>
      </p>
    </main>
  );
}
