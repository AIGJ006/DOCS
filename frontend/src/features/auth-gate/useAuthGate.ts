import { useCallback, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { authPromptFor, loginPathFor, type AuthPromptKind } from './authGate';

export interface AuthGateOptions {
  /**
   * 401일 때: `'redirect'`(기본)면 곧바로 로그인 화면으로 옮기고, `'prompt'`면 그 자리에 로그인 안내를 띄운다(버튼을 누른 비회원 —
   * 읽던 화면을 떠나지 않는다).
   */
  unauthorized?: 'redirect' | 'prompt';
}

export interface AuthGate {
  /**
   * 오류가 로그인·계정 상태 거부면 안내하고 `true`. 그 밖의 오류는 `false`라 호출한 화면이 처리한다. 다시 시도하거나 원래 행동을 대신
   * 실행하지 않는다.
   */
  handle: (error: unknown) => boolean;
  /** 지금 띄울 안내 (없으면 `null`) */
  prompt: AuthPromptKind | null;
  /** 서버에 묻기 전에 화면이 아는 사실(비회원·인증 전)로 안내를 띄운다 */
  show: (kind: AuthPromptKind) => void;
  dismiss: () => void;
  /** 지금 경로로 돌아오는 로그인 주소 */
  loginPath: string;
}

/**
 * 공통 거부 처리 (004 T052, FR-029, research R-29). 401 → 로그인 화면(`returnTo` = 지금 경로), 403 계정 상태 → code별
 * `AuthPrompt`. 화면은 서버 판정을 따른다 — 버튼을 숨기는 것은 보조다(42 P-1).
 */
export function useAuthGate({ unauthorized = 'redirect' }: AuthGateOptions = {}): AuthGate {
  const navigate = useNavigate();
  const location = useLocation();
  const [prompt, setPrompt] = useState<AuthPromptKind | null>(null);
  const loginPath = loginPathFor(location.pathname + location.search);

  const handle = useCallback(
    (error: unknown) => {
      const kind = authPromptFor(error);
      if (kind === null) {
        return false;
      }
      if (kind === 'login' && unauthorized === 'redirect') {
        navigate(loginPath, { replace: true });
        return true;
      }
      setPrompt(kind);
      return true;
    },
    [loginPath, navigate, unauthorized],
  );

  const dismiss = useCallback(() => setPrompt(null), []);
  const show = useCallback((kind: AuthPromptKind) => setPrompt(kind), []);

  return { handle, prompt, show, dismiss, loginPath };
}
