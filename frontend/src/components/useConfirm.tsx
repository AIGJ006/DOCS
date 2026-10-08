import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { ConfirmDialog, type ConfirmOptions } from './ConfirmDialog';

interface Pending {
  options: ConfirmOptions;
  resolve: (confirmed: boolean) => void;
  returnFocus: HTMLElement | null;
}

/**
 * `const { confirm, dialog } = useConfirm()` — `await confirm({...})`가 [확인]이면 true, [취소]·Esc면 false.
 * `dialog`를 화면 어딘가에 렌더링한다. 이미 열린 창이 있으면 그 창을 false로 닫고 새 창을 연다.
 */
export function useConfirm(): {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  dialog: ReactNode;
} {
  const [pending, setPending] = useState<Pending | null>(null);
  const pendingRef = useRef<Pending | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => {
    pendingRef.current?.resolve(false);
    return new Promise<boolean>((resolve) => {
      const next: Pending = {
        options,
        resolve,
        returnFocus: document.activeElement instanceof HTMLElement ? document.activeElement : null,
      };
      pendingRef.current = next;
      setPending(next);
    });
  }, []);

  useEffect(
    () => () => {
      pendingRef.current?.resolve(false);
    },
    [],
  );

  const dialog = pending ? (
    <ConfirmDialog
      {...pending.options}
      onClose={(confirmed) => {
        pendingRef.current = null;
        setPending(null);
        pending.returnFocus?.focus();
        pending.resolve(confirmed);
      }}
    />
  ) : null;

  return { confirm, dialog };
}
