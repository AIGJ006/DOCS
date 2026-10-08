import { useCallback, useEffect, useRef, useState } from 'react';
import type { PostCard } from '../../api/types/reading';
import * as listRestore from './listRestore';

/**
 * 커서 목록 이어 보기 (005 FR-004·005·016·018, research R-10).
 *
 * - 첫 요청은 커서 없이, [더 보기]는 마지막 응답의 `nextCursor`를 그대로 보낸다(값을 해석하지 않는다).
 * - 이어 붙일 때 이미 있는 글 번호는 건너뛴다(보는 도중 발행·삭제가 있어도 중복 없음).
 * - `nextCursor`가 null이면 `done` — 버튼을 없애고 "모든 글을 다 봤어요".
 * - 실패하면 `status: 'error'`로 두고 커서를 그대로 유지해 `retry()`가 같은 위치를 다시 요청한다.
 *   첫 목록 실패(`initialError`)와 [더 보기] 실패를 구분한다.
 * - `load` 함수가 바뀌면(예: 블로그 주소가 바뀌면) 처음부터 다시 받는다.
 * - 뒤로 가기 복원(005 T069, US6 #1·#2): `listKey`를 주면 불러온 카드·다음 위치·스크롤 위치를 `listRestore`에 둔다
 *   (카드가 늘 때와 화면을 떠날 때 — 라우트 이동으로 내려갈 때·`pagehide`). `restore`가 true이고 30분 안의 값이 있으면
 *   요청 없이 그 카드로 시작하고 스크롤 위치를 되돌린다.
 * - 012: 항목 타입을 넓혔다(검색 결과 카드 `PostSearchItem`처럼 `id`가 있는 값이면 된다. 기본은 `PostCard`). 마지막 실패의
 *   오류를 `error`로 돌려준다(429·410 문구 판단용).
 */
export type CursorListStatus = 'idle' | 'loading' | 'error';

/** 커서 목록 한 페이지 (`PostCardPage`와 같은 모양). */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

/** 목록 항목 — 글 번호로 중복을 거른다. */
export interface CursorItem {
  id: number;
}

export type LoadPage<T extends CursorItem = PostCard> = (
  cursor?: string | null,
) => Promise<CursorPage<T>>;

export interface CursorListOptions {
  /** 복원 저장소 키 — 홈 `home`, 블로그 `blog:{handle}`. 없으면 보관하지 않는다 */
  listKey?: string;
  /** 저장된 목록으로 시작할지 (뒤로 가기로 돌아왔을 때만 true) */
  restore?: boolean;
}

export interface CursorList<T extends CursorItem = PostCard> {
  items: T[];
  nextCursor: string | null;
  /** 더 없음 (첫 응답을 받은 뒤에만 true) */
  done: boolean;
  status: CursorListStatus;
  /** 첫 응답을 한 번이라도 받았는지 (빈 목록 문구 판단용) */
  loadedOnce: boolean;
  /** 첫 목록을 불러오지 못했다 ("글을 불러오지 못했어요 [다시 시도]") */
  initialError: boolean;
  /** 마지막 실패의 오류 (성공하면 null) */
  error: unknown;
  loadMore: () => Promise<void>;
  retry: () => Promise<void>;
}

interface ListState<T extends CursorItem = PostCard> {
  items: T[];
  nextCursor: string | null;
  status: CursorListStatus;
  loadedOnce: boolean;
  done: boolean;
  error: unknown;
}

const INITIAL: ListState<never> = {
  items: [],
  nextCursor: null,
  status: 'loading',
  loadedOnce: false,
  done: false,
  error: null,
};

interface Boot<T extends CursorItem> {
  state: ListState<T>;
  scrollY: number;
}

/** 이미 있는 글 번호는 건너뛰고 이어 붙인다 (FR-005). */
function append<T extends CursorItem>(previous: ListState<T>, page: CursorPage<T>): ListState<T> {
  const seen = new Set(previous.items.map((item) => item.id));
  return {
    items: [...previous.items, ...page.items.filter((item) => !seen.has(item.id))],
    nextCursor: page.nextCursor,
    done: page.nextCursor === null,
    loadedOnce: true,
    status: 'idle',
    error: null,
  };
}

function restoreFrom<T extends CursorItem>(
  listKey: string | undefined,
  restore: boolean,
): Boot<T> | null {
  if (!listKey || !restore) {
    return null;
  }
  const saved = listRestore.load(listKey);
  if (saved === null) {
    return null;
  }
  return {
    state: {
      // 보관한 값은 이 목록 키가 넣은 항목이다 (listRestore는 `id`만 확인한다)
      items: saved.items as unknown as T[],
      nextCursor: saved.nextCursor,
      status: 'idle',
      loadedOnce: true,
      done: saved.nextCursor === null,
      error: null,
    },
    scrollY: saved.scrollY,
  };
}

function currentScrollY(): number {
  return typeof window === 'undefined' ? 0 : window.scrollY || 0;
}

function persist<T extends CursorItem>(listKey: string, state: ListState<T>): void {
  if (state.loadedOnce && state.items.length > 0) {
    listRestore.save(listKey, {
      items: state.items as unknown as PostCard[],
      nextCursor: state.nextCursor,
      scrollY: currentScrollY(),
    });
  }
}

export function useCursorList<T extends CursorItem = PostCard>(
  load: LoadPage<T>,
  options: CursorListOptions = {},
): CursorList<T> {
  const { listKey, restore = false } = options;
  const [boot] = useState<Boot<T> | null>(() => restoreFrom<T>(listKey, restore));
  const [state, setState] = useState<ListState<T>>(() => boot?.state ?? INITIAL);
  const [source, setSource] = useState<LoadPage<T>>(() => load);
  const inFlight = useRef(false);
  // 저장된 목록으로 시작한 `load`는 첫 요청을 건너뛴다
  const skipFirstFetch = useRef<LoadPage<T> | null>(boot ? load : null);
  const latest = useRef<ListState<T>>(state);

  // load가 바뀌면 렌더 중에 처음 상태로 돌린다(효과 안에서 초기화하지 않는다).
  if (source !== load) {
    setSource(() => load);
    setState(INITIAL);
  }

  useEffect(() => {
    latest.current = state;
  });

  // 복원한 스크롤 위치 (카드를 그린 뒤)
  useEffect(() => {
    if (boot) {
      window.scrollTo(0, boot.scrollY);
    }
  }, [boot]);

  // 첫 페이지 (load가 바뀌면 다시)
  useEffect(() => {
    if (skipFirstFetch.current === load) {
      return undefined;
    }
    skipFirstFetch.current = null;
    let cancelled = false;
    inFlight.current = true;
    void (async () => {
      try {
        const page = await load();
        if (!cancelled) {
          setState((previous) => append(previous, page));
        }
      } catch (error) {
        if (!cancelled) {
          setState((previous) => ({ ...previous, status: 'error', error }));
        }
      } finally {
        inFlight.current = false;
      }
    })();
    return () => {
      cancelled = true;
      inFlight.current = false;
    };
  }, [load]);

  // 카드가 늘면 보관한다 ([더 보기] 성공 포함)
  useEffect(() => {
    if (listKey) {
      persist(listKey, state);
    }
  }, [listKey, state]);

  // 화면을 떠날 때(라우트 이동으로 내려갈 때·페이지를 닫거나 새로 고칠 때) 스크롤 위치와 함께 보관한다
  useEffect(() => {
    if (!listKey) {
      return undefined;
    }
    const onPageHide = () => persist(listKey, latest.current);
    window.addEventListener('pagehide', onPageHide);
    return () => {
      window.removeEventListener('pagehide', onPageHide);
      persist(listKey, latest.current);
    };
  }, [listKey]);

  const fetchPage = useCallback(
    async (cursor: string | null) => {
      if (inFlight.current) {
        return;
      }
      inFlight.current = true;
      setState((previous) => ({ ...previous, status: 'loading' }));
      try {
        const page = await load(cursor ?? undefined);
        setState((previous) => append(previous, page));
      } catch (error) {
        setState((previous) => ({ ...previous, status: 'error', error }));
      } finally {
        inFlight.current = false;
      }
    },
    [load],
  );

  const loadMore = useCallback(async () => {
    if (state.loadedOnce && state.nextCursor === null) {
      return;
    }
    await fetchPage(state.nextCursor);
  }, [fetchPage, state.loadedOnce, state.nextCursor]);

  const retry = useCallback(async () => {
    await fetchPage(state.loadedOnce ? state.nextCursor : null);
  }, [fetchPage, state.loadedOnce, state.nextCursor]);

  return {
    ...state,
    initialError: state.status === 'error' && !state.loadedOnce,
    loadMore,
    retry,
  };
}
