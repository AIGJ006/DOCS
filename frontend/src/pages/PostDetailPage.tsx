import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getPostDetail } from '../api/posts';
import type { PostDetail } from '../api/types/reading';
import AuthorCard from '../components/AuthorCard';
import AuthorChip from '../components/AuthorChip';
import ReactionBar from '../components/ReactionBar';
import RelativeTime from '../components/RelativeTime';
import TagList from '../components/TagList';
import CommentSectionSlot from '../features/post-detail/CommentSectionSlot';
import { playableGifs } from '../features/post-detail/gifPlayer';
import { loadHighlighter } from '../features/post-detail/loadHighlighter';
import { useViewBeacon } from '../features/post-detail/useViewBeacon';
import { formatMonthDay } from '../features/time/dateFormat';
import NotFoundPage from './NotFoundPage';

type LoadStatus = 'loading' | 'ready' | 'not-found' | 'error';

interface DetailState {
  status: LoadStatus;
  detail: PostDetail | null;
}

const LOADING: DetailState = { status: 'loading', detail: null };

/**
 * 글 상세 화면 (005 T042, US2, FR-028~037·040·041).
 *
 * - 본문만 서버가 발행 때 정화한 `contentHtml`을 `dangerouslySetInnerHTML`로 넣는다 — 다른 값에는 절대 쓰지 않는다(원칙 IV).
 * - 응답의 `canonicalPath`가 지금 주소와 다르면 쿼리를 유지해 바꿔 끼운다(FR-027, 주소의 블로그가 작성자와 달라 서버가 301한 뒤 등).
 * - 조회수는 응답 값을 그대로 보여주고(이번 방문의 +1을 기다리지 않음), 기록은 `useViewBeacon`이 따로 보낸다(FR-041).
 *
 * (구현 메모) 라우트는 `/:handle/posts/:postId`다 — react-router는 한 구간의 일부만 파라미터로 받지 못해(`/@:handle` 불가)
 * `@`를 화면에서 떼어 낸다. `@`로 시작하지 않는 주소는 공통 404로 본다.
 */
export default function PostDetailPage() {
  const params = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const rawHandle = params.handle ?? '';
  const postId = params.postId ?? '';
  const blogAddress = rawHandle.startsWith('@');

  const [state, setState] = useState<DetailState>(LOADING);
  const contentRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!blogAddress) {
      return undefined;
    }
    let cancelled = false;
    void (async () => {
      try {
        const detail = await getPostDetail(postId);
        if (!cancelled) {
          setState({ status: 'ready', detail });
        }
      } catch (error) {
        if (!cancelled) {
          const notFound = error instanceof ApiError && error.status === 404;
          setState({ status: notFound ? 'not-found' : 'error', detail: null });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [postId, blogAddress]);

  const detail = state.detail;
  const canonicalPath = detail?.canonicalPath;

  // 주소 맞추기 (쿼리는 그대로 둔다 — `?comment=`는 댓글 영역이 쓴다)
  useEffect(() => {
    if (canonicalPath && canonicalPath !== location.pathname) {
      navigate(canonicalPath + location.search, { replace: true });
    }
  }, [canonicalPath, location.pathname, location.search, navigate]);

  // 본문을 넣은 뒤 코드 강조(코드 블록이 있을 때만)·GIF 재생을 붙인다
  useEffect(() => {
    if (!detail) {
      return;
    }
    if (detail.hasCodeBlock) {
      void loadHighlighter(contentRef.current);
    }
    playableGifs(contentRef.current);
  }, [detail]);

  useViewBeacon({
    postId: detail?.id ?? 0,
    enabled: detail !== null && !detail.viewer.isAuthor,
  });

  if (!blogAddress || state.status === 'not-found') {
    return <NotFoundPage />;
  }
  if (state.status === 'error') {
    return (
      <main data-route="post-detail" style={{ padding: '4rem 1rem', textAlign: 'center' }}>
        <p role="status">글을 불러오지 못했어요</p>
      </main>
    );
  }
  if (detail === null) {
    return <main data-route="post-detail" style={{ padding: '4rem 1rem' }} aria-busy="true" />;
  }

  const aroundCommentId = new URLSearchParams(location.search).get('comment');

  return (
    <main
      data-route="post-detail"
      style={{
        boxSizing: 'border-box',
        width: '100%',
        maxWidth: 'var(--content-max-width, 45rem)',
        margin: '0 auto',
        padding: '1.5rem 1rem',
      }}
    >
      <article>
        <h1 style={{ fontSize: '1.75rem', lineHeight: 1.3, margin: '0 0 0.75rem' }}>
          {detail.title}
        </h1>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.5rem',
            color: 'var(--muted, #868e96)',
            fontSize: '0.875rem',
          }}
        >
          <AuthorChip author={detail.author} withHandle />
          <RelativeTime value={detail.displayedAt} />
          {detail.editedAt ? (
            <span data-testid="edited-at">수정됨 · {formatMonthDay(detail.editedAt)}</span>
          ) : null}
        </div>
        <div
          data-testid="post-content"
          ref={contentRef}
          style={{ marginTop: '1.5rem', overflowWrap: 'anywhere' }}
          // 서버가 발행 때 정화한 HTML만 넣는다 (FR-031·037, 원칙 IV)
          dangerouslySetInnerHTML={{ __html: detail.contentHtml }}
        />
        <TagList tags={detail.tags} />
        <ReactionBar
          likeCount={detail.likeCount}
          viewCount={detail.viewCount}
          likedByMe={detail.viewer.likedByMe}
        />
      </article>
      <AuthorCard author={detail.author} isMe={detail.viewer.isAuthor} />
      <CommentSectionSlot
        postId={detail.id}
        commentCount={detail.commentCount}
        aroundCommentId={aroundCommentId}
      />
    </main>
  );
}
