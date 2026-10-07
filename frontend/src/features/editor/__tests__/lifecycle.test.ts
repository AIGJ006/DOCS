import { afterEach, describe, expect, it, vi } from 'vitest';
import { registerLifecycle } from '../lifecycle';

function target(unsent: boolean) {
  return { flushNow: vi.fn(), hasUnsent: vi.fn(() => unsent), sendKeepalive: vi.fn() };
}

let dispose: (() => void) | undefined;

afterEach(() => {
  dispose?.();
  dispose = undefined;
  vi.restoreAllMocks();
});

function setVisibility(state: DocumentVisibilityState) {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state);
  document.dispatchEvent(new Event('visibilitychange'));
}

describe('lifecycle', () => {
  it('탭이 가려지면 바로 보낸다', () => {
    const t = target(true);
    dispose = registerLifecycle(t);
    setVisibility('hidden');
    expect(t.flushNow).toHaveBeenCalledTimes(1);
    setVisibility('visible');
    expect(t.flushNow).toHaveBeenCalledTimes(1);
  });

  it('pagehide에는 미전송 내용이 있을 때만 keepalive로 보낸다', () => {
    const t = target(true);
    dispose = registerLifecycle(t);
    window.dispatchEvent(new Event('pagehide'));
    expect(t.sendKeepalive).toHaveBeenCalledTimes(1);

    dispose();
    const clean = target(false);
    dispose = registerLifecycle(clean);
    window.dispatchEvent(new Event('pagehide'));
    expect(clean.sendKeepalive).not.toHaveBeenCalled();
  });

  it('미전송 내용이 있을 때만 beforeunload 확인창을 띄운다', () => {
    const t = target(true);
    dispose = registerLifecycle(t);
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);

    dispose();
    dispose = registerLifecycle(target(false));
    const quiet = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(quiet);
    expect(quiet.defaultPrevented).toBe(false);
  });

  it('해제하면 더는 반응하지 않는다', () => {
    const t = target(true);
    registerLifecycle(t)();
    setVisibility('hidden');
    window.dispatchEvent(new Event('pagehide'));
    expect(t.flushNow).not.toHaveBeenCalled();
    expect(t.sendKeepalive).not.toHaveBeenCalled();
  });
});
