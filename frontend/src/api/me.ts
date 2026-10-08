import { apiGet, apiPatch } from './client';

/** 현재 로그인 상태 요약 (contracts `MeSummary`). */
export interface MeSummary {
  memberId: number;
  handle: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  status: 'ACTIVE' | 'WITHDRAWN';
  provider: 'LOCAL' | 'GOOGLE' | 'GITHUB';
  emailVerified: boolean;
  reagreementRequired: boolean;
  profileImageUrl: string | null;
}

export function getMe(options?: { signal?: AbortSignal }): Promise<MeSummary> {
  return apiGet<MeSummary>('/api/me', options);
}

/** 내 프로필 (contracts `MyProfile`). */
export interface MyProfile {
  handle: string;
  nickname: string;
  bio: string | null;
  profileImageId: number | null;
  profileImageUrl: string | null;
  /** null이면 지금 바꿀 수 있음 */
  nicknameChangeAvailableAt: string | null;
}

/** 바꾸려는 칸만 보낸다. `profileImageId: null`은 기본 이미지로 (contracts `ProfileUpdateRequest`). */
export interface ProfileUpdate {
  nickname?: string;
  bio?: string | null;
  profileImageId?: number | null;
}

export type Provider = MeSummary['provider'];

/** 내 계정 설정 (contracts `MySettings`). */
export interface MySettings {
  email: string | null;
  provider: Provider;
  previousLogin: { at: string; provider: Provider } | null;
  defaultVisibility: 'PUBLIC' | 'PRIVATE';
  lastActiveVisible: boolean;
  passwordChangeAvailable: boolean;
}

export interface SettingsUpdate {
  defaultVisibility?: MySettings['defaultVisibility'];
  lastActiveVisible?: boolean;
}

export function getMyProfile(): Promise<MyProfile> {
  return apiGet<MyProfile>('/api/me/profile');
}

export function updateMyProfile(update: ProfileUpdate): Promise<MyProfile> {
  return apiPatch<MyProfile>('/api/me/profile', update);
}

export function getMySettings(): Promise<MySettings> {
  return apiGet<MySettings>('/api/me/settings');
}

export function updateMySettings(update: SettingsUpdate): Promise<MySettings> {
  return apiPatch<MySettings>('/api/me/settings', update);
}
