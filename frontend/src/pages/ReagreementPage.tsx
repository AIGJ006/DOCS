import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { getCurrentAgreements, reagree, type CurrentAgreements } from '../api/auth';
import { ApiError } from '../api/client';
import AgreementMeta from '../components/AgreementMeta';
import { logout } from '../features/auth/logout';
import { redirectFromSearch } from '../features/auth/safeRedirect';
import { useCurrentAgreements } from '../features/auth/useCurrentAgreements';
import { useSession } from '../features/auth/useSession';
import '../features/auth/auth.css';

const BOTH_REQUIRED_MESSAGE = '두 문서에 모두 동의해 주세요';
const FAILED_MESSAGE = '잠시 후 다시 시도해 주세요';

/**
 * 약관 재동의 화면 `/reagree` (FR-012, SC-011, R-24). 바뀐 이용약관·개인정보 처리방침의 버전·시행일과 문서 링크를 보이고, 둘 다
 * 동의하면 `PUT /api/me/agreements`(현재 버전) → 세션을 다시 읽고 `?returnTo=`(검사한 값, 없으면 `/`)로 간다. 그 사이 버전이
 * 또 바뀌었으면(400 `AGREEMENT_VERSION_MISMATCH`) 알리고 새 버전을 다시 읽는다. 동의하지 않으려면 로그아웃할 수 있다.
 */
export default function ReagreementPage() {
  const { me, loading, refresh } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const initial = useCurrentAgreements();
  const [reloaded, setReloaded] = useState<CurrentAgreements | null>(null);
  const agreements = reloaded ?? initial.agreements;
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreePrivacy, setAgreePrivacy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!loading && !me) {
    return (
      <main className="auth-page">
        <h1>바뀐 약관에 동의해 주세요</h1>
        <p>
          <Link to="/login?returnTo=%2Freagree">로그인</Link>한 뒤 동의할 수 있어요.
        </p>
      </main>
    );
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!agreeTerms || !agreePrivacy) {
      setError(BOTH_REQUIRED_MESSAGE);
      return;
    }
    if (!agreements) {
      setError(FAILED_MESSAGE);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await reagree({
        termsVersion: agreements.terms.version,
        privacyVersion: agreements.privacy.version,
      });
      await refresh();
      navigate(redirectFromSearch(location.search), { replace: true });
    } catch (caught) {
      setSubmitting(false);
      const mismatch =
        caught instanceof ApiError &&
        caught.errors.some((e) => e.code === 'AGREEMENT_VERSION_MISMATCH');
      if (mismatch) {
        setAgreeTerms(false);
        setAgreePrivacy(false);
        setError((caught as ApiError).errors[0]?.message ?? FAILED_MESSAGE);
        getCurrentAgreements()
          .then(setReloaded)
          .catch(() => undefined);
        return;
      }
      setError(caught instanceof ApiError ? caught.message : FAILED_MESSAGE);
    }
  }

  return (
    <main className="auth-page">
      <h1>바뀐 약관에 동의해 주세요</h1>
      <p>
        이용약관 또는 개인정보 처리방침이 바뀌었어요. 바뀐 내용을 확인하고 동의하면 계속 이용할 수
        있어요.
      </p>
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        <fieldset className="field agreements">
          <legend>약관 동의 (필수)</legend>
          <div className="check">
            <input
              id="reagree-terms"
              type="checkbox"
              checked={agreeTerms}
              onChange={(e) => setAgreeTerms(e.target.checked)}
            />
            <label htmlFor="reagree-terms">바뀐 이용약관에 동의해요 (필수)</label>{' '}
            <Link to="/terms" target="_blank" rel="noopener">
              이용약관 보기
            </Link>
            <AgreementMeta document={agreements?.terms} failed={initial.failed && !agreements} />
          </div>
          <div className="check">
            <input
              id="reagree-privacy"
              type="checkbox"
              checked={agreePrivacy}
              onChange={(e) => setAgreePrivacy(e.target.checked)}
            />
            <label htmlFor="reagree-privacy">바뀐 개인정보 처리방침에 동의해요 (필수)</label>{' '}
            <Link to="/privacy" target="_blank" rel="noopener">
              개인정보 처리방침 보기
            </Link>
            <AgreementMeta document={agreements?.privacy} failed={initial.failed && !agreements} />
          </div>
        </fieldset>
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
        <button type="submit" className="primary" disabled={submitting}>
          동의하고 계속하기
        </button>
      </form>
      {me && (
        <p className="auth-alt">
          동의하지 않으면 이용할 수 없어요.{' '}
          <button type="button" className="link-button" onClick={() => void logout(me.memberId)}>
            로그아웃
          </button>
        </p>
      )}
    </main>
  );
}
