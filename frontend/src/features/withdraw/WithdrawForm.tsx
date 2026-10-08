import { useId, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import {
  withdraw,
  type WithdrawalPreview,
  type WithdrawRequest,
  type WithdrawResult,
} from '../../api/withdrawal';
import { formatDeadline } from './formatDeadline';
import {
  GENERIC_FAILED,
  PLACEHOLDER_NOTE,
  WITHDRAW_CANCEL,
  WITHDRAW_CONFIRM_LABEL,
  WITHDRAW_CONFIRM_TEXT_LABEL,
  WITHDRAW_EFFECTS_TITLE,
  WITHDRAW_LOCKED,
  WITHDRAW_PASSWORD_LABEL,
  WITHDRAW_SUBMIT,
  WITHDRAW_SUBMITTING,
  WITHDRAW_VERIFY_TITLE,
  effectLines,
} from './withdrawMessages';

const NUMBER = new Intl.NumberFormat('ko-KR');

/** 오류를 보일 자리 (R12): 확인 칸 아래, 본인 확인 칸 아래, 화면 위(관리자·그 밖). */
interface FormErrors {
  confirm?: string;
  verify?: string;
  top?: string;
}

/**
 * 탈퇴 신청 폼 (015 T030, docs/44 §2, FR-003·FR-007). 사유 입력 칸은 없다(FR-004).
 *
 * - [탈퇴하기]는 빨간 버튼이지만 처음 포커스가 아니고(`autoFocus` 없음, 탭 순서 마지막), 칸 안에서 Enter로 제출되지 않는다 —
 *   버튼을 직접 눌러야 한다(FR-007). 그래서 처음 포커스가 [확인]인 공통 확인창(`useConfirm`)은 쓰지 않는다.
 * - 체크와 본인 확인 입력이 모두 있어야 버튼이 켜진다. 429(비밀번호 잠금)면 버튼을 끈다.
 * - 숫자·주소는 텍스트 노드로만 그린다.
 */
export default function WithdrawForm({
  preview,
  onWithdrawn,
}: {
  preview: WithdrawalPreview;
  onWithdrawn: (result: WithdrawResult) => void | Promise<void>;
}) {
  const id = useId();
  const [confirmed, setConfirmed] = useState(false);
  const [secret, setSecret] = useState('');
  const [errors, setErrors] = useState<FormErrors>({});
  const [locked, setLocked] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const byPassword = preview.verification === 'PASSWORD';
  const verifyId = `${id}-verify`;
  const verifyErrorId = `${id}-verify-error`;
  const confirmId = `${id}-confirm`;
  const confirmErrorId = `${id}-confirm-error`;
  const ready = confirmed && secret.trim().length > 0 && !locked && !submitting;

  const lines = effectLines({
    handle: preview.handle,
    postCount: NUMBER.format(preview.postCount),
    commentCount: NUMBER.format(preview.commentCount),
    receivedLikeCount: NUMBER.format(preview.receivedLikeCount),
    deadline: formatDeadline(preview.restoreDeadline),
  });

  function blockEnter(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === 'Enter' && !(event.target instanceof HTMLButtonElement)) {
      event.preventDefault();
    }
  }

  async function submit() {
    if (!ready) {
      return;
    }
    setErrors({});
    setSubmitting(true);
    const body: WithdrawRequest = byPassword
      ? { confirmed: true, password: secret }
      : { confirmed: true, confirmText: secret };
    try {
      const result = await withdraw(body);
      await onWithdrawn(result);
    } catch (caught) {
      setSubmitting(false);
      if (!(caught instanceof ApiError)) {
        setErrors({ top: GENERIC_FAILED });
        return;
      }
      switch (caught.code) {
        case 'CURRENT_PASSWORD_MISMATCH':
        case 'CONFIRM_TEXT_MISMATCH':
          setSecret('');
          setErrors({ verify: caught.message });
          break;
        case 'WITHDRAW_CONFIRM_REQUIRED':
          setErrors({ confirm: caught.message });
          break;
        case 'PASSWORD_CHANGE_TEMPORARILY_LOCKED':
          setLocked(true);
          setErrors({ verify: WITHDRAW_LOCKED });
          break;
        default:
          setErrors({ top: caught.message || GENERIC_FAILED });
      }
    }
  }

  function onSubmit(event: FormEvent) {
    // Enter·암묵적 제출은 막는다. 제출은 [탈퇴하기] 클릭으로만 (FR-007)
    event.preventDefault();
  }

  return (
    <form className="auth-form" noValidate onSubmit={onSubmit} onKeyDown={blockEnter}>
      {errors.top && (
        <p role="alert" className="form-error notice">
          {errors.top}
        </p>
      )}
      <section className="withdraw-section" aria-labelledby={`${id}-effects`}>
        <h2 id={`${id}-effects`}>① {WITHDRAW_EFFECTS_TITLE}</h2>
        <ul className="withdraw-effects">
          {lines.map((line, index) => (
            <li key={line}>
              {line}
              {index === 3 && <p className="withdraw-note">({PLACEHOLDER_NOTE})</p>}
            </li>
          ))}
        </ul>
        <div className="withdraw-check">
          <input
            id={confirmId}
            type="checkbox"
            checked={confirmed}
            aria-describedby={errors.confirm ? confirmErrorId : undefined}
            onChange={(event) => setConfirmed(event.target.checked)}
          />
          <label htmlFor={confirmId}>{WITHDRAW_CONFIRM_LABEL}</label>
        </div>
        {errors.confirm && (
          <p id={confirmErrorId} role="alert" className="field-error">
            {errors.confirm}
          </p>
        )}
      </section>
      <section className="withdraw-section" aria-labelledby={`${id}-verify-title`}>
        <h2 id={`${id}-verify-title`}>② {WITHDRAW_VERIFY_TITLE}</h2>
        <div className="field">
          <label htmlFor={verifyId}>
            {byPassword ? WITHDRAW_PASSWORD_LABEL : WITHDRAW_CONFIRM_TEXT_LABEL}
          </label>
          <input
            id={verifyId}
            type={byPassword ? 'password' : 'text'}
            autoComplete={byPassword ? 'current-password' : 'off'}
            value={secret}
            aria-invalid={errors.verify ? true : undefined}
            aria-describedby={errors.verify ? verifyErrorId : undefined}
            onChange={(event) => setSecret(event.target.value)}
          />
          {errors.verify && (
            <p id={verifyErrorId} role="alert" className="field-error">
              {errors.verify}
            </p>
          )}
        </div>
      </section>
      <div className="withdraw-actions">
        <Link to="/settings">{WITHDRAW_CANCEL}</Link>
        <button type="button" className="danger" disabled={!ready} onClick={() => void submit()}>
          {submitting ? WITHDRAW_SUBMITTING : WITHDRAW_SUBMIT}
        </button>
      </div>
    </form>
  );
}
