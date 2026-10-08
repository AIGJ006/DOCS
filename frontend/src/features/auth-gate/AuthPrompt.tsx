import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { resendVerification } from '../../api/auth';
import { RESTORE_PATH, type AuthPromptKind } from './authGate';

export interface AuthPromptProps {
  kind: AuthPromptKind;
  /** 로그인 안내의 [로그인] 주소 (`useAuthGate().loginPath`) */
  loginPath?: string;
  /** [닫기]를 보이려면 */
  onClose?: () => void;
}

type ResendState = 'idle' | 'sending' | 'sent' | { failed: string };

/**
 * 거부 안내 (004 T053, FR-029): 로그인 / 이메일 인증(001 재발송 API) / 탈퇴 유예 복구 화면 / 정지 안내. 버튼은 안내만 하고 원래 행동을
 * 대신 실행하지 않는다.
 */
export default function AuthPrompt({ kind, loginPath = '/login', onClose }: AuthPromptProps) {
  const [resend, setResend] = useState<ResendState>('idle');

  async function onResend() {
    setResend('sending');
    try {
      await resendVerification();
      setResend('sent');
    } catch (caught) {
      setResend({
        failed: caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요',
      });
    }
  }

  return (
    <div role="alert" className="auth-prompt" data-kind={kind}>
      {kind === 'login' ? (
        <>
          <p>로그인이 필요해요</p>
          <Link to={loginPath}>로그인</Link>
        </>
      ) : null}
      {kind === 'verify-email' ? (
        <>
          <p>이메일 인증 후 이용할 수 있어요</p>
          {resend === 'sent' ? (
            <p>인증 메일을 보냈어요</p>
          ) : (
            <button type="button" disabled={resend === 'sending'} onClick={() => void onResend()}>
              인증 메일 다시 보내기
            </button>
          )}
          {typeof resend === 'object' ? <p>{resend.failed}</p> : null}
        </>
      ) : null}
      {kind === 'restore' ? (
        <>
          <p>탈퇴 신청한 계정이에요</p>
          <Link to={RESTORE_PATH}>계정 복구하기</Link>
        </>
      ) : null}
      {kind === 'suspended' ? <p>정지된 계정이에요</p> : null}
      {onClose ? (
        <button type="button" onClick={onClose}>
          닫기
        </button>
      ) : null}
    </div>
  );
}
