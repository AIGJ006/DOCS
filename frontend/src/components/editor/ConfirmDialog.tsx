import { useEffect, useRef } from 'react';

/**
 * 확인 창 (002 — [변경 취소]·[편집 중인 내용으로 저장]). 006 `confirmDialogs.ts`가 생기면 문구를 그쪽에서 가져온다.
 * 결과를 이름에 담은 버튼 두 개를 두고, 처음 초점은 안전한 쪽(취소)에 둔다. Esc는 취소.
 */
interface Props {
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  message,
  confirmLabel,
  cancelLabel,
  busy = false,
  onConfirm,
  onCancel,
}: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    cancelRef.current?.focus();
  }, []);

  return (
    <div
      className="confirm-dialog"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-message"
      onKeyDown={(e) => {
        if (e.key === 'Escape' && !busy) {
          onCancel();
        }
      }}
    >
      <div className="confirm-dialog-box">
        <p id="confirm-dialog-message">{message}</p>
        <div className="dialog-actions">
          <button type="button" ref={cancelRef} onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button type="button" onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
