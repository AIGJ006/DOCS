import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { previewMarkdown } from '../../api/posts';
import { EDITOR_CONFIG } from '../../features/editor/editorConfig';
import { highlightCode } from '../../features/markdown/highlightCode';

/**
 * 미리보기 (002 T063, FR-047, C-POST-1 #5). 입력이 0.5초 멈추면 서버 렌더러(`POST /api/markdown/preview`)를 부르고,
 * 서버가 정화한 HTML만 그대로 보인다(브라우저에서 Markdown을 HTML로 바꾸지 않는다). 이전 요청은 취소한다.
 * 너무 복잡한 글·요청 과다는 패널 안에 안내만 하고 편집은 막지 않는다.
 */
interface Props {
  contentMd: string;
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      return '미리보기 요청이 많아요 — 잠시 후 다시 보여 드려요';
    }
    if (error.code === 'CONTENT_TOO_COMPLEX' || error.code === 'CONTENT_TOO_LONG') {
      return error.message;
    }
    if (error.status === 401) {
      return '로그인하면 미리보기를 볼 수 있어요';
    }
  }
  return '미리보기를 불러오지 못했어요';
}

export default function PreviewPane({ contentMd }: Props) {
  const [html, setHtml] = useState('');
  const [error, setError] = useState<string | null>(null);
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      previewMarkdown(contentMd, controller.signal)
        .then((response) => {
          if (!controller.signal.aborted) {
            setHtml(response.html);
            setError(null);
          }
        })
        .catch((e: unknown) => {
          if (
            !controller.signal.aborted &&
            !(e instanceof DOMException && e.name === 'AbortError')
          ) {
            setError(messageOf(e));
          }
        });
    }, EDITOR_CONFIG.previewDebounceMs);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [contentMd]);

  useEffect(() => {
    highlightCode(container.current);
  }, [html]);

  return (
    <section className="preview-pane" aria-label="미리보기">
      <p className="preview-note">최종 결과는 서버 렌더러 기준이에요</p>
      {error ? (
        <p role="alert" className="preview-error">
          {error}
        </p>
      ) : null}
      {/* 서버 ContentRenderer가 정화한 HTML (FR-040·047). 브라우저에서 만든 HTML은 넣지 않는다. */}
      <div
        ref={container}
        className="post-content"
        data-testid="preview-html"
        dangerouslySetInnerHTML={{ __html: html }}
      />
    </section>
  );
}
