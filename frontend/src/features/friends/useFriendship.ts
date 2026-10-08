import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  getFriendship,
  removeFriendship,
  requestFriendship,
  type FriendshipView,
} from '../../api/friends';

const FAILED_MESSAGE = '잠시 후 다시 시도해 주세요';

/**
 * 나와 그 회원의 친구 관계 (FR-054~056). `enabled`가 false(비로그인)면 읽지 않는다. `request`·`remove`는 응답 상태로 갱신하고, 실패하면
 * 상태를 그대로 두고 `error`에 이유를 둔다.
 */
export function useFriendship(handle: string, enabled: boolean) {
  const [view, setView] = useState<FriendshipView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    let active = true;
    getFriendship(handle)
      .then((value) => active && setView(value))
      .catch(() => active && setView(null));
    return () => {
      active = false;
    };
  }, [handle, enabled]);

  const run = useCallback(async (action: () => Promise<FriendshipView>) => {
    setBusy(true);
    setError(null);
    try {
      setView(await action());
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : FAILED_MESSAGE);
    } finally {
      setBusy(false);
    }
  }, []);

  return {
    view,
    error,
    busy,
    request: () => run(() => requestFriendship(handle)),
    remove: () => run(() => removeFriendship(handle)),
  };
}
