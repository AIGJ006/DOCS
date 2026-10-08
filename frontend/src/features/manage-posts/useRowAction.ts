import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { ACTION_FAILED, ALREADY_HANDLED } from './confirmDialogs';

export interface RowActionOptions<T> {
  onSuccess?: (result: T) => void;
}

export interface RowActions {
  /** 그 줄의 처리를 실행한다. 실행 중에는 그 줄 버튼을 비활성화하고, 실패하면 그 줄 아래에 이유를 둔다 */
  run: <T>(rowId: number, action: () => Promise<T>, options?: RowActionOptions<T>) => Promise<void>;
  isBusy: (rowId: number) => boolean;
  /** 줄 번호 → 그 줄 아래에 보일 오류 문구 */
  rowErrors: Record<number, string>;
}

/**
 * 줄 단위 처리 (006 T015, FR-013, research R24). 화면 전체를 다시 그리지 않고 그 줄만 바꾼다.
 *
 * - 404 `NOT_FOUND`(다른 탭에서 이미 휴지통으로 옮겼거나 지운 글)면 "이미 처리된 글이에요…"를 보이고 `reload()`로 목록을
 *   다시 부른다.
 * - 그 밖의 실패는 서버 `message`를 그대로 보인다(문구는 텍스트로만 렌더링).
 * - 401은 `useManagePosts`가 로그인 화면으로 보낸다(목록 재조회 때).
 */
export function useRowAction({ reload }: { reload: () => void | Promise<void> }): RowActions {
  const [busy, setBusy] = useState<ReadonlySet<number>>(new Set());
  const [rowErrors, setRowErrors] = useState<Record<number, string>>({});
  const busyRef = useRef(new Set<number>());
  const reloadRef = useRef(reload);
  useEffect(() => {
    reloadRef.current = reload;
  }, [reload]);

  const setRowError = useCallback((rowId: number, message: string | null) => {
    setRowErrors((current) => {
      if (message === null) {
        if (!(rowId in current)) {
          return current;
        }
        const next = { ...current };
        delete next[rowId];
        return next;
      }
      return { ...current, [rowId]: message };
    });
  }, []);

  const run = useCallback(
    async <T>(rowId: number, action: () => Promise<T>, options: RowActionOptions<T> = {}) => {
      if (busyRef.current.has(rowId)) {
        return;
      }
      busyRef.current.add(rowId);
      setBusy(new Set(busyRef.current));
      setRowError(rowId, null);
      try {
        const result = await action();
        options.onSuccess?.(result);
      } catch (caught) {
        if (caught instanceof ApiError && caught.status === 404 && caught.code === 'NOT_FOUND') {
          setRowError(rowId, ALREADY_HANDLED);
          void reloadRef.current();
        } else {
          setRowError(rowId, caught instanceof ApiError ? caught.message : ACTION_FAILED);
        }
      } finally {
        busyRef.current.delete(rowId);
        setBusy(new Set(busyRef.current));
      }
    },
    [setRowError],
  );

  const isBusy = useCallback((rowId: number) => busy.has(rowId), [busy]);

  return { run, isBusy, rowErrors };
}
