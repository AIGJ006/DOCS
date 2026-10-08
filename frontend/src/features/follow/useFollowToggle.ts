import { useCallback, useEffect, useRef, useState } from 'react';
import { follow, unfollow } from '../../api/follows';

/** 마지막으로 누른 뒤 이만큼 멈추면 마지막 상태 하나만 보낸다 (009 `useLikeToggle`과 같은 0.3초). */
export const FOLLOW_DEBOUNCE_MS = 300;

export interface FollowToggleOptions {
  handle: string;
  /** 처음 상태 (`followedByMe`·`viewer.followingAuthor`) */
  initialFollowing: boolean;
  /** 처음 팔로워 수 (모르면 0 — 버튼만 쓰는 곳) */
  initialCount?: number;
}

export interface FollowToggle {
  /** 화면에 보일 상태 (누른 즉시 바뀐다) */
  following: boolean;
  /** 화면에 보일 대상의 팔로워 수 */
  count: number;
  /** 누름: 상태를 바로 뒤집고 0.3초 타이머를 다시 건다 */
  toggle: () => void;
  /** 마지막 실패 (없으면 null). 화면은 401·403이면 로그인·계정 안내, 그 밖이면 "잠시 후 다시 시도해 주세요" */
  error: unknown;
  dismiss: () => void;
}

interface Confirmed {
  following: boolean;
  count: number;
}

/**
 * 팔로우 즉시 반영 (010 T021, research R9, FR-010, SC-009). 009 `useLikeToggle`과 같은 방식이다.
 *
 * - 누르면 서버 응답을 기다리지 않고 `following`·`count`를 바꾼다.
 * - 0.3초 안에 다시 누르면 타이머를 다시 건다. 타이머가 끝났을 때 마지막으로 원한 상태가 서버가 확인한 상태와 다를 때만
 *   `PUT`(팔로우)·`DELETE`(언팔로우)를 한 번 보낸다 — 짝수 번 연타는 요청이 없다.
 * - 응답의 `following`·`followerCount`로 맞춘다(다른 사람 변화 포함). 응답을 기다리는 동안 다시 누르면 응답 뒤에 마지막 상태를 한 번 더 보낸다.
 * - 실패하면 서버가 확인한 상태로 되돌리고 `error`를 둔다. 다시 보내지 않는다.
 */
export function useFollowToggle({
  handle,
  initialFollowing,
  initialCount = 0,
}: FollowToggleOptions): FollowToggle {
  const [confirmed, setConfirmed] = useState<Confirmed>({
    following: initialFollowing,
    count: initialCount,
  });
  const [desired, setDesired] = useState(initialFollowing);
  const [error, setError] = useState<unknown>(null);

  const confirmedRef = useRef<Confirmed>(confirmed);
  const desiredRef = useRef(initialFollowing);
  const inFlight = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      if (timer.current !== undefined) {
        clearTimeout(timer.current);
      }
    };
  }, []);

  const send = useCallback(async () => {
    timer.current = undefined;
    if (inFlight.current) {
      return; // 응답 뒤에 다시 판단한다
    }
    inFlight.current = true;
    try {
      while (
        mounted.current &&
        timer.current === undefined &&
        desiredRef.current !== confirmedRef.current.following
      ) {
        const state = desiredRef.current ? await follow(handle) : await unfollow(handle);
        if (!mounted.current) {
          return;
        }
        confirmedRef.current = { following: state.following, count: state.followerCount };
        setConfirmed(confirmedRef.current);
      }
    } catch (failure) {
      if (!mounted.current) {
        return;
      }
      if (timer.current !== undefined) {
        clearTimeout(timer.current);
        timer.current = undefined;
      }
      desiredRef.current = confirmedRef.current.following;
      setDesired(desiredRef.current);
      setError(failure ?? new Error('follow failed'));
    } finally {
      inFlight.current = false;
    }
  }, [handle]);

  const toggle = useCallback(() => {
    const next = !desiredRef.current;
    desiredRef.current = next;
    setDesired(next);
    setError(null);
    if (timer.current !== undefined) {
      clearTimeout(timer.current);
    }
    timer.current = setTimeout(() => void send(), FOLLOW_DEBOUNCE_MS);
  }, [send]);

  const dismiss = useCallback(() => setError(null), []);

  const delta = desired === confirmed.following ? 0 : desired ? 1 : -1;
  return {
    following: desired,
    count: Math.max(0, confirmed.count + delta),
    toggle,
    error,
    dismiss,
  };
}
