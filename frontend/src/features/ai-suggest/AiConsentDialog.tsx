import { useEffect, useId, useRef, type KeyboardEvent } from 'react';
import {
  AI_CONSENT_AGREE,
  AI_CONSENT_CANCEL,
  AI_CONSENT_LINES,
  AI_CONSENT_TITLE,
  AI_CONSENT_VERSION,
} from './aiConsentText';
import './aiSuggest.css';

/**
 * AI 외부 전송 동의 창 (013 T036, research R10·R13). `role="dialog"`, 초점은 창 안에 가두고 처음 초점은 [동의하고 추천받기].
 * Esc·[취소]는 아무것도 보내지 않는다.
 */
interface Props {
  busy?: boolean;
  error?: string | null;
  confirmLabel?: string;
  onAgree: () => void;
  onCancel: () => void;
}

export default function AiConsentDialog({
  busy = false,
  error = null,
  confirmLabel = AI_CONSENT_AGREE,
  onAgree,
  onCancel,
}: Props) {
  const titleId = useId();
  const boxRef = useRef<HTMLDivElement>(null);
  const agreeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    agreeRef.current?.focus();
  }, []);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      if (!busy) {
        onCancel();
      }
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }
    const focusable = Array.from(
      boxRef.current?.querySelectorAll<HTMLElement>('button:not([disabled])') ?? [],
    );
    if (focusable.length === 0) {
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="ai-consent" onKeyDown={onKeyDown}>
      <div
        ref={boxRef}
        className="ai-consent__box"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <h3 id={titleId}>{AI_CONSENT_TITLE}</h3>
        <ul className="ai-consent__lines">
          {AI_CONSENT_LINES.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
        <p className="ai-consent__version">동의 문구 버전 {AI_CONSENT_VERSION}</p>
        {error ? (
          <p role="alert" className="form-error">
            {error}
          </p>
        ) : null}
        <div className="dialog-actions">
          <button type="button" onClick={onCancel} disabled={busy}>
            {AI_CONSENT_CANCEL}
          </button>
          <button
            type="button"
            ref={agreeRef}
            className="ai-consent__agree"
            onClick={onAgree}
            disabled={busy}
            aria-busy={busy || undefined}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
