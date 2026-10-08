import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { ALREADY_HANDLED } from './confirmDialogs';
import { useRowAction } from './useRowAction';

/** 줄 단위 처리 (006 T015, FR-013, research R24). */
describe('useRowAction', () => {
  it('실행 중에는 그 줄만 busy이고 성공하면 onSuccess를 부른다', async () => {
    const reload = vi.fn();
    const { result } = renderHook(() => useRowAction({ reload }));
    let finish!: (value: string) => void;
    const onSuccess = vi.fn();

    let running!: Promise<void>;
    act(() => {
      running = result.current.run(7, () => new Promise<string>((resolve) => (finish = resolve)), {
        onSuccess,
      });
    });
    expect(result.current.isBusy(7)).toBe(true);
    expect(result.current.isBusy(8)).toBe(false);

    await act(async () => {
      finish('ok');
      await running;
    });
    expect(onSuccess).toHaveBeenCalledWith('ok');
    expect(result.current.isBusy(7)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
  });

  it('404 NOT_FOUND면 줄 아래 이유를 넣고 목록을 다시 부른다', async () => {
    const reload = vi.fn();
    const { result } = renderHook(() => useRowAction({ reload }));

    await act(() =>
      result.current.run(7, () =>
        Promise.reject(new ApiError(404, 'NOT_FOUND', '볼 수 없는 페이지예요')),
      ),
    );

    expect(result.current.rowErrors[7]).toBe(ALREADY_HANDLED);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('그 밖의 오류는 서버 message를 보이고 다시 부르지 않는다', async () => {
    const reload = vi.fn();
    const { result } = renderHook(() => useRowAction({ reload }));

    await act(() =>
      result.current.run(7, () =>
        Promise.reject(new ApiError(403, 'ACCOUNT_SUSPENDED', '정지된 계정이에요')),
      ),
    );
    expect(result.current.rowErrors[7]).toBe('정지된 계정이에요');
    expect(reload).not.toHaveBeenCalled();

    await act(() => result.current.run(7, () => Promise.resolve(1)));
    expect(result.current.rowErrors[7]).toBeUndefined();
  });
});
