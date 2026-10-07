/**
 * 페이지 생명 주기 처리 (002 T085, FR-012, US3 #4).
 *
 * - 탭이 가려지면(`visibilitychange` → hidden) 기다리지 않고 바로 보낸다.
 * - 페이지를 떠날 때(`pagehide`) 미전송 내용이 있으면 `fetch(…, { keepalive: true })`로 한 번 보낸다.
 * - 미전송 내용이 있을 때만 `beforeunload` 확인창을 띄운다.
 */
export interface LifecycleTarget {
  flushNow: () => void;
  hasUnsent: () => boolean;
  sendKeepalive: () => void;
}

export function registerLifecycle(
  target: LifecycleTarget,
  win: Window = window,
  doc: Document = document,
): () => void {
  const onVisibility = () => {
    if (doc.visibilityState === 'hidden') {
      target.flushNow();
    }
  };
  const onPageHide = () => {
    if (target.hasUnsent()) {
      target.sendKeepalive();
    }
  };
  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (target.hasUnsent()) {
      event.preventDefault();
      // 옛 브라우저는 returnValue가 있어야 확인창을 띄운다.
      event.returnValue = '';
    }
  };
  doc.addEventListener('visibilitychange', onVisibility);
  win.addEventListener('pagehide', onPageHide);
  win.addEventListener('beforeunload', onBeforeUnload);
  return () => {
    doc.removeEventListener('visibilitychange', onVisibility);
    win.removeEventListener('pagehide', onPageHide);
    win.removeEventListener('beforeunload', onBeforeUnload);
  };
}
