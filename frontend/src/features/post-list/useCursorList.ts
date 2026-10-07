import { useCallback, useEffect, useRef, useState } from 'react';
import type { PostCard, PostCardPage } from '../../api/types/reading';

/**
 * 커서 목록 이어 보기 (005 FR-004·005·016, research R-10).
 *
 * - 첫 요청은 커서 없이, [더 보기]는 마지막 응답의 `nextCursor`를 그대로 보낸다(값을 해석하지 않는다).
 * - 이어 붙일 때 이미 있는 글 번호는 건너뛴다(보는 도중 발행·삭제가 있어도 중복 없음).
 * - `nextCursor`가 null이면 `done` — 버튼을 없애고 "모든 글을 다 봤어요".
 * - 실패하면 `status: 'error'`로 두고 커서를 그대로 유지해 `retry()`가 같은 위치를 다시 요청한다.
 * - `load` 함수가 바뀌면(예: 블로그 주소가 바뀌면) 처음부터 다시 받는다.
 */
export type CursorListStatus = 'idle' | 'loading' | 'error';

export type LoadPage = (cursor?: string | null) => Promise<PostCardPage>;

export interface CursorList {
  items: PostCard[];
  nextCursor: string | null;
  /** 더 없음 (첫 응답을 받은 뒤에만 true) */
  done: boolean;
  status: CursorListStatus;
  /** 첫 응답을 한 번이라도 받았는지 (빈 목록 문구 판단용) */
  loadedOnce: boolean;
  loadMore: () => Promise<void>;
  retry: () => Promise<void>;
}

interface ListState {
  items: PostCard[];
  nextCursor: string | null;
  status: CursorListStatus;
  loadedOnce: boolean;
  done: boolean;
}

const INITIAL: ListState = {
  items: [],
  nextCursor: null,
  status: 'loading',
  loadedOnce: false,
  done: false,
};

/** 이미 있는 글 번호는 건너뛰고 이어 붙인다 (FR-005). */
function append(previous: ListState, page: PostCardPage): ListState {
  const seen = new Set(previous.items.map((item) => item.id));
  return {
    items: [...previous.items, ...page.items.filter((item) => !seen.has(item.id))],
    nextCursor: page.nextCursor,
    done: page.nextCursor === null,
    loadedOnce: true,
    status: 'idle',
  };
}

export function useCursorList(load: LoadPage): CursorList {
  const [state, setState] = useState<ListState>(INITIAL);
  const [source, setSource] = useState<LoadPage>(() => load);
  const inFlight = useRef(false);

  // load가 바뀌면 렌더 중에 처음 상태로 돌린다(효과 안에서 초기화하지 않는다).
  if (source !== load) {
    setSource(() => load);
    setState(INITIAL);
  }

  // 첫 페이지 (load가 바뀌면 다시)
  useEffect(() => {
    let cancelled = false;
    inFlight.current = true;
    void (async () => {
      try {
        const page = await load();
        if (!cancelled) {
          setState((previous) => append(previous, page));
        }
      } catch {
        if (!cancelled) {
          setState((previous) => ({ ...previous, status: 'error' }));
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
      } catch {
        setState((previous) => ({ ...previous, status: 'error' }));
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

  return { ...state, loadMore, retry };
}
