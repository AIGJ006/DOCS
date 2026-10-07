/**
 * 코드 블록 강조 (002 T062, FR-045, docs/12 §7-2). 번들에 포함한 highlight.js(우리 서버 제공, CSP `script-src 'self'`)로
 * 컨테이너 안의 `pre > code[class^="language-"]`를 강조한다. 서버가 이미 이스케이프한 코드 글자만 다루고, 언어를 모르거나
 * 예외가 나면 원래 글자를 그대로 둔다. 005 글 상세 화면도 이 함수를 쓴다.
 */
import hljs from 'highlight.js/lib/common';

hljs.configure({ ignoreUnescapedHTML: true, throwUnescapedHTML: false });

const LANGUAGE = /(?:^|\s)language-([\w+#.-]+)/;

export function highlightCode(container: ParentNode | null): void {
  if (!container) {
    return;
  }
  container.querySelectorAll<HTMLElement>('pre > code[class^="language-"]').forEach((code) => {
    if (code.dataset.highlighted === 'yes') {
      return;
    }
    const language = LANGUAGE.exec(code.className)?.[1];
    if (!language || !hljs.getLanguage(language)) {
      return;
    }
    const original = code.textContent ?? '';
    try {
      const result = hljs.highlight(original, { language, ignoreIllegals: true });
      code.innerHTML = result.value;
      code.classList.add('hljs');
      code.dataset.highlighted = 'yes';
    } catch {
      code.textContent = original;
    }
  });
}
