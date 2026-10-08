import { Link } from 'react-router-dom';
import { RESTORE_PATH, type AuthPromptKind } from './authGate';
import ResendVerificationButton from './ResendVerificationButton';

export interface AuthPromptProps {
  kind: AuthPromptKind;
  /** 로그인 안내의 [로그인] 주소 (`useAuthGate().loginPath`) */
  loginPath?: string;
  /** [닫기]를 보이려면 */
  onClose?: () => void;
}

/**
 * 거부 안내 (004 T053, FR-029): 로그인 / 이메일 인증(001 재발송 API) / 탈퇴 유예 복구 화면 / 정지 안내. 버튼은 안내만 하고 원래 행동을
 * 대신 실행하지 않는다.
 */
export default function AuthPrompt({ kind, loginPath = '/login', onClose }: AuthPromptProps) {
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
          <ResendVerificationButton />
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
