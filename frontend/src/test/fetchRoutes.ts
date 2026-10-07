import { vi } from 'vitest';

export type Handler = (init: RequestInit | undefined) => Response | Promise<Response>;

export function json(
  status: number,
  body: unknown,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export function errorBody(
  code: string,
  message: string,
  errors: { field: string; code: string; message: string }[] = [],
  details: Record<string, unknown> | null = null,
) {
  return { code, message, errors, details };
}

/**
 * `"METHOD /path"` → 응답 함수로 fetch를 흉내 낸다. 등록되지 않은 요청은 404.
 * 돌려받은 mock의 `calls`로 보낸 요청을 확인한다.
 */
export function stubFetch(routes: Record<string, Handler>) {
  const mock = vi.fn<typeof fetch>(async (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const path = url.split('?')[0];
    const key = `${init?.method ?? 'GET'} ${path}`;
    const handler = routes[key];
    if (!handler) {
      return json(404, errorBody('NOT_FOUND', '볼 수 없는 페이지예요'));
    }
    return handler(init);
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}

export function requestsTo(mock: ReturnType<typeof stubFetch>, method: string, path: string) {
  return mock.mock.calls.filter(
    ([input, init]) => String(input).split('?')[0] === path && (init?.method ?? 'GET') === method,
  );
}

export const CURRENT_AGREEMENTS = {
  terms: { version: '2026-10-07', effectiveDate: '2026-10-07', path: '/terms' },
  privacy: { version: '2026-10-07', effectiveDate: '2026-10-07', path: '/privacy' },
};

export const ME = {
  memberId: 7,
  handle: 'kim755030',
  nickname: '김민서',
  role: 'USER',
  status: 'ACTIVE',
  provider: 'LOCAL',
  emailVerified: false,
  reagreementRequired: false,
  profileImageUrl: null,
};
