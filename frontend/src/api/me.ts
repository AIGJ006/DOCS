import { apiGet } from './client';

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
