import { apiGet, apiPost, apiPut, type ErrorBody } from './client';

/** 가입 때 동의한 문서 버전 (contracts `AgreementConsent`). */
export interface AgreementConsent {
  termsVersion: string;
  privacyVersion: string;
}

export interface EmailSignupRequest {
  email: string;
  handle: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
  agreements: AgreementConsent;
}

export interface SignupResult {
  handle: string;
  nickname: string;
  emailVerified: boolean;
}

export interface LoginResult {
  redirectTo: string;
  reagreementRequired: boolean;
  accountStatus: 'ACTIVE' | 'WITHDRAWN';
}

export interface AgreementDocument {
  version: string;
  effectiveDate: string;
  path: string;
}

export interface CurrentAgreements {
  terms: AgreementDocument;
  privacy: AgreementDocument;
}

export function signup(request: EmailSignupRequest): Promise<SignupResult> {
  return apiPost<SignupResult>('/api/auth/signup', request);
}

/**
 * 이메일 로그인. Spring Security 폼 로그인이라 form-urlencoded로 보낸다(R-04).
 * `redirect`(로그인 뒤 돌아갈 사이트 안 경로)는 서버가 다시 검사해 `redirectTo`로 돌려준다(R-33).
 */
export function login(email: string, password: string, redirect?: string): Promise<LoginResult> {
  const form = new URLSearchParams({ email, password });
  if (redirect && redirect !== '/') {
    form.set('redirect', redirect);
  }
  return apiPost<LoginResult>('/api/auth/login', form);
}

export function logout(): Promise<void> {
  return apiPost<void>('/api/auth/logout');
}

/** 인증 메일 다시 보내기 (로그인 필요, 1분 1번·하루 10번). */
export function resendVerification(): Promise<void> {
  return apiPost<void>('/api/auth/email-verification');
}

/** 메일 링크 토큰 확인. 토큰은 GET으로 소모하지 않고 POST 본문으로 보낸다(R-11). */
export function confirmVerification(token: string): Promise<{ verified: boolean }> {
  return apiPost<{ verified: boolean }>('/api/auth/email-verification/confirm', { token });
}

export function getCurrentAgreements(): Promise<CurrentAgreements> {
  return apiGet<CurrentAgreements>('/api/agreements/current');
}

// ---- 소셜 로그인 (US2) ----

export type SocialProvider = 'GOOGLE' | 'GITHUB';

/** 소셜 가입 마무리 화면의 미리 채운 값 (contracts `SocialSignupDraft`). */
export interface SocialSignupDraft {
  provider: SocialProvider;
  handlePrefix: 'go-' | 'gi-';
  suggestedHandleBody: string;
  suggestedNickname: string | null;
  email: string | null;
  emailRequired: boolean;
  profilePhotoUrl: string | null;
  existingAccountNotice: boolean;
  expiresAt: string;
}

export interface SocialSignupRequest {
  handleBody: string;
  nickname: string;
  email?: string | null;
  useProfilePhoto: boolean;
  agreements: AgreementConsent;
}

export interface SocialSignupResult {
  handle: string;
  nickname: string;
  emailVerified: boolean;
  profilePhotoUrl?: string | null;
  redirectTo: string;
}

/** 앱 키가 설정되어 버튼을 보일 소셜 로그인 수단. */
export function getSocialProviders(): Promise<{ providers: SocialProvider[] }> {
  return apiGet<{ providers: SocialProvider[] }>('/api/auth/social-providers');
}

export function getSocialSignupDraft(): Promise<SocialSignupDraft> {
  return apiGet<SocialSignupDraft>('/api/auth/social-signup', { notFoundScreen: false });
}

export function completeSocialSignup(request: SocialSignupRequest): Promise<SocialSignupResult> {
  return apiPost<SocialSignupResult>('/api/auth/social-signup', request);
}

/** 소셜 콜백이 보관한 오류를 한 번 읽는다. 없으면 null. */
export async function popSocialLoginError(): Promise<ErrorBody | null> {
  const body = await apiGet<ErrorBody | undefined>('/api/auth/social-login-error');
  return body ?? null;
}

/** 소셜 로그인 시작 주소 — 전체 페이지로 이동한다. `redirect`는 서버가 다시 검사한다(R-33). */
export function socialLoginUrl(provider: SocialProvider, redirect: string): string {
  const id = provider === 'GOOGLE' ? 'google' : 'github';
  const query = redirect && redirect !== '/' ? `?redirect=${encodeURIComponent(redirect)}` : '';
  return `/oauth2/authorization/${id}${query}`;
}

// ---- 비밀번호 찾기·재설정·변경 (US4) ----

/** 비밀번호 찾기. 가입 여부와 무관하게 같은 문구가 온다. */
export function requestPasswordReset(email: string): Promise<{ message: string }> {
  return apiPost<{ message: string }>('/api/auth/password-reset', { email });
}

/** 재설정 링크로 새 비밀번호 저장. 성공하면 모든 기기에서 로그아웃된다. */
export function confirmPasswordReset(
  token: string,
  newPassword: string,
  newPasswordConfirm: string,
): Promise<void> {
  return apiPost<void>('/api/auth/password-reset/confirm', {
    token,
    newPassword,
    newPasswordConfirm,
  });
}

/** 로그인 상태 비밀번호 변경 (이메일 가입만). 다른 기기는 로그아웃된다. */
export function changePassword(
  currentPassword: string,
  newPassword: string,
  newPasswordConfirm: string,
): Promise<void> {
  return apiPost<void>('/api/me/password', { currentPassword, newPassword, newPasswordConfirm });
}

/** 바뀐 약관·처리방침 재동의 (`reagree`, FR-012) → 204. 현재 버전이 아니면 400 `AGREEMENT_VERSION_MISMATCH`. */
export function reagree(consent: AgreementConsent): Promise<void> {
  return apiPut('/api/me/agreements', consent);
}
