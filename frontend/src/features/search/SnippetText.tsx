import type { Snippet } from '../../api/types/discovery';
import './search.css';

/**
 * 검색어 주변 문장 (012 T018, research R9, FR-033·FR-034). `text`를 `marks` 범위(UTF-16 색인, `[시작, 끝)`)로 잘라
 * 텍스트 노드와 `<mark>`로만 그린다 — HTML로 해석하지 않으므로 글자 `<script>`도 글자로 보인다(이스케이프는 React가 한다).
 * 범위가 겹치거나 순서가 틀리거나 글자 밖이면 그 범위는 건너뛴다.
 */
export interface SnippetTextProps {
  snippet: Snippet;
}

function snippetParts(snippet: Snippet): { text: string; marked: boolean }[] {
  const { text, marks } = snippet;
  const parts: { text: string; marked: boolean }[] = [];
  let at = 0;
  for (const [start, end] of marks) {
    if (start < at || end <= start || end > text.length) {
      continue;
    }
    if (start > at) {
      parts.push({ text: text.slice(at, start), marked: false });
    }
    parts.push({ text: text.slice(start, end), marked: true });
    at = end;
  }
  if (at < text.length) {
    parts.push({ text: text.slice(at), marked: false });
  }
  return parts;
}

export default function SnippetText({ snippet }: SnippetTextProps) {
  return (
    <span data-testid="snippet-text">
      {snippetParts(snippet).map((part, i) =>
        part.marked ? (
          <mark key={i} className="search-mark">
            {part.text}
          </mark>
        ) : (
          <span key={i}>{part.text}</span>
        ),
      )}
    </span>
  );
}
