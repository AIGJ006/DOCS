import { useCallback, useState } from 'react';
import { Link, Navigate, useNavigationType } from 'react-router-dom';
import { getFeed } from '../api/follows';
import LoadMoreButton, { INITIAL_LOAD_FAILED_TEXT } from '../components/LoadMoreButton';
import PostCard from '../components/PostCard';
import PostCardGrid from '../components/PostCardGrid';
import { loginPathFor } from '../features/auth-gate/authGate';
import { useSession } from '../features/auth/useSession';
import { FOLLOW_MESSAGES } from '../features/follow/followMessages';
import { useCursorList } from '../features/post-list/useCursorList';
import '../features/follow/follow.css';

/** 피드 목록의 복원 저장소 키 (FR-022). */
export const FEED_LIST_KEY = 'feed';
export const FEED_PATH = '/feed';

/**
 * 팔로잉 피드 (010 T029, US2, FR-017~022, research R9).
 *
 * - 로그인 전용: 비로그인이면 `/login?returnTo=/feed`로 보낸다(서버도 401 — 화면만 막지 않는다).
 * - 홈과 같은 카드·9개·[더 보기]·첫 목록 실패 문구, 뒤로 가기(POP)로 돌아오면 30분 안의 보관값으로 복원(`listKey: 'feed'`).
 * - 빈 상태 두 가지: 팔로우한 사람이 없으면 "팔로우한 사람이 없어요…[홈]", 있지만 공개 글이 없으면 "팔로우한 사람의 공개 글이 아직 없어요"
 *   (첫 응답의 `hasFollowing`).
 */
export default function FeedPage() {
  const { loading, me } = useSession();
  if (loading) {
    return <main data-route="feed" aria-busy="true" className="follow-page" />;
  }
  if (!me) {
    return <Navigate to={loginPathFor(FEED_PATH)} replace />;
  }
  return <Feed />;
}

function Feed() {
  const navigationType = useNavigationType();
  const [hasFollowing, setHasFollowing] = useState(true);
  const load = useCallback(async (cursor?: string | null) => {
    const page = await getFeed(cursor);
    if (!cursor) {
      setHasFollowing(page.hasFollowing);
    }
    return page;
  }, []);
  const list = useCursorList(load, { listKey: FEED_LIST_KEY, restore: navigationType === 'POP' });
  const empty = list.loadedOnce && list.items.length === 0;

  return (
    <main data-route="feed" className="follow-page">
      <h1 style={{ fontSize: '1.25rem', margin: '0 0 1rem' }}>피드</h1>
      <PostCardGrid>
        {list.items.map((card) => (
          <PostCard key={card.id} card={card} showAuthor />
        ))}
      </PostCardGrid>
      {list.initialError ? (
        <p role="status" className="follow-empty">
          {INITIAL_LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : empty ? (
        <p data-testid="empty-feed" className="follow-empty">
          {hasFollowing ? (
            FOLLOW_MESSAGES.feedNoPosts
          ) : (
            <>
              {FOLLOW_MESSAGES.feedNoFollowing} <Link to="/">홈</Link>
            </>
          )}
        </p>
      ) : (
        <LoadMoreButton
          status={list.status}
          done={list.done}
          onLoadMore={() => void list.loadMore()}
          onRetry={() => void list.retry()}
        />
      )}
    </main>
  );
}
