import { act, renderHook } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/client';
import { authPromptFor, loginPathFor } from './authGate';
import { useAuthGate, type AuthGateOptions } from './useAuthGate';

function setup(path: string, options?: AuthGateOptions) {
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(MemoryRouter, { initialEntries: [path] }, children);
  return renderHook(
    () => {
      const gate = useAuthGate(options);
      const location = useLocation();
      return { gate, location };
    },
    { wrapper },
  );
}

const unauthorized = new ApiError(401, 'LOGIN_REQUIRED', '로그인이 필요해요');
const forbidden = (code: string) => new ApiError(403, code, '거부');

/** 공통 거부 처리 (004 T051·T052, FR-029, H6). */
describe('useAuthGate', () => {
  it('401이면 지금 경로(쿼리 포함)를 returnTo로 붙여 로그인 화면으로 옮긴다', () => {
    const { result } = setup('/@kim/posts/42?ref=home');

    let handled = false;
    act(() => {
      handled = result.current.gate.handle(unauthorized);
    });

    expect(handled).toBe(true);
    expect(result.current.location.pathname).toBe('/login');
    expect(new URLSearchParams(result.current.location.search).get('returnTo')).toBe(
      '/@kim/posts/42?ref=home',
    );
  });

  it('로그인 주소에는 돌아올 경로만 있고 누르려던 행동은 담지 않는다 (자동 실행 없음, H6)', () => {
    expect(loginPathFor('/@kim/posts/42')).toBe('/login?returnTo=%2F%40kim%2Fposts%2F42');
    const { result } = setup('/@kim/posts/42');
    act(() => {
      result.current.gate.handle(unauthorized);
    });
    const params = new URLSearchParams(result.current.location.search);
    expect([...params.keys()]).toEqual(['returnTo']);
  });

  it("'prompt' 모드의 401은 화면을 떠나지 않고 로그인 안내를 띄운다", () => {
    const { result } = setup('/@kim/posts/42', { unauthorized: 'prompt' });

    act(() => {
      result.current.gate.handle(unauthorized);
    });

    expect(result.current.location.pathname).toBe('/@kim/posts/42');
    expect(result.current.gate.prompt).toBe('login');
    expect(result.current.gate.loginPath).toBe(loginPathFor('/@kim/posts/42'));
    act(() => result.current.gate.dismiss());
    expect(result.current.gate.prompt).toBeNull();
  });

  it.each([
    ['EMAIL_NOT_VERIFIED', 'verify-email'],
    ['ACCOUNT_WITHDRAWN', 'restore'],
    ['ACCOUNT_SUSPENDED', 'suspended'],
  ] as const)('403 %s → %s 안내', (code, kind) => {
    const { result } = setup('/manage/posts');

    act(() => {
      expect(result.current.gate.handle(forbidden(code))).toBe(true);
    });

    expect(result.current.gate.prompt).toBe(kind);
    expect(result.current.location.pathname).toBe('/manage/posts');
  });

  it('그 밖의 오류는 처리하지 않고 호출한 화면에 넘긴다', () => {
    const { result } = setup('/manage/posts');

    for (const error of [
      new ApiError(404, 'NOT_FOUND', '볼 수 없는 페이지예요'),
      new ApiError(400, 'INVALID_VISIBILITY', '공개 범위를 다시 선택해 주세요'),
      forbidden('CSRF_REJECTED'),
      new Error('network'),
    ]) {
      act(() => {
        expect(result.current.gate.handle(error)).toBe(false);
      });
    }
    expect(result.current.gate.prompt).toBeNull();
    expect(authPromptFor('문자열')).toBeNull();
  });
});
