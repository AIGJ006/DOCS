import { useCallback } from 'react';
import { Link, useNavigationType, useSearchParams } from 'react-router-dom';
import { listHomePosts } from '../api/posts';
import LoadMoreButton, { INITIAL_LOAD_FAILED_TEXT } from '../components/LoadMoreButton';
import PostCard from '../components/PostCard';
import PostCardGrid from '../components/PostCardGrid';
import { useSession } from '../features/auth/useSession';
import { useCursorList } from '../features/post-list/useCursorList';
import TrendingList from '../features/trending/TrendingList';
import '../features/search/search.css';

/** 공개 글이 하나도 없을 때 (FR-017). */
export const EMPTY_HOME_TEXT = '아직 올라온 글이 없어요. 첫 글의 주인공이 되어 보세요';
export { INITIAL_LOAD_FAILED_TEXT };

/** 홈 목록의 복원 저장소 키 (FR-018). */
export const HOME_LIST_KEY = 'home';

export type HomeTab = 'latest' | 'trending';

const TABS: { value: HomeTab; label: string }[] = [
  { value: 'latest', label: '최신' },
  { value: 'trending', label: '트렌딩' },
];

/**
 * 홈 — 전체 공개 글 목록 (005 T024·T071, US1·US6, FR-001~004·016~018). 카드 9개 + [더 보기]로 이어 본다.
 *
 * - 뒤로 가기(POP)로 돌아오면 30분 안에 보관한 카드·스크롤 위치를 요청 없이 복원한다. 링크로 새로 들어오면 처음부터.
 * - 첫 목록 실패: "글을 불러오지 못했어요 [다시 시도]". [더 보기] 실패·로딩 문구는 `LoadMoreButton`.
 * - 글이 하나도 없으면 빈 홈 문구 + [글쓰기](로그인 회원 → 002 새 글) / [로그인](비회원 → `/login?returnTo=/`).
 * - 012: 위에 `[최신] [트렌딩]` 탭(`role="tablist"`, 기본 최신, 트렌딩은 `/?tab=trending`). 탭마다 복원 키가 따로다
 *   (`home`·`trending`).
 */
export default function HomePage() {
  const [params, setParams] = useSearchParams();
  const tab: HomeTab = params.get('tab') === 'trending' ? 'trending' : 'latest';

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
      <h1 style={{ fontSize: '1.25rem', margin: '0 0 0.75rem' }}>
        {tab === 'trending' ? '트렌딩 글' : '최신 글'}
      </h1>
      <div role="tablist" aria-label="홈 목록" className="tab-row">
        {TABS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            id={`home-tab-${option.value}`}
            aria-selected={tab === option.value}
            aria-controls="home-tabpanel"
            onClick={() => {
              if (tab !== option.value) {
                setParams(option.value === 'latest' ? {} : { tab: option.value });
              }
            }}
          >
            {option.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id="home-tabpanel" aria-labelledby={`home-tab-${tab}`}>
        {tab === 'trending' ? <TrendingList /> : <LatestPosts />}
      </div>
    </main>
  );
}

/** 최신 탭 (005 홈 목록 그대로). */
function LatestPosts() {
  const navigationType = useNavigationType();
  const { loading: sessionLoading, me } = useSession();
  const load = useCallback((cursor?: string | null) => listHomePosts(cursor), []);
  const list = useCursorList(load, { listKey: HOME_LIST_KEY, restore: navigationType === 'POP' });

  const empty = list.loadedOnce && list.items.length === 0;

  return (
    <>
      <PostCardGrid>
        {list.items.map((card) => (
          <PostCard key={card.id} card={card} showAuthor />
        ))}
      </PostCardGrid>
      {list.initialError ? (
        <p role="status" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          {INITIAL_LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : empty ? (
        <p data-testid="empty-home" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          {EMPTY_HOME_TEXT}{' '}
          {sessionLoading ? null : me ? (
            <Link to="/write/new">글쓰기</Link>
          ) : (
            <Link to="/login?returnTo=/">로그인</Link>
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
    </>
  );
}
