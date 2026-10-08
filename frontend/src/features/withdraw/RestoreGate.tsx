import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useToast } from '../../components/useToast';
import { RESTORE_PATH } from '../auth-gate/authGate';
import { useSession } from '../auth/useSession';
import { RESTORED_TOAST } from './withdrawMessages';

/** 탈퇴 유예 중에도 열 수 있는 화면 (015 research R12, FR-017). */
const RESTORE_ALLOWED_PATHS = [
  RESTORE_PATH,
  '/terms',
  '/privacy',
  '/forgot-password',
  '/reset-password',
];

/**
 * 탈퇴 유예 가드 (015 T039, FR-017, 42 P-12). 로그인 세션이 `status = WITHDRAWN`이면 허용 경로 밖 화면을
 * `/account/restore`로 `replace` 이동한다. 서버도 허용 목록 밖 API를 403 `ACCOUNT_WITHDRAWN`으로 막는다.
 *
 * 복구 화면이 `state.restored`로 홈에 보내면 "다시 오신 걸 환영해요"를 띄운다 — 이 가드는 모든 경로를 감싸므로 화면이 바뀌어도
 * 알림이 남는다.
 */
export function RestoreGate({ children }: { children: ReactNode }) {
  const { me } = useSession();
  const location = useLocation();
  const { show, toast } = useToast();
  const restored = (location.state as { restored?: unknown } | null)?.restored === true;

  useEffect(() => {
    if (restored) {
      show({ text: RESTORED_TOAST });
    }
  }, [restored, location.key, show]);

  if (me?.status === 'WITHDRAWN' && !RESTORE_ALLOWED_PATHS.includes(location.pathname)) {
    return <Navigate to={RESTORE_PATH} replace />;
  }
  return (
    <>
      {children}
      {toast}
    </>
  );
}
