import { useCallback, useEffect, useRef, useState } from 'react';
import type { CursorList } from '../../api/friends';

/**
 * 커서 목록 (친구·받은 요청, FR-055·056). [더 보기]로 다음 페이지를 붙이고, 이미 있는 주소(`handle`)는 건너뛴다(그 사이 순서가 바뀌어
 * 같은 사람이 다시 와도 한 번만). `removeItem`은 화면에서만 뺀다(수락·거절·끊기 뒤).
 */
export function useCursorList<T extends { handle: string }>(
  fetchPage: (cursor?: string | null) => Promise<CursorList<T>>,
) {
  const [items, setItems] = useState<T[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const fetchRef = useRef(fetchPage);

  const apply = useCallback((cursor: string | null, page: CursorList<T>) => {
    setItems((current) => {
      const base = cursor ? current : [];
      const seen = new Set(base.map((item) => item.handle));
      return [...base, ...page.items.filter((item) => !seen.has(item.handle))];
    });
    setNextCursor(page.nextCursor);
  }, []);

  const load = useCallback(
    async (cursor: string | null) => {
      setLoading(true);
      setFailed(false);
      try {
        apply(cursor, await fetchRef.current(cursor));
      } catch {
        setFailed(true);
      } finally {
        setLoading(false);
      }
    },
    [apply],
  );

  useEffect(() => {
    let active = true;
    fetchRef
      .current(null)
      .then((page) => active && apply(null, page))
      .catch(() => active && setFailed(true))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [apply]);

  return {
    items,
    loading,
    failed,
    hasMore: nextCursor !== null,
    loadMore: () => (nextCursor ? load(nextCursor) : Promise.resolve()),
    reload: () => load(null),
    removeItem: (handle: string) =>
      setItems((current) => current.filter((item) => item.handle !== handle)),
  };
}
