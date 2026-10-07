import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { login } from '../api/auth';
import '../features/auth/auth.css';

const INVALID_CREDENTIALS_MESSAGE = '이메일 또는 비밀번호가 올바르지 않아요';

/**
 * 이메일 로그인 화면 (07 §6, FR-034·036). 실패 문구는 어느 칸이 틀렸는지 알리지 않는다.
 * 성공하면 전체 새로 고침으로 이동한다 — 로그인하면 세션 ID와 CSRF 토큰이 바뀌므로 앱을 새로 띄워 다시 받는다.
 * 정지·잠금 안내와 소셜 로그인 버튼은 US2·US5에서 더한다.
 */
export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await login(email, password);
      window.location.assign(result.reagreementRequired ? '/reagree' : result.redirectTo || '/');
    } catch (caught) {
      setPassword('');
      if (caught instanceof ApiError && caught.status !== 401) {
        setError(caught.message);
      } else {
        setError(INVALID_CREDENTIALS_MESSAGE);
      }
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <h1>로그인</h1>
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <div className="field">
          <label htmlFor="login-email">이메일</label>
          <input
            id="login-email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">비밀번호</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          로그인
        </button>
      </form>
      <p className="auth-alt">
        <Link to="/forgot-password">비밀번호 찾기</Link> · <Link to="/signup">회원 가입</Link>
      </p>
    </main>
  );
}
