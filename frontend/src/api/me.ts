import { apiGet, apiPatch } from './client';
import type { Visibility } from './posts';

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
  /** 015: 탈퇴 유예 중(WITHDRAWN)일 때 복구 기한(ISO-8601 UTC). 그 밖에는 null */
  restoreDeadline: string | null;
  /** 015: 복구 기한이 지났는가. WITHDRAWN이 아니면 false */
  restoreExpired: boolean;
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
  /** 004 공개 범위 값 (`FRIENDS`는 친구 공개 선택 구현 빌드에서만 나온다) */
  defaultVisibility: Visibility;
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
