import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { ApiError, apiGet, onNotFound, resetClientForTests } from '../api/client';
import NotFoundPage, { NOT_FOUND_MESSAGE } from './NotFoundPage';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

const NOT_FOUND_BODY = {
  code: 'NOT_FOUND',
  message: '볼 수 없는 페이지예요',
  errors: [],
  details: null,
};

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

let fetchMock: FetchMock;

beforeEach(() => {
  resetClientForTests();
  fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('공통 404 화면 (FR-014)', () => {
  it('고정 문구와 홈 링크만 보이고 이유를 구분하지 않는다', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    );

    expect(NOT_FOUND_MESSAGE).toBe('볼 수 없는 페이지예요');
    expect(screen.getByRole('heading', { name: '볼 수 없는 페이지예요' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '홈으로' })).toHaveAttribute('href', '/');
    expect(document.body.textContent).not.toMatch(/비공개|삭제|권한|없는 글/);
  });

  it('없는 화면 경로도 같은 화면이다', () => {
    render(
      <MemoryRouter initialEntries={['/no/such/page']}>
        <App />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: '볼 수 없는 페이지예요' })).toBeInTheDocument();
  });

  it('API 클라이언트가 404 NOT_FOUND를 받으면 공통 404 화면으로 바뀐다', async () => {
    fetchMock.mockResolvedValue(jsonResponse(404, NOT_FOUND_BODY));
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    expect(screen.queryByRole('heading', { name: '볼 수 없는 페이지예요' })).toBeNull();

    await act(async () => {
      await expect(apiGet('/api/posts/1')).rejects.toBeInstanceOf(ApiError);
    });

    expect(screen.getByRole('heading', { name: '볼 수 없는 페이지예요' })).toBeInTheDocument();
  });

  it('onNotFound 콜백은 404 NOT_FOUND에만 불리고 해제할 수 있다', async () => {
    const handler = vi.fn();
    const off = onNotFound(handler);

    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'INVALID_VISIBILITY', message: 'x', errors: [], details: null }),
    );
    await expect(apiGet('/api/x')).rejects.toBeInstanceOf(ApiError);
    expect(handler).not.toHaveBeenCalled();

    fetchMock.mockResolvedValueOnce(jsonResponse(404, NOT_FOUND_BODY));
    await expect(apiGet('/api/x')).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' });
    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler.mock.calls[0]?.[0]).toBeInstanceOf(ApiError);

    off();
    fetchMock.mockResolvedValueOnce(jsonResponse(404, NOT_FOUND_BODY));
    await expect(apiGet('/api/x')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledTimes(1);
  });
});
