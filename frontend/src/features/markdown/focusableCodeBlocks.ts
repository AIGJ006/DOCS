/**
 * 본문 코드 블록(`pre`)을 Tab으로 갈 수 있게 한다 (final-check, axe `scrollable-region-focusable`, WCAG 2.1.1).
 * 긴 코드 줄은 `pre` 안에서만 가로로 스크롤되므로 키보드 사용자도 그 영역에 들어가 화살표로 스크롤할 수 있어야 한다.
 * 서버가 정화한 HTML에는 `tabindex`가 없으므로(허용 목록 밖) 화면이 그린 뒤에 붙인다. 글 상세·에디터 미리보기가 쓴다.
 */
export function focusableCodeBlocks(container: ParentNode | null): void {
  container?.querySelectorAll<HTMLPreElement>('pre').forEach((pre) => {
    if (!pre.hasAttribute('tabindex')) {
      pre.tabIndex = 0;
    }
  });
}
