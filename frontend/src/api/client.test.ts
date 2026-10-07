import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiError,
  apiDelete,
  apiGet,
  apiPatch,
  apiPost,
  apiPut,
  ensureCsrf,
  onUnauthorized,
  resetClientForTests,
} from './client';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

function setCookie(value: string) {
  document.cookie = value;
}

function clearCookies() {
  for (const part of document.cookie.split(';')) {
    const name = part.split('=')[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

function headerOf(fetchMock: FetchMock, call: number, name: string): string | null {
  const init = fetchMock.mock.calls[call]?.[1];
  return new Headers(init?.headers).get(name);
}

let fetchMock: FetchMock;

beforeEach(() => {
  clearCookies();
  resetClientForTests();
  fetchMock = vi.fn<typeof fetch>();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  clearCookies();
});

describe('CSRF 헤더', () => {
  it.each([
    ['POST', () => apiPost('/api/x', { a: 1 })],
    ['PUT', () => apiPut('/api/x', { a: 1 })],
    ['PATCH', () => apiPatch('/api/x', { a: 1 })],
    ['DELETE', () => apiDelete('/api/x')],
  ])('%s에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 붙인다', async (method, call) => {
    setCookie('XSRF-TOKEN=token-123; path=/');
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await call();

    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe(method);
    expect(headerOf(fetchMock, 0, 'X-XSRF-TOKEN')).toBe('token-123');
  });

  it('GET에는 붙이지 않는다', async () => {
    setCookie('XSRF-TOKEN=token-123; path=/');
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));

    await apiGet('/api/x');

    expect(headerOf(fetchMock, 0, 'X-XSRF-TOKEN')).toBeNull();
  });

  it('로그인 뒤 바뀐 토큰을 쓰도록 요청마다 쿠키를 다시 읽는다', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(new Response(null, { status: 204 })));
    setCookie('XSRF-TOKEN=before; path=/');
    await apiPost('/api/auth/login');
    setCookie('XSRF-TOKEN=after; path=/');
    await apiPost('/api/me/x');

    expect(headerOf(fetchMock, 0, 'X-XSRF-TOKEN')).toBe('before');
    expect(headerOf(fetchMock, 1, 'X-XSRF-TOKEN')).toBe('after');
  });

  it('쿠키 값이 인코딩돼 있으면 풀어서 보낸다', async () => {
    setCookie('XSRF-TOKEN=a%2Bb%3D; path=/');
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await apiPost('/api/x');

    expect(headerOf(fetchMock, 0, 'X-XSRF-TOKEN')).toBe('a+b=');
  });
});

describe('요청 형식', () => {
  it("credentials는 'same-origin'", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await apiGet('/api/x');
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe('same-origin');
  });

  it('객체 본문은 JSON으로 보내고 JSON 응답을 돌려준다', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 7 }));

    const result = await apiPost<{ id: number }>('/api/x', { title: '제목' });

    expect(result).toEqual({ id: 7 });
    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe(JSON.stringify({ title: '제목' }));
    expect(headerOf(fetchMock, 0, 'Content-Type')).toBe('application/json');
  });

  it('FormData 본문은 그대로 보내고 Content-Type을 정하지 않는다', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const form = new FormData();
    form.append('email', 'a@example.com');

    await apiPost('/api/x', form);

    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe(form);
    expect(headerOf(fetchMock, 0, 'Content-Type')).toBeNull();
  });

  it('204는 undefined', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(apiDelete('/api/x')).resolves.toBeUndefined();
  });

  it('추가 헤더를 넘길 수 있다', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await apiPost('/api/x', {}, { headers: { 'Idempotency-Key': 'k-1' } });
    expect(headerOf(fetchMock, 0, 'Idempotency-Key')).toBe('k-1');
  });
});

describe('오류 응답', () => {
  it('공통 오류 본문을 ApiError로 던진다', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'VALIDATION_FAILED',
        message: '입력값을 확인해 주세요',
        errors: [{ field: 'nickname', code: 'TOO_SHORT', message: '2자 이상 입력해 주세요' }],
        details: null,
      }),
    );

    const error = await apiPost('/api/x', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.message).toBe('입력값을 확인해 주세요');
    expect(apiError.errors).toEqual([
      { field: 'nickname', code: 'TOO_SHORT', message: '2자 이상 입력해 주세요' },
    ]);
    expect(apiError.details).toBeNull();
    expect(apiError.retryAfter).toBeNull();
  });

  it('details와 Retry-After(초)를 담는다', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        429,
        {
          code: 'TOO_MANY_REQUESTS',
          message: '잠시 후 다시 시도해 주세요',
          errors: [],
          details: { a: 1 },
        },
        { 'Retry-After': '30' },
      ),
    );

    const error = (await apiGet('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error.details).toEqual({ a: 1 });
    expect(error.retryAfter).toBe(30);
  });

  it('JSON이 아닌 오류 본문은 code UNKNOWN', async () => {
    fetchMock.mockResolvedValue(
      new Response('<html>Bad Gateway</html>', {
        status: 502,
        headers: { 'Content-Type': 'text/html' },
      }),
    );

    const error = (await apiGet('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.code).toBe('UNKNOWN');
    expect(error.errors).toEqual([]);
    expect(error.details).toBeNull();
  });

  it('JSON이라고 했지만 깨진 본문도 code UNKNOWN', async () => {
    fetchMock.mockResolvedValue(
      new Response('{broken', { status: 500, headers: { 'Content-Type': 'application/json' } }),
    );

    const error = (await apiGet('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('UNKNOWN');
    expect(error.status).toBe(500);
  });

  it('401이면 등록된 onUnauthorized 콜백을 부르고 ApiError를 던진다', async () => {
    const callback = vi.fn();
    onUnauthorized(callback);
    fetchMock.mockResolvedValue(
      jsonResponse(401, {
        code: 'LOGIN_REQUIRED',
        message: '로그인이 필요해요',
        errors: [],
        details: null,
      }),
    );

    const error = (await apiGet('/api/me').catch((e: unknown) => e)) as ApiError;

    expect(error.code).toBe('LOGIN_REQUIRED');
    expect(callback).toHaveBeenCalledTimes(1);
    expect(callback).toHaveBeenCalledWith(error);
  });

  it('401이 아니면 onUnauthorized를 부르지 않고, 해제한 콜백은 부르지 않는다', async () => {
    const callback = vi.fn();
    const unregister = onUnauthorized(callback);
    fetchMock.mockResolvedValueOnce(
      jsonResponse(403, {
        code: 'CSRF_REJECTED',
        message: '다시 시도해 주세요',
        errors: [],
        details: null,
      }),
    );
    await apiPost('/api/x').catch(() => undefined);
    expect(callback).not.toHaveBeenCalled();

    unregister();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, {
        code: 'LOGIN_REQUIRED',
        message: '로그인이 필요해요',
        errors: [],
        details: null,
      }),
    );
    await apiGet('/api/me').catch(() => undefined);
    expect(callback).not.toHaveBeenCalled();
  });
});

describe('ensureCsrf', () => {
  it('GET /api/auth/csrf를 한 번만 부른다', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await Promise.all([ensureCsrf(), ensureCsrf()]);
    await ensureCsrf();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/csrf');
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('GET');
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe('same-origin');
  });

  it('실패하면 다음 호출에서 다시 시도한다', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('network'));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(ensureCsrf()).rejects.toThrow();
    await expect(ensureCsrf()).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
