import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useSession } from './useSession';

/** 재동의 전에도 열 수 있는 화면 (FR-012). */
const REAGREEMENT_ALLOWED_PATHS = ['/reagree', '/terms', '/privacy'];

/**
 * 약관 재동의 가드 (FR-012, SC-011). 로그인 세션이 `reagreementRequired`면 `/reagree`·`/terms`·`/privacy` 밖 화면을
 * `/reagree?returnTo=…`로 보낸다. 서버도 허용 목록 밖 API를 403 `REAGREEMENT_REQUIRED`로 막는다(`ReagreementGateFilter`).
 */
export function ReagreementGate({ children }: { children: ReactNode }) {
  const { me } = useSession();
  const location = useLocation();
  if (me?.reagreementRequired && !REAGREEMENT_ALLOWED_PATHS.includes(location.pathname)) {
    const current = location.pathname + location.search;
    const target =
      current === '/' ? '/reagree' : `/reagree?returnTo=${encodeURIComponent(current)}`;
    return <Navigate to={target} replace />;
  }
  return <>{children}</>;
}
