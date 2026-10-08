import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigationType, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getBlogHeader, listBlogPosts } from '../api/members';
import type { BlogHeader } from '../api/types/reading';
import DefaultAvatar from '../components/DefaultAvatar';
import LoadMoreButton, { INITIAL_LOAD_FAILED_TEXT } from '../components/LoadMoreButton';
import PostCard from '../components/PostCard';
import PostCardGrid from '../components/PostCardGrid';
import { useCursorList } from '../features/post-list/useCursorList';
import NotFoundPage from './NotFoundPage';

/** 글이 없을 때 (FR-023). */
export const EMPTY_BLOG_TEXT = '아직 공개한 글이 없어요';
export const EMPTY_MY_BLOG_TEXT = '첫 글을 써 보세요';

type HeaderStatus = 'loading' | 'ready' | 'not-found' | 'error';

interface HeaderState {
  status: HeaderStatus;
  header: BlogHeader | null;
}

const LOADING: HeaderState = { status: 'loading', header: null };

/**
 * 개인 블로그 화면 (005 T051, US3, FR-019~023).
 *
 * 머리말과 글 목록을 동시에 부르고, 카드는 홈과 같은 부품을 작성자 영역만 빼고 쓴다(`showAuthor=false`).
 * 첫 목록 실패 문구와 뒤로 가기 복원(`blog:{handle}`)은 홈과 같다(005 T071).
 *
 * (구현 메모) 라우트는 `/:handle`이고 `@`는 화면이 떼어 낸다 — react-router 7은 한 구간의 일부만 파라미터로 받지 못한다.
 * 001 `FriendButton`(T132)·`LastActiveBadge`(T142)와 개인 확장(카테고리·시리즈)은 아직 없어 `headerSlot`·
 * `sidebarSlot` prop 자리만 둔다.
 */
export interface BlogPageProps {
  /** 001 친구 버튼·마지막 활동 배지 자리 (001 US6~US8) */
  headerSlot?: ReactNode;
  /** 개인 확장(카테고리 사이드바·시리즈 탭) 자리 (spec Assumptions) */
  sidebarSlot?: ReactNode;
}

export default function BlogPage({ headerSlot = null, sidebarSlot = null }: BlogPageProps) {
  const params = useParams();
  const rawHandle = params.handle ?? '';
  const blogAddress = rawHandle.startsWith('@');
  const handle = blogAddress ? rawHandle.slice(1) : '';

  const [state, setState] = useState<HeaderState>(LOADING);

  useEffect(() => {
    if (!blogAddress) {
      return undefined;
    }
    let cancelled = false;
    void (async () => {
      try {
        const header = await getBlogHeader(handle);
        if (!cancelled) {
          setState({ status: 'ready', header });
        }
      } catch (error) {
        if (!cancelled) {
          const notFound = error instanceof ApiError && error.status === 404;
          setState({ status: notFound ? 'not-found' : 'error', header: null });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [handle, blogAddress]);

  const navigationType = useNavigationType();
  const load = useCallback((cursor?: string | null) => listBlogPosts(handle, cursor), [handle]);
  // 뒤로 가기로 돌아오면 30분 안의 보관값으로 복원한다 (005 T071, FR-018)
  const list = useCursorList(load, {
    listKey: `blog:${handle}`,
    restore: navigationType === 'POP',
  });

  if (!blogAddress || state.status === 'not-found') {
    return <NotFoundPage />;
  }
  const header = state.header;

  return (
    <main
      data-route="blog"
      style={{
        boxSizing: 'border-box',
        width: '100%',
        maxWidth: 'var(--page-max-width, 72rem)',
        margin: '0 auto',
        padding: '1.5rem 1rem',
      }}
    >
      {header === null ? (
        <div aria-busy="true" style={{ minHeight: '4rem' }} />
      ) : (
        <header
          style={{
            display: 'flex',
            gap: '1rem',
            alignItems: 'flex-start',
            margin: '0 0 1.5rem',
          }}
        >
          {header.profileImageUrl ? (
            <img
              src={header.profileImageUrl}
              data-testid="blog-avatar"
              alt=""
              width={64}
              height={64}
              style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                objectFit: 'cover',
                flex: '0 0 64px',
              }}
            />
          ) : (
            <DefaultAvatar size={64} />
          )}
          <div style={{ minWidth: 0, flex: 1 }}>
            <h1 style={{ fontSize: '1.25rem', margin: 0 }}>{header.nickname}</h1>
            <p style={{ margin: '0.125rem 0 0', color: 'var(--muted, #868e96)' }}>
              @{header.handle}
            </p>
            {header.bio ? (
              <p
                data-testid="blog-bio"
                style={{ whiteSpace: 'pre-line', margin: '0.5rem 0 0', overflowWrap: 'anywhere' }}
              >
                {header.bio}
              </p>
            ) : null}
            <p style={{ margin: '0.5rem 0 0', color: 'var(--muted, #868e96)' }}>
              공개 글 {header.publicPostCount.toLocaleString('ko-KR')}
            </p>
            {headerSlot}
          </div>
        </header>
      )}

      {sidebarSlot}

      <PostCardGrid>
        {list.items.map((card) => (
          <PostCard key={card.id} card={card} showAuthor={false} />
        ))}
      </PostCardGrid>

      {list.initialError ? (
        <p role="status" style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          {INITIAL_LOAD_FAILED_TEXT}{' '}
          <button type="button" onClick={() => void list.retry()}>
            다시 시도
          </button>
        </p>
      ) : list.loadedOnce && list.items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          {header?.isMe ? (
            <p style={{ margin: 0 }}>
              {EMPTY_MY_BLOG_TEXT} <Link to="/write/new">글쓰기</Link>
            </p>
          ) : (
            <p style={{ margin: 0 }}>{EMPTY_BLOG_TEXT}</p>
          )}
        </div>
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
