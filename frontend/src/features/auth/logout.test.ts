import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { logout, registerLogoutCleanup, resetLogoutCleanupForTests } from './logout';

type FetchMock = ReturnType<typeof vi.fn<typeof fetch>>;

let fetchMock: FetchMock;
let assign: ReturnType<typeof vi.fn>;
const calls: string[] = [];

beforeEach(() => {
  calls.length = 0;
  resetClientForTests();
  resetLogoutCleanupForTests();
  localStorage.clear();
  fetchMock = vi.fn<typeof fetch>(async (input) => {
    calls.push(`fetch ${String(input)}`);
    return new Response(null, { status: 204 });
  });
  vi.stubGlobal('fetch', fetchMock);
  assign = vi.fn((url: string) => {
    calls.push(`assign ${url}`);
  });
  vi.stubGlobal('location', { ...window.location, assign });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('logout', () => {
  it('그 회원의 임시 글을 지운 뒤 POST /api/auth/logout 하고 홈으로 간다', async () => {
    const clearMemberDrafts = vi.fn(async (memberId: number) => {
      calls.push(`clear ${memberId}`);
    });
    registerLogoutCleanup({ clearMemberDrafts });

    await logout(42);

    expect(clearMemberDrafts).toHaveBeenCalledWith(42);
    expect(calls).toEqual(['clear 42', 'fetch /api/auth/logout', 'assign /']);
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('POST');
  });

  it('테마 설정은 지우지 않는다', async () => {
    localStorage.setItem('theme', 'dark');
    registerLogoutCleanup({ clearMemberDrafts: async () => undefined });

    await logout(42);

    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('002 정리 함수가 아직 없어도 서버 로그아웃과 홈 이동은 한다', async () => {
    await logout(42);
    expect(calls).toEqual(['fetch /api/auth/logout', 'assign /']);
  });

  it('미전송 작업 전송이 실패하면 확인을 받고, 취소하면 로그아웃하지 않는다', async () => {
    const confirm = vi.fn(() => false);
    vi.stubGlobal('confirm', confirm);
    const clearMemberDrafts = vi.fn(async () => undefined);
    registerLogoutCleanup({ flushPendingWork: async () => false, clearMemberDrafts });

    const done = await logout(42);

    expect(done).toBe(false);
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('보내지 못한 작업이 지워져요'));
    expect(clearMemberDrafts).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(assign).not.toHaveBeenCalled();
  });

  it('미전송 작업 전송은 최대 3초만 기다린다', async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    );
    registerLogoutCleanup({ flushPendingWork: () => new Promise<boolean>(() => undefined) });

    const pending = logout(42);
    await vi.advanceTimersByTimeAsync(3000);
    await expect(pending).resolves.toBe(true);
    expect(calls).toEqual(['fetch /api/auth/logout', 'assign /']);
  });

  it('이미 로그아웃된 세션(401)이어도 홈으로 간다', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ code: 'LOGIN_REQUIRED', message: 'x', errors: [], details: null }),
        {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    await logout(42);
    expect(assign).toHaveBeenCalledWith('/');
  });
});
