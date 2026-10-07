import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, onUnauthorized } from '../../api/client';
import { getMe, type MeSummary } from '../../api/me';
import { SessionContext, type SessionValue } from './sessionContext';

/**
 * 로그인 상태를 `GET /api/me`로 읽어 하위 화면에 준다. 401이면 비로그인이다.
 * 다른 요청이 401을 받으면(세션 만료·다른 기기에서 끊김) 비로그인으로 바꾼다.
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
    return onUnauthorized(() => setMe(null));
  }, [load]);

  const value = useMemo<SessionValue>(() => ({ loading, me, refresh: load }), [loading, me, load]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}
