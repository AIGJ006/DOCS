import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import NotFoundPage from '../../pages/NotFoundPage';
import { useSession } from '../auth/useSession';
import { loginPathFor } from './authGate';

/**
 * 관리자 화면 라우트 가드 (004 T067, FR-044, US7). `/admin/*`에서 비로그인 → `/login?returnTo=<지금 주소>`, 로그인한 일반 회원
 * → 공통 404 화면(`NotFoundPage`), 관리자 → 하위 화면(014). 서버도 같은 규칙으로 막는다(`AdminPathSecurityCustomizer`) — 이
 * 가드는 화면 보조다.
 */
export default function AdminRouteGate({ children }: { children?: ReactNode }) {
  const { loading, me } = useSession();
  const location = useLocation();

  if (loading) {
    return <main data-route="admin" aria-busy="true" />;
  }
  if (me === null) {
    return <Navigate to={loginPathFor(location.pathname + location.search)} replace />;
  }
  if (me.role !== 'ADMIN') {
    return <NotFoundPage />;
  }
  return <>{children ?? <Outlet />}</>;
}
