import { useCallback } from 'react';
import { listHomePosts } from '../api/posts';
import LoadMoreButton from '../components/LoadMoreButton';
import PostCard from '../components/PostCard';
import PostCardGrid from '../components/PostCardGrid';
import { useCursorList } from '../features/post-list/useCursorList';

/**
 * 홈 — 전체 공개 글 목록 (005 T024, US1, FR-001~004). 카드 9개 + [더 보기]로 이어 본다.
 * 빈 목록·로딩·실패 문구는 US6(T068~T071)에서 다듬는다.
 */
export default function HomePage() {
  const load = useCallback((cursor?: string | null) => listHomePosts(cursor), []);
  const list = useCursorList(load);

  return (
    <main
      data-route="home"
      style={{
        boxSizing: 'border-box',
        width: '100%',
        maxWidth: 'var(--page-max-width, 72rem)',
        margin: '0 auto',
        padding: '1.5rem 1rem',
      }}
    >
      <h1 style={{ fontSize: '1.25rem', margin: '0 0 1rem' }}>최신 글</h1>
      <PostCardGrid>
        {list.items.map((card) => (
          <PostCard key={card.id} card={card} showAuthor />
        ))}
      </PostCardGrid>
      {list.loadedOnce && list.items.length === 0 ? null : (
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
