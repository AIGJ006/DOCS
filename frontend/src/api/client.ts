/**
 * 공통 API 클라이언트 (001 T042 소유).
 *
 * - 같은 도메인 요청만 보낸다(`credentials: 'same-origin'`, 세션 쿠키 `SESSION`).
 * - 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)에는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 붙인다.
 *   로그인 뒤 토큰이 바뀌므로 요청마다 쿠키를 다시 읽는다(quickstart §2).
 * - 앱 시작 때 `ensureCsrf()`가 `GET /api/auth/csrf`를 한 번 불러 토큰 쿠키를 받는다(main.tsx).
 * - 오류 응답은 공통 본문 `{code, message, errors, details}`를 `ApiError`로 던진다. JSON이 아니면 `code = 'UNKNOWN'`.
 * - 401이면 등록된 `onUnauthorized` 콜백을 부른다(로그인 화면 안내).
 *
 * - 404 `NOT_FOUND`면 등록된 `onNotFound` 콜백을 부른다(공통 404 화면 전환, 004 T024).
 *   요청 옵션 `notFoundScreen: false`면 부르지 않는다(006 줄 단위 오류 표시).
 *
 * 화면별 403 코드 안내(`useAuthGate`)는 004가 이 파일 위에 더한다.
 */

export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';
export const CSRF_ENDPOINT = '/api/auth/csrf';

/** 필드 오류 한 건 (README "정해진 것": 마침표 없음). */
export interface FieldError {
  field: string;
  code: string;
  message: string;
}

/** 공통 오류 본문. `errors`는 항상 배열이다. */
export interface ErrorBody {
  code: string;
  message: string;
  errors: FieldError[];
  details: Record<string, unknown> | null;
}

const UNKNOWN_MESSAGE = '잠시 후 다시 시도해 주세요';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldError[];
  readonly details: Record<string, unknown> | null;
  /** `Retry-After` 헤더(초). 없으면 null. */
  readonly retryAfter: number | null;

  constructor(
    status: number,
    code: string,
    message: string,
    errors: FieldError[] = [],
    details: Record<string, unknown> | null = null,
    retryAfter: number | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.errors = errors;
    this.details = details;
    this.retryAfter = retryAfter;
  }
}

export interface RequestOptions {
  headers?: Record<string, string>;
  signal?: AbortSignal;
  /**
   * `false`면 404 `NOT_FOUND`여도 `onNotFound` 콜백(공통 404 화면 전환)을 부르지 않고 오류만 던진다.
   * 006 내 글 관리처럼 화면은 그대로 두고 그 줄 아래에 이유를 보여야 하는 요청이 쓴다. 기본 `true`.
   */
  notFoundScreen?: boolean;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
type UnauthorizedHandler = (error: ApiError) => void;
type NotFoundHandler = (error: ApiError) => void;

const unauthorizedHandlers = new Set<UnauthorizedHandler>();
const notFoundHandlers = new Set<NotFoundHandler>();
let csrfPromise: Promise<void> | null = null;

/** 401 응답 때 부를 콜백을 등록한다. 돌려받은 함수로 해제한다. */
export function onUnauthorized(handler: UnauthorizedHandler): () => void {
  unauthorizedHandlers.add(handler);
  return () => {
    unauthorizedHandlers.delete(handler);
  };
}

/**
 * 404 `NOT_FOUND` 응답 때 부를 콜백을 등록한다(공통 404 화면 전환, 004 FR-014). 돌려받은 함수로 해제한다.
 * 볼 수 없는 글과 없는 글은 같은 404이므로 이유를 구분하지 않는다.
 */
export function onNotFound(handler: NotFoundHandler): () => void {
  notFoundHandlers.add(handler);
  return () => {
    notFoundHandlers.delete(handler);
  };
}

/** 앱 시작 때 CSRF 토큰 쿠키를 받는다. 성공하면 다시 부르지 않고, 실패하면 다음 호출에서 다시 시도한다. */
export function ensureCsrf(): Promise<void> {
  if (!csrfPromise) {
    csrfPromise = fetch(CSRF_ENDPOINT, {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    }).then((response) => {
      if (!response.ok) {
        throw new ApiError(response.status, 'UNKNOWN', UNKNOWN_MESSAGE);
      }
    });
    csrfPromise.catch(() => {
      csrfPromise = null;
    });
  }
  return csrfPromise;
}

/** 테스트 전용: 모듈 상태(CSRF 호출 여부·콜백)를 비운다. */
export function resetClientForTests(): void {
  csrfPromise = null;
  unauthorizedHandlers.clear();
  notFoundHandlers.clear();
}

export function apiGet<T>(path: string, options?: RequestOptions): Promise<T> {
  return request<T>('GET', path, undefined, options);
}

export function apiPost<T = void>(
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return request<T>('POST', path, body, options);
}

export function apiPut<T = void>(
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return request<T>('PUT', path, body, options);
}

export function apiPatch<T = void>(
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return request<T>('PATCH', path, body, options);
}

export function apiDelete<T = void>(
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return request<T>('DELETE', path, body, options);
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  for (const part of document.cookie.split(';')) {
    const index = part.indexOf('=');
    if (index < 0) {
      continue;
    }
    if (part.slice(0, index).trim() === name) {
      const raw = part.slice(index + 1).trim();
      try {
        return decodeURIComponent(raw);
      } catch {
        return raw;
      }
    }
  }
  return null;
}

function isRawBody(body: unknown): body is BodyInit {
  return (
    typeof body === 'string' ||
    body instanceof FormData ||
    body instanceof URLSearchParams ||
    body instanceof Blob ||
    body instanceof ArrayBuffer
  );
}

async function request<T>(
  method: Method,
  path: string,
  body: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' });
  let payload: BodyInit | undefined;
  if (body !== undefined && body !== null) {
    if (isRawBody(body)) {
      payload = body;
    } else {
      payload = JSON.stringify(body);
      headers.set('Content-Type', 'application/json');
    }
  }
  if (method !== 'GET') {
    const token = readCookie(CSRF_COOKIE);
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }
  for (const [name, value] of Object.entries(options.headers ?? {})) {
    headers.set(name, value);
  }

  const response = await fetch(path, {
    method,
    headers,
    body: payload,
    credentials: 'same-origin',
    signal: options.signal,
  });

  if (!response.ok) {
    const error = await toApiError(response);
    if (error.status === 401) {
      for (const handler of [...unauthorizedHandlers]) {
        handler(error);
      }
    }
    if (error.status === 404 && error.code === 'NOT_FOUND' && options.notFoundScreen !== false) {
      for (const handler of [...notFoundHandlers]) {
        handler(error);
      }
    }
    throw error;
  }
  return (await readBody(response)) as T;
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204 || response.status === 205) {
    return undefined;
  }
  const text = await response.text();
  if (text === '') {
    return undefined;
  }
  const type = response.headers.get('Content-Type') ?? '';
  return type.includes('json') ? (JSON.parse(text) as unknown) : text;
}

function parseRetryAfter(value: string | null): number | null {
  if (value === null || value.trim() === '') {
    return null;
  }
  const seconds = Number(value);
  if (Number.isFinite(seconds)) {
    return Math.max(0, Math.round(seconds));
  }
  const date = Date.parse(value);
  return Number.isNaN(date) ? null : Math.max(0, Math.ceil((date - Date.now()) / 1000));
}

function isErrorBody(value: unknown): value is Partial<ErrorBody> & { code: string } {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { code?: unknown }).code === 'string'
  );
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = parseRetryAfter(response.headers.get('Retry-After'));
  let parsed: unknown;
  try {
    const text = await response.text();
    parsed = text === '' ? null : (JSON.parse(text) as unknown);
  } catch {
    parsed = null;
  }
  if (!isErrorBody(parsed)) {
    return new ApiError(response.status, 'UNKNOWN', UNKNOWN_MESSAGE, [], null, retryAfter);
  }
  return new ApiError(
    response.status,
    parsed.code,
    typeof parsed.message === 'string' ? parsed.message : UNKNOWN_MESSAGE,
    Array.isArray(parsed.errors) ? parsed.errors : [],
    parsed.details && typeof parsed.details === 'object' ? parsed.details : null,
    retryAfter,
  );
}
