import { useEffect, useState, type FormEvent } from 'react';
import { ApiError, type FieldError } from '../api/client';
import {
  completeSocialSignup,
  getCurrentAgreements,
  getSocialSignupDraft,
  type CurrentAgreements,
  type SocialSignupDraft,
} from '../api/auth';
import { groupFieldErrors, stripActionLabels } from '../features/auth/fieldErrors';
import { safeRedirect } from '../features/auth/safeRedirect';
import { importSocialPhoto } from '../features/profile/socialPhotoImport';
import '../features/auth/auth.css';

const AGREEMENT_REQUIRED_MESSAGE = '이용약관과 개인정보 처리방침에 동의해 주세요';
const NICKNAME_REQUIRED_MESSAGE = '닉네임을 입력해 주세요';
/** 사진 복사 실패 안내를 보여 주는 시간 */
export const PHOTO_NOTICE_MS = 2500;
const PROVIDER_LABEL = { GOOGLE: 'Google', GITHUB: 'GitHub' } as const;

type FieldName = 'handleBody' | 'nickname' | 'email' | 'agreements';

/**
 * 소셜 가입 마무리 화면 (`/signup/social`, FR-027·030~033, R-07·R-21).
 *
 * 소셜 콜백 리다이렉트로 전체 페이지로 열린다 — 이 응답만 CSP `img-src`에 소셜 사진 호스트가 있어 미리보기를 그릴 수 있다.
 * 주소 접두어(`go-`/`gi-`)는 고칠 수 없는 고정 글자이고 본문만 입력한다. 가입을 마치면 (사진 사용 시) 사진을 복사한 뒤
 * `window.location.assign`으로 전체 페이지 이동한다(FR-032 — 다른 화면에는 이 CSP를 남기지 않는다).
 */
export default function SocialSignupPage() {
  const [draft, setDraft] = useState<SocialSignupDraft | null>(null);
  const [expired, setExpired] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [agreements, setAgreements] = useState<CurrentAgreements | null>(null);
  const [handleBody, setHandleBody] = useState('');
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [usePhoto, setUsePhoto] = useState(true);
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreePrivacy, setAgreePrivacy] = useState(false);
  const [noticeOpen, setNoticeOpen] = useState(true);
  const [fieldErrors, setFieldErrors] = useState<Record<string, FieldError[]>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    getSocialSignupDraft()
      .then((value) => {
        setDraft(value);
        setHandleBody(value.suggestedHandleBody);
        setNickname(value.suggestedNickname ?? '');
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.code === 'SOCIAL_SIGNUP_EXPIRED') {
          setExpired(true);
        } else {
          setLoadError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요');
        }
      });
    getCurrentAgreements()
      .then(setAgreements)
      .catch(() => setAgreements(null));
  }, []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!draft) {
      return;
    }
    setFormError(null);
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
      const result = await completeSocialSignup({
        handleBody,
        nickname,
        email: draft.emailRequired ? email : null,
        useProfilePhoto: usePhoto && draft.profilePhotoUrl !== null,
        agreements: {
          termsVersion: agreements.terms.version,
          privacyVersion: agreements.privacy.version,
        },
      });
      setFieldErrors({});
      if (result.profilePhotoUrl) {
        setStatus('프로필 사진을 가져오는 중…');
        const photo = await importSocialPhoto(result.profilePhotoUrl);
        if (!photo.ok) {
          // 가입은 성공이다. 안내를 잠깐 보인 뒤 이동한다(기본 이미지는 이동한 화면이 그린다).
          setStatus(photo.message);
          await new Promise((resolve) => setTimeout(resolve, PHOTO_NOTICE_MS));
        }
      }
      window.location.assign(safeRedirect(result.redirectTo));
    } catch (error) {
      if (error instanceof ApiError && error.code === 'SOCIAL_SIGNUP_EXPIRED') {
        setExpired(true);
      } else if (error instanceof ApiError && error.errors.length > 0) {
        setFieldErrors(groupFieldErrors(error.errors));
      } else {
        setFieldErrors({});
        setFormError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요');
      }
      setSubmitting(false);
    }
  }

  if (expired) {
    return (
      <main className="auth-page" data-route="signup-social">
        <h1>소셜 로그인 정보가 만료됐어요</h1>
        <p>가입 마무리는 10분 안에 해야 해요. 다시 소셜 로그인해 주세요.</p>
        <p>
          <a href="/login">다시 소셜 로그인</a>
        </p>
      </main>
    );
  }

  if (!draft) {
    return (
      <main className="auth-page" data-route="signup-social" aria-busy={!loadError}>
        {loadError ? (
          <p role="alert" className="form-error">
            {loadError}
          </p>
        ) : (
          <p>불러오는 중…</p>
        )}
      </main>
    );
  }

  const errorsOf = (field: FieldName) => fieldErrors[field] ?? [];
  const describedBy = (field: FieldName, extra?: string) =>
    [errorsOf(field).length > 0 ? `social-${field}-error` : null, extra]
      .filter(Boolean)
      .join(' ') || undefined;
  const renderErrors = (field: FieldName) => {
    const errors = errorsOf(field);
    if (errors.length === 0) {
      return null;
    }
    return (
      <div id={`social-${field}-error`} className="field-error">
        {errors.map((error) => (
          <p key={error.code}>{stripActionLabels(error.message)}</p>
        ))}
      </div>
    );
  };

  return (
    <main className="auth-page" data-route="signup-social">
      <h1>{PROVIDER_LABEL[draft.provider]} 계정으로 가입 마무리</h1>
      {draft.existingAccountNotice && noticeOpen && (
        <div className="notice" role="status">
          <p>이 이메일로 가입한 계정이 이미 있어요</p>
          <p>
            <a href="/login">기존 계정으로 로그인</a>{' '}
            <button type="button" className="link-button" onClick={() => setNoticeOpen(false)}>
              새 계정 만들기
            </button>
          </p>
        </div>
      )}
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        {draft.emailRequired ? (
          <div className="field">
            <label htmlFor="social-email">이메일</label>
            <input
              id="social-email"
              type="email"
              autoComplete="email"
              maxLength={254}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={errorsOf('email').length > 0}
              aria-describedby={describedBy('email', 'social-email-help')}
            />
            <p id="social-email-help" className="field-help">
              GitHub에 확인된 대표 이메일이 없어요. 입력한 주소로 인증 메일을 보내요
            </p>
            {renderErrors('email')}
          </div>
        ) : (
          draft.email && (
            <p className="field-help">
              이메일: <strong>{draft.email}</strong>
            </p>
          )
        )}

        <div className="field">
          <label htmlFor="social-handle">블로그 주소</label>
          <div className="handle-input">
            <span className="handle-prefix" aria-hidden="true">
              {draft.handlePrefix}
            </span>
            <input
              id="social-handle"
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={36}
              value={handleBody}
              onChange={(e) => setHandleBody(e.target.value.toLowerCase())}
              aria-invalid={errorsOf('handleBody').length > 0}
              aria-describedby={describedBy('handleBody', 'social-handle-help')}
            />
          </div>
          <p id="social-handle-help" className="field-help">
            앞의 <code>{draft.handlePrefix}</code>는 가입 수단 표시라 바꿀 수 없어요. 영문
            소문자·숫자·_로 3~36자
          </p>
          {renderErrors('handleBody')}
        </div>

        <div className="field">
          <label htmlFor="social-nickname">닉네임</label>
          <input
            id="social-nickname"
            autoComplete="nickname"
            value={nickname}
            placeholder={draft.suggestedNickname === null ? NICKNAME_REQUIRED_MESSAGE : undefined}
            onChange={(e) => setNickname(e.target.value)}
            aria-invalid={errorsOf('nickname').length > 0}
            aria-describedby={describedBy('nickname', 'social-nickname-help')}
          />
          <p id="social-nickname-help" className="field-help">
            {draft.suggestedNickname === null && nickname === ''
              ? NICKNAME_REQUIRED_MESSAGE
              : '한글·영문·숫자로 2~10자'}
          </p>
          {renderErrors('nickname')}
        </div>

        {draft.profilePhotoUrl && (
          <div className="field check">
            <input
              id="social-use-photo"
              type="checkbox"
              checked={usePhoto}
              onChange={(e) => setUsePhoto(e.target.checked)}
            />
            <label htmlFor="social-use-photo">프로필 사진 사용</label>
            {usePhoto && (
              <img
                src={draft.profilePhotoUrl}
                alt="소셜 프로필 사진 미리보기"
                width={64}
                height={64}
                crossOrigin="anonymous"
                referrerPolicy="no-referrer"
              />
            )}
          </div>
        )}

        <fieldset className="field agreements" aria-describedby={describedBy('agreements')}>
          <legend>약관 동의 (필수)</legend>
          <div className="check">
            <input
              id="social-agree-terms"
              type="checkbox"
              checked={agreeTerms}
              onChange={(e) => setAgreeTerms(e.target.checked)}
            />
            <label htmlFor="social-agree-terms">이용약관에 동의해요 (필수)</label>{' '}
            <a href="/terms" target="_blank" rel="noopener">
              이용약관
            </a>
          </div>
          <div className="check">
            <input
              id="social-agree-privacy"
              type="checkbox"
              checked={agreePrivacy}
              onChange={(e) => setAgreePrivacy(e.target.checked)}
            />
            <label htmlFor="social-agree-privacy">개인정보 처리방침에 동의해요 (필수)</label>{' '}
            <a href="/privacy" target="_blank" rel="noopener">
              처리방침 보기
            </a>
          </div>
          {renderErrors('agreements')}
        </fieldset>

        {formError && (
          <p role="alert" className="form-error">
            {formError}
          </p>
        )}
        {status && <p role="status">{status}</p>}

        <button type="submit" className="primary" disabled={submitting}>
          가입 완료
        </button>
      </form>
    </main>
  );
}
