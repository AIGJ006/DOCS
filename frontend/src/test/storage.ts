/**
 * `localStorage` 접근이 예외를 던지게 한다 (016 T005 — 사생활 보호 모드·저장소 차단 흉내).
 */
let original: PropertyDescriptor | undefined;

export function blockStorage(): void {
  original = Object.getOwnPropertyDescriptor(window, 'localStorage');
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    get() {
      throw new DOMException('저장소 접근이 막혔어요', 'SecurityError');
    },
  });
}

export function restoreStorage(): void {
  if (original) {
    Object.defineProperty(window, 'localStorage', original);
    original = undefined;
  }
}
