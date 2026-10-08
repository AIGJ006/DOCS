/**
 * jsdom에 없는 `window.matchMedia`를 흉내 낸다 (016 T005). `(prefers-color-scheme: dark)`만 다룬다.
 * `setPrefersDark(true)`는 값을 바꾸고 등록된 `change` 리스너를 부른다.
 */
type Listener = (event: MediaQueryListEvent) => void;

let prefersDark = false;
const listeners = new Set<Listener>();

function mediaQueryList(query: string): MediaQueryList {
  const isDarkQuery = query.includes('prefers-color-scheme: dark');
  return {
    get matches() {
      return isDarkQuery ? prefersDark : false;
    },
    media: query,
    onchange: null,
    addEventListener: (_type: string, listener: Listener) => {
      if (isDarkQuery) listeners.add(listener);
    },
    removeEventListener: (_type: string, listener: Listener) => {
      listeners.delete(listener);
    },
    addListener: (listener: Listener) => {
      if (isDarkQuery) listeners.add(listener);
    },
    removeListener: (listener: Listener) => {
      listeners.delete(listener);
    },
    dispatchEvent: () => true,
  } as unknown as MediaQueryList;
}

export function installMatchMedia(initialDark = false): void {
  prefersDark = initialDark;
  listeners.clear();
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query: string) => mediaQueryList(query),
  });
}

export function uninstallMatchMedia(): void {
  listeners.clear();
  Reflect.deleteProperty(window, 'matchMedia');
}

export function setPrefersDark(value: boolean): void {
  prefersDark = value;
  for (const listener of [...listeners]) {
    listener({ matches: value, media: '(prefers-color-scheme: dark)' } as MediaQueryListEvent);
  }
}

/** 지금 구독 중인 `change` 리스너 수 (구독 누수 확인용). */
export function listenerCount(): number {
  return listeners.size;
}
