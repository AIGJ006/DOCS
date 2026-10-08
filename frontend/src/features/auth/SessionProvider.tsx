import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, onReagreementRequired, onUnauthorized } from '../../api/client';
import { getMe, type MeSummary } from '../../api/me';
import { SessionContext, type SessionValue } from './sessionContext';

/**
 * 로그인 상태를 `GET /api/me`로 읽어 하위 화면에 준다. 401이면 비로그인이다.
 * 다른 요청이 401을 받으면(세션 만료·다른 기기에서 끊김) 비로그인으로 바꾼다.
 * 403 `REAGREEMENT_REQUIRED`를 받으면 `reagreementRequired`를 켠다 — `ReagreementGate`가 재동의 화면으로 보낸다.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [me, setMe] = useState<MeSummary | null>(null);

  const load = useCallback(
    () =>
      getMe().then(
        (summary) => {
          setMe(summary);
          setLoading(false);
          return summary;
        },
        (error: unknown) => {
          if (!(error instanceof ApiError)) {
            console.warn('로그인 상태를 읽지 못했어요', error);
          }
          setMe(null);
          setLoading(false);
          return null;
        },
      ),
    [],
  );

  useEffect(() => {
    void load();
    const offUnauthorized = onUnauthorized(() => setMe(null));
    const offReagreement = onReagreementRequired(() =>
      setMe((current) => (current ? { ...current, reagreementRequired: true } : current)),
    );
    return () => {
      offUnauthorized();
      offReagreement();
    };
  }, [load]);

  const value = useMemo<SessionValue>(() => ({ loading, me, refresh: load }), [loading, me, load]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
