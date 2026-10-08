/**
 * 본문 GIF 재생 자리 (005 T041, FR-036·037).
 *
 * (구현 메모) 003 이미지 업로드 기능의 GIF 재생 모듈(003 FR-039)이 소유한다 — 그때 이 함수를 그 모듈 호출로 바꾼다.
 * 003 전까지는 아무 것도 하지 않는다(본문의 GIF는 브라우저 기본 동작대로 보인다). 인라인 스크립트를 쓰지 않는다.
 */
export function playableGifs(container: ParentNode | null): void {
  void container;
  // 003에서 교체: 첫 장면 + ▶ 오버레이를 붙이고 누르면 재생한다.
}
