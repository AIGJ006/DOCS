import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * E2E 공통 (002). 이메일 인증을 마친 회원의 이메일·비밀번호를 환경 변수로 받는다:
 * `E2E_EMAIL`, `E2E_PASSWORD`. 없으면 시험을 건너뛴다.
 */
export const E2E_EMAIL = process.env.E2E_EMAIL ?? '';
export const E2E_PASSWORD = process.env.E2E_PASSWORD ?? '';
export const hasAccount = E2E_EMAIL !== '' && E2E_PASSWORD !== '';

async function csrf(request: APIRequestContext): Promise<string> {
  await request.get('/api/auth/csrf');
  const state = await request.storageState();
  const cookie = state.cookies.find((c) => c.name === 'XSRF-TOKEN');
  if (!cookie) {
    throw new Error('XSRF-TOKEN 쿠키가 없습니다');
  }
  return decodeURIComponent(cookie.value);
}

/** 페이지와 쿠키를 함께 쓰는 요청 컨텍스트로 로그인한다. */
export async function login(page: Page): Promise<void> {
  const request = page.request;
  const token = await csrf(request);
  const response = await request.post('/api/auth/login', {
    form: { email: E2E_EMAIL, password: E2E_PASSWORD },
    headers: { 'X-XSRF-TOKEN': token },
  });
  expect(response.status(), await response.text()).toBe(200);
}

/** 상태를 바꾸는 API 호출 (CSRF 헤더 포함). */
export async function api(
  page: Page,
  method: 'POST' | 'PUT',
  path: string,
  data: unknown,
  headers: Record<string, string> = {},
) {
  const token = await csrf(page.request);
  return page.request.fetch(path, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': token, ...headers },
  });
}

export async function createPost(page: Page, body: Record<string, string> = {}): Promise<number> {
  const response = await api(page, 'POST', '/api/posts', body);
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { postId: number }).postId;
}

export async function publish(
  page: Page,
  postId: number,
  title: string,
  contentMd: string,
  baseVersion = 0,
) {
  const response = await api(
    page,
    'POST',
    `/api/posts/${postId}/publish`,
    { title, contentMd, tags: [], visibility: 'PUBLIC', baseVersion },
    { 'Idempotency-Key': crypto.randomUUID() },
  );
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as { url: string; version: number };
}

/** docs/12 §9-1 공격 문자열 32개 (백엔드 테스트 코퍼스와 같은 파일). */
export function xssCorpus(): { name: string; markdown: string }[] {
  const dir = join(import.meta.dirname, '../../backend/src/test/resources/markdown/xss');
  return readdirSync(dir)
    .filter((f) => f.endsWith('.md'))
    .sort()
    .map((name) => ({ name, markdown: readFileSync(join(dir, name), 'utf8') }));
}
