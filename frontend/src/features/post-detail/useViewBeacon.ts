import { useEffect, useRef } from 'react';
import { recordPostView } from '../../api/posts';

/**
 * 조회 기록 비콘 (005 T040, FR-041, research R-16·R-28).
 *
 * - 글이 **보이는 상태로** 1초가 지나면 `POST /api/posts/{id}/views`를 **한 번만** 보낸다.
 * - 1초 전에 숨으면(탭 전환·미리 불러오기·백그라운드) 보내지 않고, 다시 보이면 남은 시간부터 센다.
 * - 실패는 무시하고 재시도하지 않는다 — 조회 기록이 상세 화면을 막지 않는다(원칙 V).
 * - `enabled=false`(작성자 본인)면 아무 요청도 하지 않는다.
 *
 * 인라인 스크립트를 쓰지 않는다 — 번들의 이 훅이 보낸다(FR-037, CSP `script-src 'self'`).
 */
export const VIEW_BEACON_DELAY_MS = 1000;

export interface ViewBeaconOptions {
  postId: number | string;
  /** 작성자 본인이면 false (FR-041, SC-007) */
  enabled: boolean;
}

export function useViewBeacon({ postId, enabled }: ViewBeaconOptions): void {
  const sent = useRef(false);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    let remaining = VIEW_BEACON_DELAY_MS;
    let startedAt = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const send = () => {
      timer = undefined;
      if (sent.current) {
        return;
      }
      sent.current = true;
      recordPostView(postId);
    };

    const start = () => {
      if (timer !== undefined || sent.current || remaining <= 0) {
        return;
      }
      startedAt = Date.now();
      timer = setTimeout(send, remaining);
    };

    const pause = () => {
      if (timer === undefined) {
        return;
      }
      clearTimeout(timer);
      timer = undefined;
      remaining -= Date.now() - startedAt;
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        start();
      } else {
        pause();
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    if (document.visibilityState === 'visible') {
      start();
    }
    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      if (timer !== undefined) {
        clearTimeout(timer);
      }
    };
  }, [postId, enabled]);
}
