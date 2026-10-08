import { useCallback, useEffect, useRef, useState } from 'react';
import { getUnreadCount } from '../../api/notifications';

/** 안 읽은 수를 다시 확인하는 간격 (research R16). */
export const NOTIFICATION_POLL_MS = 30_000;

/**
 * 알림 화면 등이 읽음·삭제를 했을 때 보내는 창 이벤트. 종이 받으면 바로 다시 확인한다.
 */
export const NOTIFICATIONS_CHANGED_EVENT = 'blog:notifications-changed';

export function announceNotificationsChanged(): void {
  window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
}

export interface UnreadCountState {
  count: number;
  /** 지금 바로 다시 확인한다 */
  refresh: () => void;
  /** 읽음 처리 뒤 로컬 값을 바로 줄인다(0 아래로는 내려가지 않음). 다음 확인에서 서버 값으로 맞춘다 */
  decrease: (by?: number) => void;
  /** 모두 읽음 뒤 0으로 */
  clear: () => void;
}

/**
 * 안 읽은 알림 수 (011 T032, research R16).
 *
 * - `enabled`(로그인)일 때만 부른다. 마운트 즉시 1번, 30초마다.
 * - 탭이 `hidden`이면 타이머를 멈추고, `visible`이 되면 즉시 1번 + 타이머를 다시 건다.
 * - 실패는 조용히 무시하고 마지막 값을 둔다.
 */
export function useUnreadCount(enabled: boolean): UnreadCountState {
  const [count, setCount] = useState(0);
  const active = useRef(false);

  const refresh = useCallback(() => {
    if (!active.current) {
      return;
    }
    getUnreadCount()
      .then((result) => {
        if (active.current) {
          setCount(result.count);
        }
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    active.current = true;
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (timer === null) {
        timer = setInterval(refresh, NOTIFICATION_POLL_MS);
      }
    };
    const stop = () => {
      if (timer !== null) {
        clearInterval(timer);
        timer = null;
      }
    };
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        stop();
      } else {
        refresh();
        start();
      }
    };
    refresh();
    if (document.visibilityState !== 'hidden') {
      start();
    }
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, refresh);
    return () => {
      active.current = false;
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, refresh);
    };
  }, [enabled, refresh]);

  const decrease = useCallback((by = 1) => setCount((value) => Math.max(0, value - by)), []);
  const clear = useCallback(() => setCount(0), []);

  return { count: enabled ? count : 0, refresh, decrease, clear };
}
