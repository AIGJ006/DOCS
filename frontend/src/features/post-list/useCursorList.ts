import { useCallback, useEffect, useRef, useState } from 'react';
import type { PostCard, PostCardPage } from '../../api/types/reading';

/**
 * 커서 목록 이어 보기 (005 FR-004·005·016, research R-10).
 *
 * - 첫 요청은 커서 없이, [더 보기]는 마지막 응답의 `nextCursor`를 그대로 보낸다(값을 해석하지 않는다).
 * - 이어 붙일 때 이미 있는 글 번호는 건너뛴다(보는 도중 발행·삭제가 있어도 중복 없음).
 * - `nextCursor`가 null이면 `done` — 버튼을 없애고 "모든 글을 다 봤어요".
 * - 실패하면 `status: 'error'`로 두고 커서를 그대로 유지해 `retry()`가 같은 위치를 다시 요청한다.
 */
export type CursorListStatus = 'idle' | 'loading' | 'error';

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

export type LoadPage = (cursor?: string | null) => Promise<PostCardPage>;

export function useCursorList(load: LoadPage): CursorList {
  const [items, setItems] = useState<PostCard[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [status, setStatus] = useState<CursorListStatus>('loading');
  const [loadedOnce, setLoadedOnce] = useState(false);
  const [done, setDone] = useState(false);
  const loading = useRef(false);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const fetchPage = useCallback(
    async (cursor: string | null) => {
      if (loading.current) {
        return;
      }
      loading.current = true;
      setStatus('loading');
      try {
        const page = await load(cursor ?? undefined);
        if (!alive.current) {
          return;
        }
        setItems((previous) => {
          const seen = new Set(previous.map((item) => item.id));
          return [...previous, ...page.items.filter((item) => !seen.has(item.id))];
        });
        setNextCursor(page.nextCursor);
        setDone(page.nextCursor === null);
        setLoadedOnce(true);
        setStatus('idle');
      } catch {
        if (alive.current) {
          setStatus('error');
        }
      } finally {
        loading.current = false;
      }
    },
    [load],
  );

  // 첫 페이지 (load가 바뀌면 — 예: 블로그 주소가 바뀌면 — 처음부터 다시)
  useEffect(() => {
    setItems([]);
    setNextCursor(null);
    setDone(false);
    setLoadedOnce(false);
    void fetchPage(null);
  }, [fetchPage]);

  const loadMore = useCallback(async () => {
    if (status === 'loading' || loading.current) {
      return;
    }
    if (loadedOnce && nextCursor === null) {
      return;
    }
    await fetchPage(nextCursor);
  }, [fetchPage, loadedOnce, nextCursor, status]);

  const retry = useCallback(async () => {
    await fetchPage(loadedOnce ? nextCursor : null);
  }, [fetchPage, loadedOnce, nextCursor]);

  return { items, nextCursor, done, status, loadedOnce, loadMore, retry };
}
