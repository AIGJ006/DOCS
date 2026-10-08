import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import PreviewPane from '../PreviewPane';

beforeEach(() => {
  resetClientForTests();
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function tick(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe('PreviewPane', () => {
  it('입력이 0.5초 멈추면 서버가 정화한 HTML을 보이고 코드 블록을 강조한다', async () => {
    const fetchMock = stubFetch({
      'POST /api/markdown/preview': () =>
        json(200, {
          html: '<h2 id="h-a">a</h2><pre><code class="language-java">int x = 1;</code></pre>',
        }),
    });
    const { rerender } = render(<PreviewPane contentMd="# a" />);
    rerender(<PreviewPane contentMd="# a\n\n```java" />);
    await tick(499);
    expect(requestsTo(fetchMock, 'POST', '/api/markdown/preview')).toHaveLength(0);
    await tick(1);
    expect(requestsTo(fetchMock, 'POST', '/api/markdown/preview')).toHaveLength(1);
    await tick(0);

    expect(screen.getByRole('heading', { name: 'a' })).toBeInTheDocument();
    expect(document.querySelector('code.hljs')).not.toBeNull();
    expect(screen.getByText(/최종 결과는 서버 렌더러 기준/)).toBeInTheDocument();
  });

  it('새 입력이 오면 이전 요청을 취소한다', async () => {
    const signals: AbortSignal[] = [];
    stubFetch({
      'POST /api/markdown/preview': (init) => {
        signals.push(init?.signal as AbortSignal);
        return new Promise<Response>(() => undefined);
      },
    });
    const { rerender } = render(<PreviewPane contentMd="a" />);
    await tick(500);
    rerender(<PreviewPane contentMd="ab" />);
    await tick(500);
    expect(signals).toHaveLength(2);
    expect(signals[0]?.aborted).toBe(true);
    expect(signals[1]?.aborted).toBe(false);
  });

  it('너무 복잡한 글·요청 과다는 패널 안에 안내만 한다', async () => {
    stubFetch({
      'POST /api/markdown/preview': () =>
        json(
          400,
          errorBody('CONTENT_TOO_COMPLEX', '글 구조가 너무 복잡해요 (목록·인용은 20단계까지)'),
        ),
    });
    const { rerender } = render(<PreviewPane contentMd="a" />);
    await tick(500);
    await tick(0);
    expect(screen.getByRole('alert')).toHaveTextContent('글 구조가 너무 복잡해요');

    stubFetch({
      'POST /api/markdown/preview': () =>
        json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요'), { 'Retry-After': '3' }),
    });
    rerender(<PreviewPane contentMd="ab" />);
    await tick(500);
    await tick(0);
    expect(screen.getByRole('alert')).toHaveTextContent('미리보기 요청이 많아요');
  });

  it('남이 올린 사진·완료 전 사진은 서버가 링크로 바꾼 그대로 보인다 (003 US2, 화면 변경 없음)', async () => {
    const url =
      'http://localhost:9000/blog/images/2026/10/3f1c2a9e-8d7b-4c1e-9a55-0b6f2a1d7e44.webp';
    stubFetch({
      'POST /api/markdown/preview': () =>
        json(200, {
          html: `<p><a href="${url}" rel="nofollow noopener noreferrer" target="_blank">[이미지] 남의 사진</a></p>`,
        }),
    });
    render(<PreviewPane contentMd={`![남의 사진](${url})`} />);
    await tick(500);
    await tick(0);

    expect(screen.getByRole('link', { name: '[이미지] 남의 사진' })).toHaveAttribute('href', url);
    expect(document.querySelector('img')).toBeNull();
  });

  it('업로드 대기(local:) 사진은 기기 사본(blob:)으로 보이고, 서버에는 자리표시 주소로 보낸다 (003 US3)', async () => {
    const fetchMock = stubFetch({
      'POST /api/markdown/preview': (init) => {
        const sent = JSON.parse(String(init?.body)) as { contentMd: string };
        const href = /\((https:\/\/local-image\.invalid\/[a-z0-9-]+)\)/.exec(sent.contentMd)![1];
        return json(200, {
          html: `<p><a href="${href}" rel="nofollow noopener noreferrer">[이미지] ${href}</a></p>`,
        });
      },
    });
    const localImages = new Map([['a1b2', 'blob:http://localhost/a1b2']]);
    render(<PreviewPane contentMd="![](local:a1b2)" localImages={localImages} />);
    await tick(500);
    await tick(0);

    const body = JSON.parse(
      String(requestsTo(fetchMock, 'POST', '/api/markdown/preview')[0][1]?.body),
    );
    expect(body.contentMd).toBe('![](https://local-image.invalid/a1b2)');
    const img = screen.getByTestId('preview-html').querySelector('img');
    expect(img?.getAttribute('src')).toBe('blob:http://localhost/a1b2');
    expect(img?.getAttribute('alt')).toBe('');
    expect(screen.queryByRole('link')).toBeNull();
  });
});
