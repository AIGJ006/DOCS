import { useCallback, useEffect, useReducer, useRef } from 'react';
import { deleteComment, listComments, listReplies } from '../../api/comments';
import type {
  CommentPage,
  CommentView,
  ReplyPage,
  RootCommentView,
} from '../../api/types/comments';

/**
 * 한 글의 댓글 목록 상태 (007 T023·T034·T042·T053, research R14).
 *
 * - `rootIds`(화면 순서) + `byId`(id → 댓글) + `replies`(최상위 id → 답글 id·커서·전체 수). 같은 id는 한 번만 그린다.
 * - [댓글 더 보기] 결과는 아직 없는 id만 끝에, [이전 댓글 보기]는 앞에 붙인다.
 * - 내가 쓴 댓글은 응답을 받는 즉시 끝(답글이면 그 최상위 답글 끝)에 붙이고, 나중에 같은 id가 페이지로 와도 다시 붙이지 않는다
 *   (Clarifications Q5).
 * - 머리말 수는 상세 응답의 `commentCount`에서 시작해 작성 +1·삭제 −1을 화면에서 반영한다(숨긴 댓글 삭제는 이미 빠져 있어 그대로).
 * - 삭제 응답(204)에는 결과 모양이 없으므로 서버 규칙(data-model §2)대로 화면에서 정한다: 답글이 남은 최상위는 "삭제된 댓글이에요"
 *   자리, 그 밖은 사라짐, 자리의 마지막 답글이 지워지면 자리도 사라짐.
 */
export type LoadState = 'idle' | 'loading' | 'failed';

export interface RepliesState {
  ids: number[];
  nextCursor: string | null;
  /** 숨긴 답글 포함 전체 수 (`replyCount`) */
  total: number;
  load: LoadState;
}

export interface ThreadState {
  first: 'loading' | 'ready' | 'failed';
  rootIds: number[];
  byId: Record<number, CommentView>;
  replies: Record<number, RepliesState>;
  nextCursor: string | null;
  more: LoadState;
  prevCursor: string | null;
  prev: LoadState;
  /** around 대상 — 스크롤·강조가 끝나면 null */
  focusId: number | null;
  count: number;
}

type Action =
  | { type: 'first/start' }
  | { type: 'first/loaded'; page: CommentPage }
  | { type: 'first/failed' }
  | { type: 'more/start' }
  | { type: 'more/loaded'; page: CommentPage }
  | { type: 'more/failed' }
  | { type: 'prev/start' }
  | { type: 'prev/loaded'; page: CommentPage }
  | { type: 'prev/failed' }
  | { type: 'replies/start'; rootId: number }
  | { type: 'replies/loaded'; rootId: number; page: ReplyPage }
  | { type: 'replies/failed'; rootId: number }
  | { type: 'mine/added'; view: CommentView }
  | { type: 'edited'; view: CommentView }
  | { type: 'deleted'; id: number }
  | { type: 'focus/clear' };

export function initialThread(count: number): ThreadState {
  return {
    first: 'loading',
    rootIds: [],
    byId: {},
    replies: {},
    nextCursor: null,
    more: 'idle',
    prevCursor: null,
    prev: 'idle',
    focusId: null,
    count,
  };
}

function plain(view: CommentView): CommentView {
  return {
    id: view.id,
    state: view.state,
    content: view.content,
    createdAt: view.createdAt,
    edited: view.edited,
    author: view.author,
    replyTo: view.replyTo,
    mine: view.mine,
    parentId: view.parentId,
  };
}

/** 페이지의 최상위들을 앞(`prepend`)이나 끝(`append`)에 붙인다. 이미 있는 id는 건너뛰되 답글은 없는 것만 더한다. */
function mergeRoots(
  state: ThreadState,
  items: RootCommentView[],
  where: 'append' | 'prepend',
): Pick<ThreadState, 'rootIds' | 'byId' | 'replies'> {
  const byId = { ...state.byId };
  const replies = { ...state.replies };
  const fresh: number[] = [];
  for (const item of items) {
    if (!(item.id in byId)) {
      fresh.push(item.id);
    }
    byId[item.id] = plain(item);
    const known = replies[item.id];
    const ids = known ? [...known.ids] : [];
    for (const reply of item.replies ?? []) {
      if (!ids.includes(reply.id)) {
        ids.push(reply.id);
      }
      byId[reply.id] = plain(reply);
    }
    replies[item.id] = known
      ? { ...known, ids }
      : {
          ids,
          nextCursor: item.repliesNextCursor ?? null,
          total: Math.max(item.replyCount ?? 0, ids.length),
          load: 'idle',
        };
  }
  const rootIds = where === 'append' ? [...state.rootIds, ...fresh] : [...fresh, ...state.rootIds];
  return { rootIds, byId, replies };
}

function withoutKey<T>(record: Record<number, T>, key: number): Record<number, T> {
  const next = { ...record };
  delete next[key];
  return next;
}

export function threadReducer(state: ThreadState, action: Action): ThreadState {
  switch (action.type) {
    case 'first/start':
      return { ...state, first: 'loading' };
    case 'first/loaded': {
      const empty = { ...initialThread(state.count), first: 'ready' as const };
      return {
        ...empty,
        ...mergeRoots(empty, action.page.items, 'append'),
        nextCursor: action.page.nextCursor,
        prevCursor: action.page.prevCursor,
        focusId: action.page.focusCommentId,
      };
    }
    case 'first/failed':
      return { ...state, first: 'failed' };
    case 'more/start':
      return { ...state, more: 'loading' };
    case 'more/loaded':
      return {
        ...state,
        ...mergeRoots(state, action.page.items, 'append'),
        nextCursor: action.page.nextCursor,
        more: 'idle',
      };
    case 'more/failed':
      return { ...state, more: 'failed' };
    case 'prev/start':
      return { ...state, prev: 'loading' };
    case 'prev/loaded':
      return {
        ...state,
        ...mergeRoots(state, action.page.items, 'prepend'),
        prevCursor: action.page.prevCursor,
        prev: 'idle',
      };
    case 'prev/failed':
      return { ...state, prev: 'failed' };
    case 'replies/start':
    case 'replies/failed': {
      const current = state.replies[action.rootId];
      if (!current) {
        return state;
      }
      const load = action.type === 'replies/start' ? 'loading' : 'failed';
      return { ...state, replies: { ...state.replies, [action.rootId]: { ...current, load } } };
    }
    case 'replies/loaded': {
      const current = state.replies[action.rootId];
      if (!current) {
        return state;
      }
      const byId = { ...state.byId };
      const ids = [...current.ids];
      for (const reply of action.page.items) {
        if (!ids.includes(reply.id)) {
          ids.push(reply.id);
        }
        byId[reply.id] = plain(reply);
      }
      return {
        ...state,
        byId,
        replies: {
          ...state.replies,
          [action.rootId]: {
            ids,
            nextCursor: action.page.nextCursor,
            total: Math.max(current.total, ids.length),
            load: 'idle',
          },
        },
      };
    }
    case 'mine/added': {
      const view = action.view;
      if (view.id in state.byId) {
        return state;
      }
      const byId = { ...state.byId, [view.id]: plain(view) };
      if (view.parentId === null) {
        return {
          ...state,
          byId,
          rootIds: [...state.rootIds, view.id],
          replies: {
            ...state.replies,
            [view.id]: { ids: [], nextCursor: null, total: 0, load: 'idle' },
          },
          count: state.count + 1,
        };
      }
      const current = state.replies[view.parentId] ?? {
        ids: [],
        nextCursor: null,
        total: 0,
        load: 'idle' as const,
      };
      return {
        ...state,
        byId,
        replies: {
          ...state.replies,
          [view.parentId]: { ...current, ids: [...current.ids, view.id], total: current.total + 1 },
        },
        count: state.count + 1,
      };
    }
    case 'edited':
      if (!(action.view.id in state.byId)) {
        return state;
      }
      return { ...state, byId: { ...state.byId, [action.view.id]: plain(action.view) } };
    case 'deleted': {
      const view = state.byId[action.id];
      if (!view) {
        return state;
      }
      const count = view.state === 'HIDDEN' ? state.count : Math.max(0, state.count - 1);
      if (view.parentId === null) {
        const own = state.replies[view.id];
        if (own && own.total > 0) {
          return {
            ...state,
            count,
            byId: {
              ...state.byId,
              [view.id]: { ...view, state: 'DELETED', content: null, author: null, edited: false },
            },
          };
        }
        return {
          ...state,
          count,
          rootIds: state.rootIds.filter((id) => id !== view.id),
          byId: withoutKey(state.byId, view.id),
          replies: withoutKey(state.replies, view.id),
        };
      }
      const rootId = view.parentId;
      const siblings = state.replies[rootId];
      let byId = withoutKey(state.byId, view.id);
      let replies = state.replies;
      let rootIds = state.rootIds;
      if (siblings) {
        const total = Math.max(0, siblings.total - 1);
        replies = {
          ...replies,
          [rootId]: { ...siblings, ids: siblings.ids.filter((id) => id !== view.id), total },
        };
        if (total === 0 && state.byId[rootId]?.state === 'DELETED') {
          // 빈 자리 정리 (FR-012, C-CMT-1 #5)
          byId = withoutKey(byId, rootId);
          replies = withoutKey(replies, rootId);
          rootIds = rootIds.filter((id) => id !== rootId);
        }
      }
      return { ...state, count, byId, replies, rootIds };
    }
    case 'focus/clear':
      return { ...state, focusId: null };
    default:
      return state;
  }
}

export interface UseCommentThreadOptions {
  postId: number;
  /** 상세 응답의 `commentCount` */
  commentCount: number;
  /** 주소의 `?comment=` 값 */
  aroundCommentId?: string | null;
  /** 상위(글 상세)가 상세 요청과 동시에 시작한 첫 페이지 요청 (Clarifications Q1) */
  initialPage?: Promise<CommentPage> | null;
}

export function useCommentThread({
  postId,
  commentCount,
  aroundCommentId = null,
  initialPage = null,
}: UseCommentThreadOptions) {
  const [state, dispatch] = useReducer(threadReducer, commentCount, initialThread);
  const [attempt, retryFirst] = useReducer((n: number) => n + 1, 0);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const request =
      attempt === 0 && initialPage
        ? initialPage
        : listComments(postId, { around: aroundCommentId });
    request.then(
      (page) => {
        if (!cancelled) {
          dispatch({ type: 'first/loaded', page });
        }
      },
      () => {
        if (!cancelled) {
          dispatch({ type: 'first/failed' });
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [postId, aroundCommentId, initialPage, attempt]);

  const reloadFirst = useCallback(() => {
    dispatch({ type: 'first/start' });
    retryFirst();
  }, []);

  const loadMore = useCallback(async () => {
    if (state.more === 'loading' || state.nextCursor === null) {
      return;
    }
    dispatch({ type: 'more/start' });
    try {
      const page = await listComments(postId, { cursor: state.nextCursor });
      if (alive.current) {
        dispatch({ type: 'more/loaded', page });
      }
    } catch {
      if (alive.current) {
        dispatch({ type: 'more/failed' });
      }
    }
  }, [postId, state.more, state.nextCursor]);

  const loadPrevious = useCallback(async () => {
    if (state.prev === 'loading' || state.prevCursor === null) {
      return;
    }
    dispatch({ type: 'prev/start' });
    try {
      const page = await listComments(postId, { cursor: state.prevCursor });
      if (alive.current) {
        dispatch({ type: 'prev/loaded', page });
      }
    } catch {
      if (alive.current) {
        dispatch({ type: 'prev/failed' });
      }
    }
  }, [postId, state.prev, state.prevCursor]);

  const loadReplies = useCallback(
    async (rootId: number) => {
      const current = state.replies[rootId];
      if (!current || current.load === 'loading' || current.nextCursor === null) {
        return;
      }
      dispatch({ type: 'replies/start', rootId });
      try {
        const page = await listReplies(rootId, current.nextCursor);
        if (alive.current) {
          dispatch({ type: 'replies/loaded', rootId, page });
        }
      } catch {
        if (alive.current) {
          dispatch({ type: 'replies/failed', rootId });
        }
      }
    },
    [state.replies],
  );

  const addMine = useCallback((view: CommentView) => dispatch({ type: 'mine/added', view }), []);
  const applyEdited = useCallback((view: CommentView) => dispatch({ type: 'edited', view }), []);
  const applyDeleted = useCallback((id: number) => dispatch({ type: 'deleted', id }), []);
  const clearFocus = useCallback(() => dispatch({ type: 'focus/clear' }), []);

  /** 지우고 화면에 반영한다. 실패하면 오류를 그대로 던진다(부른 쪽이 문구를 보인다). */
  const remove = useCallback(async (id: number) => {
    await deleteComment(id);
    if (alive.current) {
      dispatch({ type: 'deleted', id });
    }
  }, []);

  return {
    state,
    reloadFirst,
    loadMore,
    loadPrevious,
    loadReplies,
    addMine,
    applyEdited,
    applyDeleted,
    remove,
    clearFocus,
  };
}

export type CommentThread = ReturnType<typeof useCommentThread>;
