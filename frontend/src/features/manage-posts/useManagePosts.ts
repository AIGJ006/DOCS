import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { authPromptFor } from '../auth-gate/authGate';
import { useAuthGate } from '../auth-gate/useAuthGate';
import {
  listMyPosts,
  type ManageCounts,
  type ManagePostItem,
  type ManagePostPage,
  type ManageTab,
  type VisibilityFilter,
} from '../../api/managePosts';

export interface ManageQuery {
  tab: ManageTab;
  /** 발행 글 탭의 [공개]·[비공개] 필터 (그 밖의 탭은 무시) */
  visibility: VisibilityFilter | null;
}

export type CountsDelta = Partial<Record<keyof ManageCounts, number>>;

export interface ManagePostsState {
  items: ManagePostItem[];
  nextCursor: string | null;
  counts: ManageCounts | null;
  loading: boolean;
  error: string | null;
  /** [더 보기] — 이미 화면에 있는 글은 건너뛰고 이어 붙인다 */
  loadMore: () => Promise<void>;
  /** 첫 페이지를 다시 부른다 (글 수도 새로 받는다) */
  reload: () => Promise<void>;
  removeRow: (id: number) => void;
  updateRow: (id: number, patch: Partial<ManagePostItem>) => void;
  /** 화면의 탭 옆 숫자만 바꾼다 (서버 재계산 없음, FR-004) */
  adjustCounts: (delta: CountsDelta) => void;
}

const LOAD_FAILED = '목록을 불러오지 못했어요';

/**
 * 내 글 관리 목록 상태 (006 T046, FR-001·004·006·013, research R17).
 *
 * - 서버 커서는 값 기반이라 다른 탭에서 자동 저장해 순서가 바뀐 글이 [더 보기]에 한 번 더 올 수 있다 — 이미 화면에 있는
 *   id는 건너뛴다(SC-006).
 * - `counts`는 첫 응답 값만 쓰고 [더 보기]의 `null`로 덮지 않는다. 줄 처리 뒤에는 `adjustCounts`로 화면 숫자만 고친다.
 * - 탭·필터가 바뀌면 커서·목록을 비우고 첫 페이지를 다시 부른다. 늦게 온 이전 탭 응답은 버린다.
 * - 401이면 004 `useAuthGate`가 로그인 화면으로 보내고 지금 주소를 `returnTo`로 붙인다(FR-001).
 */
export function useManagePosts({ tab, visibility }: ManageQuery): ManagePostsState {
  const gate = useAuthGate();
  const filter = tab === 'published' ? visibility : null;

  const [items, setItems] = useState<ManagePostItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [counts, setCounts] = useState<ManageCounts | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  /** 지금 화면의 탭·필터 요청 번호 — 늦게 온 이전 응답을 버린다 */
  const generation = useRef(0);
  /** 주소(필터)가 바뀔 때마다 목록을 다시 부르지 않게 최신 처리기를 ref로 든다 */
  const handleAuthRef = useRef(gate.handle);
  useEffect(() => {
    handleAuthRef.current = gate.handle;
  }, [gate.handle]);

  const fail = useCallback((caught: unknown) => {
    if (authPromptFor(caught) === 'login') {
      handleAuthRef.current(caught);
      return;
    }
    setError(caught instanceof ApiError ? caught.message : LOAD_FAILED);
  }, []);

  // 탭·필터가 바뀌면 렌더 중에 이전 탭의 줄·커서를 비운다(효과 안에서 초기화하지 않는다)
  const queryKey = `${tab}|${filter ?? ''}`;
  const [shownKey, setShownKey] = useState(queryKey);
  if (shownKey !== queryKey) {
    setShownKey(queryKey);
    setItems([]);
    setNextCursor(null);
    setLoading(true);
    setError(null);
  }

  /** 첫 페이지 응답을 반영한다 (`mine`이 지금 요청 번호일 때만) */
  const applyFirst = useCallback((mine: number, page: ManagePostPage) => {
    if (mine !== generation.current) {
      return;
    }
    setItems(page.items);
    setNextCursor(page.nextCursor);
    if (page.counts) {
      setCounts(page.counts);
    }
    setLoading(false);
  }, []);

  const failFirst = useCallback(
    (mine: number, caught: unknown) => {
      if (mine === generation.current) {
        setLoading(false);
        fail(caught);
      }
    },
    [fail],
  );

  useEffect(() => {
    const mine = ++generation.current;
    listMyPosts({ tab, visibility: filter }).then(
      (page) => applyFirst(mine, page),
      (caught: unknown) => failFirst(mine, caught),
    );
  }, [tab, filter, applyFirst, failFirst]);

  /** 첫 페이지를 다시 부른다 (지금 줄은 응답이 올 때까지 둔다) */
  const reload = useCallback(async () => {
    const mine = ++generation.current;
    setLoading(true);
    setError(null);
    try {
      applyFirst(mine, await listMyPosts({ tab, visibility: filter }));
    } catch (caught) {
      failFirst(mine, caught);
    }
  }, [tab, filter, applyFirst, failFirst]);

  const loadMore = useCallback(async () => {
    if (!nextCursor) {
      return;
    }
    const mine = generation.current;
    setLoading(true);
    setError(null);
    try {
      const page = await listMyPosts({ tab, visibility: filter, cursor: nextCursor });
      if (mine !== generation.current) {
        return;
      }
      setItems((current) => {
        const seen = new Set(current.map((row) => row.id));
        return [...current, ...page.items.filter((row) => !seen.has(row.id))];
      });
      setNextCursor(page.nextCursor);
    } catch (caught) {
      if (mine === generation.current) {
        fail(caught);
      }
    } finally {
      if (mine === generation.current) {
        setLoading(false);
      }
    }
  }, [tab, filter, nextCursor, fail]);

  const removeRow = useCallback((id: number) => {
    setItems((current) => current.filter((row) => row.id !== id));
  }, []);

  const updateRow = useCallback((id: number, patch: Partial<ManagePostItem>) => {
    setItems((current) => current.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  }, []);

  const adjustCounts = useCallback((delta: CountsDelta) => {
    setCounts((current) =>
      current
        ? {
            drafts: Math.max(0, current.drafts + (delta.drafts ?? 0)),
            published: Math.max(0, current.published + (delta.published ?? 0)),
            trash: Math.max(0, current.trash + (delta.trash ?? 0)),
          }
        : current,
    );
  }, []);

  return {
    items,
    nextCursor,
    counts,
    loading,
    error,
    loadMore,
    reload,
    removeRow,
    updateRow,
    adjustCounts,
  };
}
