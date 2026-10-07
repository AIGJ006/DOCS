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
        json(429, errorBody('RATE_LIMITED', '잠시 후 다시 시도해 주세요'), { 'Retry-After': '3' }),
    });
    rerender(<PreviewPane contentMd="ab" />);
    await tick(500);
    await tick(0);
    expect(screen.getByRole('alert')).toHaveTextContent('미리보기 요청이 많아요');
  });
});
