import { useEffect, useId, useRef } from 'react';
import './dialogs.css';

/** 확인창에 넣는 문구. 글자는 텍스트로만 렌더링한다(`dangerouslySetInnerHTML` 금지, 헌법 IV). */
export interface ConfirmOptions {
  /** 굵은 제목 (없으면 본문만) */
  title?: string;
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
}

interface ConfirmDialogProps extends ConfirmOptions {
  onClose: (confirmed: boolean) => void;
}

/**
 * 공통 확인창 (006 T012). `role="dialog"`·`aria-modal`, 처음 포커스는 확인 버튼, Esc·[취소]는 닫기(false),
 * Tab은 창 안에서만 돈다. 닫히면 열기 전에 포커스가 있던 곳으로 돌려준다.
 *
 * (구현 메모) 002의 `components/editor/ConfirmDialog.tsx`(처음 포커스 = 취소, `alertdialog`)는 에디터 화면이 그대로
 * 쓰고, 이 컴포넌트는 006 tasks T012의 규칙(처음 포커스 = 확인, `confirm()` 훅)을 따른다.
 */
export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  cancelLabel = '취소',
  onClose,
}: ConfirmDialogProps) {
  const id = useId();
  const boxRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmRef.current?.focus();
  }, []);

  return (
    <div className="app-dialog-backdrop">
      <div
        ref={boxRef}
        className="app-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? `${id}-title` : undefined}
        aria-label={title ? undefined : confirmLabel}
        aria-describedby={`${id}-message`}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.stopPropagation();
            onClose(false);
            return;
          }
          if (event.key === 'Tab') {
            const buttons = boxRef.current?.querySelectorAll<HTMLButtonElement>('button');
            if (!buttons || buttons.length === 0) {
              return;
            }
            const first = buttons[0];
            const last = buttons[buttons.length - 1];
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault();
              last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault();
              first.focus();
            }
          }
        }}
      >
        {title ? (
          <h2 id={`${id}-title`} className="app-dialog-title">
            {title}
          </h2>
        ) : null}
        <p id={`${id}-message`}>{message}</p>
        <div className="app-dialog-actions">
          <button type="button" onClick={() => onClose(false)}>
            {cancelLabel}
          </button>
          <button
            type="button"
            ref={confirmRef}
            className="app-dialog-confirm"
            onClick={() => onClose(true)}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
