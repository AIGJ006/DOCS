/**
 * 코드 강조 지연 로드 (005 T040, FR-031, research R-14).
 *
 * 코드 블록이 있는 글에서만 002의 `features/markdown/highlightCode.ts`(T062)를 동적 `import()`로 불러
 * 본문 컨테이너에 적용한다 — 강조 규칙을 새로 만들지 않고, 같은 출처 번들이라 CSP `script-src 'self'`를 지킨다.
 * 불러오지 못하면 본문은 강조 없이 그대로 보인다(원칙 V).
 */
export async function loadHighlighter(container: ParentNode | null): Promise<void> {
  if (!container) {
    return;
  }
  try {
    const { highlightCode } = await import('../markdown/highlightCode');
    highlightCode(container);
  } catch {
    // 강조 모듈을 불러오지 못해도 본문은 보인다.
  }
}
