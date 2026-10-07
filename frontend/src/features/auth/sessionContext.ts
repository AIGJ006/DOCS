import { createContext } from 'react';
import type { MeSummary } from '../../api/me';

export interface SessionValue {
  /** 첫 `GET /api/me` 응답을 기다리는 중. */
  loading: boolean;
  /** 로그인 상태 요약. 비로그인이면 null. `reagreementRequired`·`status`로 화면 이동을 정한다. */
  me: MeSummary | null;
  /** `GET /api/me`를 다시 읽는다(가입·인증 직후 등). */
  refresh: () => Promise<MeSummary | null>;
}

export const SessionContext = createContext<SessionValue>({
  loading: false,
  me: null,
  refresh: async () => null,
});
