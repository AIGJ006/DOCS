import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { previewMarkdown } from '../../api/posts';
import { EDITOR_CONFIG } from '../../features/editor/editorConfig';
import { highlightCode } from '../../features/markdown/highlightCode';
import { focusableCodeBlocks } from '../../features/markdown/focusableCodeBlocks';

/**
 * 미리보기 (002 T063, FR-047, C-POST-1 #5). 입력이 0.5초 멈추면 서버 렌더러(`POST /api/markdown/preview`)를 부르고,
 * 서버가 정화한 HTML만 그대로 보인다(브라우저에서 Markdown을 HTML로 바꾸지 않는다). 이전 요청은 취소한다.
 * 너무 복잡한 글·요청 과다는 패널 안에 안내만 하고 편집은 막지 않는다.
 */
interface Props {
  contentMd: string;
  /** 업로드 대기 사진 `localId` → `blob:` 주소 (003 US3). 본문의 `local:` 사진을 기기 사본으로 보인다 */
  localImages?: ReadonlyMap<string, string>;
}

/** 미리보기 요청에서만 `local:` 사진을 이 자리표시 주소로 바꿔 보낸다 — 서버는 외부 사진처럼 링크로 돌려준다. */
const LOCAL_PREVIEW_PREFIX = 'https://local-image.invalid/';

function toPreviewSource(contentMd: string): string {
  return contentMd.replace(/\(local:([A-Za-z0-9-]+)\)/g, `(${LOCAL_PREVIEW_PREFIX}$1)`);
}

/** 서버가 돌려준 자리표시 링크를 기기 사본 이미지로 바꾼다 (주소는 이 기기에서 만든 `blob:`만). */
function showLocalImages(root: HTMLElement | null, localImages?: ReadonlyMap<string, string>) {
  if (!root) return;
  for (const link of Array.from(
    root.querySelectorAll<HTMLAnchorElement>(`a[href^="${LOCAL_PREVIEW_PREFIX}"]`),
  )) {
    const localId = link.getAttribute('href')!.slice(LOCAL_PREVIEW_PREFIX.length);
    const url = localImages?.get(localId);
    const img = document.createElement('img');
    img.alt = '';
    img.className = 'local-image';
    img.dataset.localId = localId;
    if (url && url.startsWith('blob:')) {
      img.src = url;
    }
    link.replaceWith(img);
  }
  for (const img of Array.from(root.querySelectorAll<HTMLImageElement>('img[data-local-id]'))) {
    const url = localImages?.get(img.dataset.localId ?? '');
    if (url && url.startsWith('blob:') && img.src !== url) {
      img.src = url;
    }
  }
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

export default function PreviewPane({ contentMd, localImages }: Props) {
  const [html, setHtml] = useState('');
  const [error, setError] = useState<string | null>(null);
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      previewMarkdown(toPreviewSource(contentMd), controller.signal)
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
    focusableCodeBlocks(container.current);
  }, [html]);

  useEffect(() => {
    showLocalImages(container.current, localImages);
  }, [html, localImages]);

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
