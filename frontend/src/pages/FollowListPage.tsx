import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { listFollowers, listFollowing } from '../api/follows';
import { getBlogHeader } from '../api/members';
import type { FollowListItem as Item, FollowListPage as Page } from '../api/types/follow';
import { ALL_SEEN_TEXT, LOAD_FAILED_TEXT, LOADING_TEXT } from '../components/LoadMoreButton';
import FollowListItem from '../features/follow/FollowListItem';
import { FOLLOW_MESSAGES, followListTitle } from '../features/follow/followMessages';
import NotFoundPage from './NotFoundPage';
import '../features/follow/follow.css';

export type FollowListMode = 'followers' | 'following';

interface ListState {
  key: string;
  items: Item[];
  nextCursor: string | null;
  status: 'loading' | 'idle' | 'error';
  loadedOnce: boolean;
  notFound: boolean;
}

function initial(key: string): ListState {
  return {
    key,
    items: [],
    nextCursor: null,
    status: 'loading',
    loadedOnce: false,
    notFound: false,
  };
}

/** 이미 있는 주소는 건너뛰고 이어 붙인다. */
function append(previous: ListState, page: Page): ListState {
  const seen = new Set(previous.items.map((item) => item.handle));
  return {
    ...previous,
    items: [...previous.items, ...page.items.filter((item) => !seen.has(item.handle))],
    nextCursor: page.nextCursor,
    status: 'idle',
    loadedOnce: true,
  };
}

/**
 * 팔로워·팔로잉 목록 (010 T038, US3, FR-014~016, research R9). `/@{handle}/followers`·`/@{handle}/following` — 로그인
 * 없이 누구나 본다.
 *
 * - 제목 "{닉네임}님의 팔로워"/"팔로잉"(머리말 API), 항목 20개씩 [더 보기](`nextCursor`를 그대로 보냄). 복원은 하지 않는다.
 * - 빈 목록 "아직 팔로워가 없어요"/"아직 팔로우한 사람이 없어요". 없는 주소·탈퇴 유예는 공통 404 화면.
 */
export default function FollowListPage({ mode }: { mode: FollowListMode }) {
  const params = useParams();
  const rawHandle = params.handle ?? '';
  const blogAddress = rawHandle.startsWith('@');
  const handle = blogAddress ? rawHandle.slice(1) : '';
  const key = `${mode}:${handle}`;

  const [nickname, setNickname] = useState<{ handle: string; value: string } | null>(null);
  const [state, setState] = useState<ListState>(() => initial(key));
  if (state.key !== key) {
    setState(initial(key));
  }

  const load = useCallback(
    (cursor?: string | null) =>
      mode === 'followers' ? listFollowers(handle, cursor) : listFollowing(handle, cursor),
    [handle, mode],
  );

  useEffect(() => {
    if (!blogAddress) {
      return undefined;
    }
    let cancelled = false;
    getBlogHeader(handle).then(
      (header) => {
        if (!cancelled) {
          setNickname({ handle, value: header.nickname });
        }
      },
      () => {
        // 제목만 못 채운다 — 목록 요청이 404면 그쪽이 404 화면을 띄운다
      },
    );
    return () => {
      cancelled = true;
    };
  }, [handle, blogAddress]);

  useEffect(() => {
    if (!blogAddress) {
      return undefined;
    }
    let cancelled = false;
    load().then(
      (page) => {
        if (!cancelled) {
          setState((previous) => append(previous, page));
        }
      },
      (error: unknown) => {
        if (!cancelled) {
          const notFound = error instanceof ApiError && error.status === 404;
          setState((previous) => ({ ...previous, status: 'error', notFound }));
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [load, blogAddress]);

  const loadMore = useCallback(() => {
    if (state.status === 'loading') {
      return;
    }
    const cursor = state.loadedOnce ? state.nextCursor : null;
    setState((previous) => ({ ...previous, status: 'loading' }));
    load(cursor).then(
      (page) => setState((previous) => append(previous, page)),
      () => setState((previous) => ({ ...previous, status: 'error' })),
    );
  }, [load, state.loadedOnce, state.nextCursor, state.status]);

  if (!blogAddress || state.notFound) {
    return <NotFoundPage />;
  }

  const title =
    nickname !== null && nickname.handle === handle ? followListTitle(nickname.value, mode) : null;
  const empty = state.loadedOnce && state.items.length === 0;
  const done = state.loadedOnce && state.nextCursor === null;

  return (
    <main data-route={mode} className="follow-page">
      <p style={{ margin: '0 0 0.5rem' }}>
        <Link to={`/@${handle}`}>← @{handle}</Link>
      </p>
      <h1 style={{ fontSize: '1.25rem', margin: '0 0 1rem', overflowWrap: 'anywhere' }}>
        {title ?? (mode === 'followers' ? '팔로워' : '팔로잉')}
      </h1>
      <nav aria-label="목록 고르기" style={{ display: 'flex', gap: '1rem', margin: '0 0 1rem' }}>
        <Link
          to={`/@${handle}/followers`}
          aria-current={mode === 'followers' ? 'page' : undefined}
          style={{ minHeight: 44, display: 'inline-flex', alignItems: 'center' }}
        >
          팔로워
        </Link>
        <Link
          to={`/@${handle}/following`}
          aria-current={mode === 'following' ? 'page' : undefined}
          style={{ minHeight: 44, display: 'inline-flex', alignItems: 'center' }}
        >
          팔로잉
        </Link>
      </nav>
      <ul className="follow-list">
        {state.items.map((item) => (
          <FollowListItem key={item.handle} item={item} />
        ))}
      </ul>
      {state.status === 'error' && !state.loadedOnce ? (
        <p role="status" className="follow-empty">
          {FOLLOW_MESSAGES.listFailed}{' '}
          <button type="button" onClick={loadMore}>
            다시 시도
          </button>
        </p>
      ) : empty ? (
        <p data-testid="empty-follow-list" className="follow-empty">
          {mode === 'followers' ? FOLLOW_MESSAGES.noFollowers : FOLLOW_MESSAGES.noFollowing}
        </p>
      ) : done ? (
        <p data-testid="all-seen" style={{ textAlign: 'center', color: 'var(--color-text-muted)' }}>
          {ALL_SEEN_TEXT}
        </p>
      ) : state.status === 'error' ? (
        <p role="status" style={{ textAlign: 'center' }}>
          {LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={loadMore}>
            다시 시도
          </button>
        </p>
      ) : state.loadedOnce ? (
        <p style={{ textAlign: 'center' }}>
          <button type="button" onClick={loadMore} disabled={state.status === 'loading'}>
            {state.status === 'loading' ? LOADING_TEXT : '더 보기'}
          </button>
        </p>
      ) : null}
    </main>
  );
}
