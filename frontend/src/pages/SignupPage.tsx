import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError, type FieldError } from '../api/client';
import {
  getCurrentAgreements,
  signup,
  type CurrentAgreements,
  type SignupResult,
} from '../api/auth';
import AvailabilityHint from '../components/AvailabilityHint';
import HandleInput from '../components/HandleInput';
import NicknameInput from '../components/NicknameInput';
import PasswordRuleChecklist from '../components/PasswordRuleChecklist';
import { groupFieldErrors, stripActionLabels } from '../features/auth/fieldErrors';
import { useSession } from '../features/auth/useSession';
import '../features/auth/auth.css';

const AGREEMENT_REQUIRED_MESSAGE = '이용약관과 개인정보 처리방침에 동의해 주세요';

type FieldName = 'email' | 'handle' | 'password' | 'passwordConfirm' | 'nickname' | 'agreements';

/**
 * 이메일 가입 화면 (07 §3, FR-002~005·010·013·014). 서버가 모든 칸 오류를 한 번에 돌려주면 칸마다 보인다.
 * 블로그 주소는 이메일 앞부분으로 미리 채우고(직접 고치면 멈춤), 주소·닉네임은 입력이 멈추면 사용 가능 여부를 묻는다(US3).
 */
export default function SignupPage() {
  const { refresh } = useSession();
  const [agreements, setAgreements] = useState<CurrentAgreements | null>(null);
  const [agreementsFailed, setAgreementsFailed] = useState(false);
  const [email, setEmail] = useState('');
  const [handle, setHandle] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [nickname, setNickname] = useState('');
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreePrivacy, setAgreePrivacy] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, FieldError[]>>({});
  const [handleSuggestion, setHandleSuggestion] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState<SignupResult | null>(null);

  useEffect(() => {
    getCurrentAgreements()
      .then(setAgreements)
      .catch(() => setAgreementsFailed(true));
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    setHandleSuggestion(null);
    if (!agreeTerms || !agreePrivacy) {
      setFieldErrors({
        agreements: [
          { field: 'agreements', code: 'AGREEMENT_REQUIRED', message: AGREEMENT_REQUIRED_MESSAGE },
        ],
      });
      return;
    }
    if (!agreements) {
      setFormError('약관 정보를 불러오지 못했어요. 새로 고친 뒤 다시 시도해 주세요');
      return;
    }
    setSubmitting(true);
    try {
      const result = await signup({
        email,
        handle,
        password,
        passwordConfirm,
        nickname,
        agreements: {
          termsVersion: agreements.terms.version,
          privacyVersion: agreements.privacy.version,
        },
      });
      setFieldErrors({});
      setDone(result);
      await refresh();
    } catch (error) {
      if (error instanceof ApiError && error.errors.length > 0) {
        setFieldErrors(groupFieldErrors(error.errors));
        const suggestion = error.details?.handleSuggestion;
        setHandleSuggestion(typeof suggestion === 'string' ? suggestion : null);
      } else {
        setFieldErrors({});
        setFormError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <main className="auth-page">
        <h1>인증 메일을 보냈어요</h1>
        <p>
          <strong>{email.trim().toLowerCase()}</strong>(으)로 보낸 메일의 링크를 24시간 안에 눌러
          주세요. 인증 전에도 로그인은 되지만 글쓰기·댓글·사진 올리기는 인증한 뒤에 할 수 있어요.
        </p>
        <p>
          블로그 주소: <code>{done.handle}</code> · 닉네임: {done.nickname}
        </p>
        <p>
          <Link to="/">홈으로</Link>
        </p>
      </main>
    );
  }

  const errorsOf = (field: FieldName) => fieldErrors[field] ?? [];
  const describedBy = (field: FieldName, extra?: string) =>
    [errorsOf(field).length > 0 ? `${field}-error` : null, extra].filter(Boolean).join(' ') ||
    undefined;

  function renderErrors(field: FieldName) {
    const errors = errorsOf(field);
    if (errors.length === 0) {
      return null;
    }
    return (
      <div id={`${field}-error`} className="field-error">
        {errors.map((error) => (
          <p key={error.code}>
            {stripActionLabels(error.message)}
            {error.code === 'EMAIL_ALREADY_REGISTERED' && (
              <>
                {' '}
                <Link to="/login">로그인</Link> <Link to="/forgot-password">비밀번호 찾기</Link>
              </>
            )}
            {/* 015: 같은 이메일 계정이 탈퇴 유예 중 — 로그인하면 복구 화면으로 간다 */}
            {error.code === 'EMAIL_WITHDRAWAL_PENDING' && (
              <>
                {' '}
                <Link to="/login">로그인</Link>
              </>
            )}
          </p>
        ))}
        {field === 'handle' && handleSuggestion && (
          <button type="button" className="link-button" onClick={() => setHandle(handleSuggestion)}>
            {handleSuggestion} 쓰기
          </button>
        )}
      </div>
    );
  }

  return (
    <main className="auth-page">
      <h1>회원 가입</h1>
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <div className="field">
          <label htmlFor="signup-email">이메일</label>
          <input
            id="signup-email"
            type="email"
            autoComplete="email"
            maxLength={254}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={errorsOf('email').length > 0}
            aria-describedby={describedBy('email')}
          />
          {renderErrors('email')}
        </div>

        <div className="field">
          <HandleInput
            id="signup-handle"
            label="블로그 주소"
            value={handle}
            onChange={setHandle}
            sourceEmail={email}
            invalid={errorsOf('handle').length > 0}
            describedBy={describedBy('handle')}
          />
          <AvailabilityHint kind="handle" value={handle} onSuggestion={setHandle} />
          {renderErrors('handle')}
        </div>

        <div className="field">
          <label htmlFor="signup-password">비밀번호</label>
          <input
            id="signup-password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={errorsOf('password').length > 0}
            aria-describedby={describedBy('password', 'signup-password-rules')}
          />
          <PasswordRuleChecklist id="signup-password-rules" password={password} />
          {renderErrors('password')}
        </div>

        <div className="field">
          <label htmlFor="signup-password-confirm">비밀번호 확인</label>
          <input
            id="signup-password-confirm"
            type="password"
            autoComplete="new-password"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            aria-invalid={errorsOf('passwordConfirm').length > 0}
            aria-describedby={describedBy('passwordConfirm')}
          />
          {renderErrors('passwordConfirm')}
        </div>

        <div className="field">
          <NicknameInput
            id="signup-nickname"
            value={nickname}
            onChange={setNickname}
            invalid={errorsOf('nickname').length > 0}
            describedBy={describedBy('nickname')}
          />
          <AvailabilityHint kind="nickname" value={nickname} />
          {renderErrors('nickname')}
        </div>

        <fieldset className="field agreements" aria-describedby={describedBy('agreements')}>
          <legend>약관 동의 (필수)</legend>
          <div className="check">
            <input
              id="signup-agree-terms"
              type="checkbox"
              checked={agreeTerms}
              onChange={(e) => setAgreeTerms(e.target.checked)}
            />
            <label htmlFor="signup-agree-terms">이용약관에 동의해요 (필수)</label>{' '}
            <Link to="/terms" target="_blank" rel="noopener">
              이용약관
            </Link>
          </div>
          <div className="check">
            <input
              id="signup-agree-privacy"
              type="checkbox"
              checked={agreePrivacy}
              onChange={(e) => setAgreePrivacy(e.target.checked)}
            />
            <label htmlFor="signup-agree-privacy">개인정보 처리방침에 동의해요 (필수)</label>{' '}
            <Link to="/privacy" target="_blank" rel="noopener">
              처리방침 보기
            </Link>
          </div>
          {agreementsFailed && (
            <p className="field-error">약관 정보를 불러오지 못했어요. 새로 고쳐 주세요</p>
          )}
          {renderErrors('agreements')}
        </fieldset>

        {formError && (
          <p role="alert" className="form-error">
            {formError}
          </p>
        )}

        <button type="submit" className="primary" disabled={submitting}>
          가입하기
        </button>
      </form>
      <p className="auth-alt">
        이미 계정이 있나요? <Link to="/login">로그인하기</Link>
      </p>
    </main>
  );
}
