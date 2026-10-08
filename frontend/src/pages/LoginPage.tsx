import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  getSocialProviders,
  login,
  popSocialLoginError,
  socialLoginUrl,
  type SocialProvider,
} from '../api/auth';
import { formatWait } from '../features/auth/fieldErrors';
import { redirectFromSearch } from '../features/auth/safeRedirect';
import { RESTORE_PATH } from '../features/auth-gate/authGate';
import '../features/auth/auth.css';

const INVALID_CREDENTIALS_MESSAGE = '이메일 또는 비밀번호가 올바르지 않아요';
const UNAVAILABLE_MESSAGE = '잠시 후 다시 시도해 주세요';
const SOCIAL_LABEL: Record<SocialProvider, string> = {
  GOOGLE: 'Google로 계속하기',
  GITHUB: 'GitHub로 계속하기',
};

/**
 * 로그인 화면 (07 §5·§6, FR-030·034~039).
 *
 * - 이메일 로그인: 실패 문구는 어느 칸이 틀렸는지 알리지 않는다. 성공하면 전체 새로 고침으로 이동한다 — 로그인하면 세션 ID와
 *   CSRF 토큰이 바뀌므로 앱을 새로 띄워 다시 받는다.
 * - 돌아갈 곳: 다른 화면이 보낸 `?returnTo=`(또는 `?redirect=`)를 `safeRedirect`로 검사해 서버에 `redirect`로 넘긴다.
 *   서버도 같은 검사를 다시 한다(R-33).
 * - 소셜 로그인: 앱 키가 설정된 제공자만 버튼을 보인다. `/oauth2/authorization/{id}?redirect=…`로 전체 페이지 이동.
 *   콜백이 실패하면 `?error=social`로 돌아오고, 보관된 오류(정지 계정 등)를 한 번 읽어 보인다.
 * - 정지(403 `ACCOUNT_SUSPENDED`, 서버 문구에 기한·사유)·잠금(429, `Retry-After`) 문구를 보인다.
 */
export default function LoginPage() {
  const location = useLocation();
  const redirect = redirectFromSearch(location.search);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const socialError = new URLSearchParams(location.search).get('error');
  const [error, setError] = useState<string | null>(
    socialError === 'unavailable' ? UNAVAILABLE_MESSAGE : null,
  );
  const [submitting, setSubmitting] = useState(false);
  const [providers, setProviders] = useState<SocialProvider[]>([]);

  useEffect(() => {
    let active = true;
    getSocialProviders()
      .then((value) => active && setProviders(value.providers))
      .catch(() => active && setProviders([]));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (socialError !== 'social') {
      return;
    }
    let active = true;
    popSocialLoginError()
      .then((body) => {
        if (active && body) {
          setError(body.message);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [socialError]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await login(email, password, redirect);
      const target = result.redirectTo || '/';
      // 015: 탈퇴 유예 계정은 어디서 왔든 복구 화면으로 (재동의보다 먼저)
      if (result.accountStatus === 'WITHDRAWN') {
        window.location.assign(RESTORE_PATH);
        return;
      }
      window.location.assign(
        result.reagreementRequired
          ? target === '/'
            ? '/reagree'
            : `/reagree?returnTo=${encodeURIComponent(target)}`
          : target,
      );
    } catch (caught) {
      setPassword('');
      setError(messageOf(caught));
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
      {providers.length > 0 && (
        <div className="social-login">
          {providers.map((provider) => (
            <a key={provider} href={socialLoginUrl(provider, redirect)}>
              {SOCIAL_LABEL[provider]}
            </a>
          ))}
        </div>
      )}
      <p className="auth-alt">
        <Link to="/forgot-password">비밀번호 찾기</Link> · <Link to="/signup">회원 가입</Link>
      </p>
    </main>
  );
}

function messageOf(caught: unknown): string {
  if (!(caught instanceof ApiError) || caught.status === 401) {
    return INVALID_CREDENTIALS_MESSAGE;
  }
  if (caught.code === 'LOGIN_TEMPORARILY_LOCKED' && caught.retryAfter !== null) {
    return `${caught.message} (${formatWait(caught.retryAfter)} 뒤 다시 시도할 수 있어요)`;
  }
  return caught.message;
}
