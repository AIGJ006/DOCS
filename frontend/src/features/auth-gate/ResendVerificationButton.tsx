import { useState } from 'react';
import { ApiError } from '../../api/client';
import { resendVerification } from '../../api/auth';

type ResendState = 'idle' | 'sending' | 'sent' | { failed: string };

/** [인증 메일 다시 보내기] (001 `POST /api/auth/email-verification`). 보낸 뒤에는 "인증 메일을 보냈어요"로 바뀐다. */
export default function ResendVerificationButton() {
  const [state, setState] = useState<ResendState>('idle');

  async function onResend() {
    setState('sending');
    try {
      await resendVerification();
      setState('sent');
    } catch (caught) {
      setState({
        failed: caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요',
      });
    }
  }

  if (state === 'sent') {
    return <p>인증 메일을 보냈어요</p>;
  }
  return (
    <>
      <button type="button" disabled={state === 'sending'} onClick={() => void onResend()}>
        인증 메일 다시 보내기
      </button>
      {typeof state === 'object' ? <p>{state.failed}</p> : null}
    </>
  );
}
