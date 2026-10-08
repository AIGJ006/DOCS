import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { deleteLike, putLike } from '../../api/likes';
import type { LikeNotice } from './likeMessages';

/** 마지막으로 누른 뒤 이만큼 멈추면 마지막 상태 하나만 보낸다 (FR-015, 30 §5). */
export const LIKE_DEBOUNCE_MS = 300;

export interface LikeToggleOptions {
  postId: number | string;
  /** 상세 응답의 `viewer.likedByMe` */
  initialLiked: boolean;
  /** 상세 응답의 `likeCount` */
  initialCount: number;
}

export interface LikeToggle {
  /** 화면에 보일 상태 (누른 즉시 바뀐다) */
  liked: boolean;
  /** 화면에 보일 수 */
  count: number;
  /** 누름: 상태를 바로 뒤집고 0.3초 타이머를 다시 건다 */
  toggle: () => void;
  /** 실패·거부 안내 (없으면 null) */
  notice: LikeNotice | null;
  dismiss: () => void;
}

interface Confirmed {
  liked: boolean;
  count: number;
}

/** 서버 거부를 안내 종류로 바꾼다. 401 → 로그인, 403 계정 상태 → 각 안내, 그 밖 → 실패(429면 남은 초). */
function noticeFor(error: unknown): LikeNotice {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return { kind: 'login' };
    }
    if (error.status === 403) {
      switch (error.code) {
        case 'EMAIL_NOT_VERIFIED':
          return { kind: 'verifyEmail' };
        case 'ACCOUNT_WITHDRAWN':
          return { kind: 'withdrawn' };
        case 'ACCOUNT_SUSPENDED':
          return { kind: 'suspended' };
        default:
          break;
      }
    }
    if (error.status === 429) {
      return { kind: 'failed', retryAfter: error.retryAfter };
    }
  }
  return { kind: 'failed' };
}

/**
 * 좋아요 즉시 반영 (009 T018, research R10, FR-015, SC-005).
 *
 * - 누르면 서버 응답을 기다리지 않고 `liked`·`count`를 바꾼다.
 * - 0.3초 안에 다시 누르면 타이머를 다시 건다. 타이머가 끝났을 때 "마지막으로 원한 상태"가 서버가 확인한 상태와 다를 때만
 *   `PUT`(좋아요)·`DELETE`(취소)를 한 번 보낸다 — 짝수 번 연타는 요청이 없다.
 * - 응답의 `liked`·`likeCount`로 맞춘다(다른 사람 변화 포함). 응답을 기다리는 동안 다시 누르면 응답 뒤에 마지막 상태를 한 번 더 보낸다.
 * - 실패하면 서버가 확인한 상태로 되돌리고 안내한다. 401·403은 로그인·인증 안내로 바꾼다. 다시 보내지 않는다.
 */
export function useLikeToggle({
  postId,
  initialLiked,
  initialCount,
}: LikeToggleOptions): LikeToggle {
  const [confirmed, setConfirmed] = useState<Confirmed>({
    liked: initialLiked,
    count: initialCount,
  });
  const [desired, setDesired] = useState(initialLiked);
  const [notice, setNotice] = useState<LikeNotice | null>(null);

  const confirmedRef = useRef<Confirmed>(confirmed);
  const desiredRef = useRef(initialLiked);
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
      // 응답을 기다리는 동안 다시 눌렀고 타이머도 이미 끝났다면, 응답 뒤에 마지막 상태를 한 번 더 보낸다
      while (
        mounted.current &&
        timer.current === undefined &&
        desiredRef.current !== confirmedRef.current.liked
      ) {
        const state = desiredRef.current ? await putLike(postId) : await deleteLike(postId);
        if (!mounted.current) {
          return;
        }
        confirmedRef.current = { liked: state.liked, count: state.likeCount };
        setConfirmed(confirmedRef.current);
      }
    } catch (error) {
      if (!mounted.current) {
        return;
      }
      if (timer.current !== undefined) {
        clearTimeout(timer.current);
        timer.current = undefined;
      }
      desiredRef.current = confirmedRef.current.liked;
      setDesired(desiredRef.current);
      setNotice(noticeFor(error));
    } finally {
      inFlight.current = false;
    }
  }, [postId]);

  const toggle = useCallback(() => {
    const next = !desiredRef.current;
    desiredRef.current = next;
    setDesired(next);
    setNotice(null);
    if (timer.current !== undefined) {
      clearTimeout(timer.current);
    }
    timer.current = setTimeout(() => void send(), LIKE_DEBOUNCE_MS);
  }, [send]);

  const dismiss = useCallback(() => setNotice(null), []);

  const delta = desired === confirmed.liked ? 0 : desired ? 1 : -1;
  return {
    liked: desired,
    count: Math.max(0, confirmed.count + delta),
    toggle,
    notice,
    dismiss,
  };
}
