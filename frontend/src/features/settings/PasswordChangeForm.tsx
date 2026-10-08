import { useState, type FormEvent } from 'react';
import { ApiError, type FieldError } from '../../api/client';
import { changePassword } from '../../api/auth';
import PasswordRuleChecklist from '../../components/PasswordRuleChecklist';
import { formatWait, groupFieldErrors } from '../auth/fieldErrors';

const MESSAGES: Record<string, string> = {
  CURRENT_PASSWORD_MISMATCH: '현재 비밀번호가 올바르지 않아요',
  PASSWORD_SAME_AS_CURRENT: '지금 비밀번호와 다른 비밀번호를 입력해 주세요',
  PASSWORD_NOT_SUPPORTED: '소셜 로그인 계정은 비밀번호가 없어요',
};

interface Props {
  /** 설정의 `passwordChangeAvailable`(이메일 가입 계정만). false면 그리지 않는다 */
  available: boolean;
}

/**
 * 비밀번호 변경 (11 §6-2, FR-045). 성공하면 다른 기기는 로그아웃되고 지금 기기는 그대로다.
 * 현재 비밀번호를 5번 연속 틀리면 15분 잠기며 남은 시간을 보인다.
 */
export default function PasswordChangeForm({ available }: Props) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, FieldError[]>>({});
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!available) {
    return null;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setMessage(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await changePassword(current, next, confirm);
      setCurrent('');
      setNext('');
      setConfirm('');
      setMessage({ kind: 'ok', text: '비밀번호를 바꿨어요. 다른 기기에서는 로그아웃됐어요' });
    } catch (caught) {
      if (!(caught instanceof ApiError)) {
        setMessage({ kind: 'error', text: '잠시 후 다시 시도해 주세요' });
      } else if (caught.status === 429) {
        const wait =
          caught.retryAfter !== null ? ` (${formatWait(caught.retryAfter)} 남았어요)` : '';
        setMessage({ kind: 'error', text: `${caught.message}${wait}` });
      } else if (caught.code === 'VALIDATION_FAILED') {
        setFieldErrors(groupFieldErrors(caught.errors));
      } else {
        setMessage({ kind: 'error', text: MESSAGES[caught.code] ?? caught.message });
      }
    } finally {
      setSubmitting(false);
    }
  }

  const errorsOf = (field: string) => fieldErrors[field] ?? [];
  return (
    <form className="auth-form" onSubmit={onSubmit} noValidate aria-labelledby="pw-change-title">
      <h2 id="pw-change-title">비밀번호 변경</h2>
      <div className="field">
        <label htmlFor="pw-current">현재 비밀번호</label>
        <input
          id="pw-current"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor="pw-new">새 비밀번호</label>
        <input
          id="pw-new"
          type="password"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          aria-describedby="pw-new-rules"
          aria-invalid={errorsOf('newPassword').length > 0 || undefined}
        />
        <PasswordRuleChecklist id="pw-new-rules" password={next} />
        {errorsOf('newPassword').map((e) => (
          <p key={e.code} className="field-error">
            {e.message}
          </p>
        ))}
      </div>
      <div className="field">
        <label htmlFor="pw-confirm">새 비밀번호 확인</label>
        <input
          id="pw-confirm"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          aria-invalid={errorsOf('newPasswordConfirm').length > 0 || undefined}
        />
        {errorsOf('newPasswordConfirm').map((e) => (
          <p key={e.code} className="field-error">
            {e.message}
          </p>
        ))}
      </div>
      {message && (
        <p role={message.kind === 'ok' ? 'status' : 'alert'} className="form-error">
          {message.text}
        </p>
      )}
      <button type="submit" className="primary" disabled={submitting}>
        비밀번호 변경
      </button>
    </form>
  );
}
