import { useContext } from 'react';
import { SessionContext, type SessionValue } from './sessionContext';

/** 현재 로그인 상태 (`SessionProvider` 안에서 쓴다). */
export function useSession(): SessionValue {
  return useContext(SessionContext);
}
