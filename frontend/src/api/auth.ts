import { apiGet, apiPost } from './client';

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

/** 이메일 로그인. Spring Security 폼 로그인이라 form-urlencoded로 보낸다(R-04). */
export function login(email: string, password: string): Promise<LoginResult> {
  return apiPost<LoginResult>('/api/auth/login', new URLSearchParams({ email, password }));
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
