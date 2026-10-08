import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { listComments } from '../api/comments';
import { getPostDetail } from '../api/posts';
import type { CommentPage } from '../api/types/comments';
import type { PostDetail } from '../api/types/reading';
import AuthorCard from '../components/AuthorCard';
import FollowButton from '../features/follow/FollowButton';
import AuthorChip from '../components/AuthorChip';
import LikeButton from '../components/LikeButton';
import ReactionBar from '../components/ReactionBar';
import ReportButton from '../features/moderation/ReportButton';
import RelativeTime from '../components/RelativeTime';
import TagList from '../components/TagList';
import AuthorActions, { type AuthorActionSlot } from '../features/post-detail/AuthorActions';
import AuthorStatusBanner from '../features/post-detail/AuthorStatusBanner';
import CommentSectionSlot from '../features/post-detail/CommentSectionSlot';
import { playableGifs } from '../features/post-detail/gifPlayer';
import { loadHighlighter } from '../features/post-detail/loadHighlighter';
import { useViewBeacon } from '../features/post-detail/useViewBeacon';
import { formatMonthDay } from '../features/time/dateFormat';
import NotFoundPage from './NotFoundPage';
import '../features/post-detail/postDetail.css';
import CategoryPath from '../features/category/CategoryPath';

type LoadStatus = 'loading' | 'ready' | 'not-found' | 'error' | 'redirecting';

interface DetailState {
  status: LoadStatus;
  detail: PostDetail | null;
  /** 상세 요청과 동시에 시작한 댓글 첫 페이지 요청 (007 Clarifications Q1) */
  comments?: Promise<CommentPage> | null;
}

const LOADING: DetailState = { status: 'loading', detail: null };

/** 볼 수 없는 글의 문서 제목 — 서버 첫 응답의 공통 문구(06 §3-1)와 같다. */
export const UNAVAILABLE_TITLE = '볼 수 없는 글이에요';

/**
 * 글 상세 화면 (005 T042, US2, FR-028~037·040·041).
 *
 * - 본문만 서버가 발행 때 정화한 `contentHtml`을 `dangerouslySetInnerHTML`로 넣는다 — 다른 값에는 절대 쓰지 않는다(원칙 IV).
 * - 응답의 `canonicalPath`가 지금 주소와 다르면 쿼리·#조각을 유지해 바꿔 끼운다(FR-027 — 화면 안 링크로 다른 블로그 주소를 연 경우.
 *   첫 응답은 서버가 301로 처리한다). 문서 제목은 `{제목} - {닉네임}`, 볼 수 없는 글은 "볼 수 없는 글이에요"(T065).
 * - 조회수는 응답 값을 그대로 보여주고(이번 방문의 +1을 기다리지 않음), 기록은 `useViewBeacon`이 따로 보낸다(FR-041).
 * - 작성자 카드의 [팔로우]는 010 `FollowButton`이 `AuthorCard`의 `followButton` 자리를 채운다(처음 상태 `viewer.followingAuthor`).
 * - 좋아요는 009 `LikeButton`이 `ReactionBar`의 `likeButton` 자리를 채운다(처음 상태 `viewer.likedByMe`·`likeCount`).
 *   작성자 본인에게는 버튼 없이 ♥ + 수.
 *
 * - 작성자 본인(005 T059, US4): 임시글 응답(`status: DRAFT`)이면 `editorPath`로 바꿔 끼우고, 상태 안내
 *   (`AuthorStatusBanner`)와 [수정]·[공개 범위 ▾]·[삭제] 줄(`AuthorActions`)을 보이며 [좋아요]·[신고]·[팔로우]와 조회 기록은 넣지
 *   않는다.
 *
 * (구현 메모) 라우트는 `/:handle/posts/:postId`다 — react-router는 한 구간의 일부만 파라미터로 받지 못해(`/@:handle` 불가)
 * `@`를 화면에서 떼어 낸다. `@`로 시작하지 않는 주소는 공통 404로 본다.
 */
export interface PostDetailPageProps {
  /** [공개 범위 ▾] 자리 — 004 `VisibilitySelect`가 채운다 */
  visibilityControl?: AuthorActionSlot;
  /** [삭제] 자리 — 006 휴지통 확인 창이 채운다 */
  deleteControl?: AuthorActionSlot;
}

export default function PostDetailPage({
  visibilityControl,
  deleteControl,
}: PostDetailPageProps = {}) {
  const params = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const rawHandle = params.handle ?? '';
  const postId = params.postId ?? '';
  const blogAddress = rawHandle.startsWith('@');

  const commentAround = new URLSearchParams(location.search).get('comment');
  const [state, setState] = useState<DetailState>(LOADING);
  const [reloadKey, setReloadKey] = useState(0);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    if (!blogAddress) {
      return undefined;
    }
    let cancelled = false;
    // 상세와 댓글 첫 페이지를 동시에 부른다(007 FR-016). 댓글 실패는 댓글 영역만 알린다 — 여기서 처리됨으로 표시해 둔다.
    const detailRequest = getPostDetail(postId);
    const comments = listComments(postId, { around: commentAround });
    comments.catch(() => undefined);
    void (async () => {
      try {
        const response = await detailRequest;
        if (cancelled) {
          return;
        }
        if (response.status === 'DRAFT') {
          // ⑤ 작성자 본인의 임시글 → 에디터 (US4 #2)
          setState({ status: 'redirecting', detail: null });
          navigate(response.editorPath, { replace: true });
          return;
        }
        setState({ status: 'ready', detail: response, comments });
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
  }, [postId, blogAddress, reloadKey, navigate, commentAround]);

  const detail = state.detail;
  const canonicalPath = detail?.canonicalPath;

  // 주소 맞추기 (쿼리·#조각은 그대로 둔다 — `?comment=`·`#comment-{id}`는 댓글 영역이 쓴다)
  useEffect(() => {
    if (canonicalPath && canonicalPath !== location.pathname) {
      navigate(canonicalPath + location.search + location.hash, { replace: true });
    }
  }, [canonicalPath, location.pathname, location.search, location.hash, navigate]);

  // 문서 제목 (005 T065, research R-22·R-25). 서버 메타는 첫 응답에만 쓰이므로 화면 안 이동은 제목만 바꾼다.
  const documentTitle =
    detail !== null
      ? `${detail.title} - ${detail.author.nickname}`
      : !blogAddress || state.status === 'not-found'
        ? UNAVAILABLE_TITLE
        : null;
  useEffect(() => {
    if (documentTitle !== null) {
      document.title = documentTitle;
    }
  }, [documentTitle]);

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

  const isAuthor = detail.viewer.isAuthor;

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
      {isAuthor ? (
        <>
          {detail.authorView ? (
            <AuthorStatusBanner
              postId={detail.id}
              visibility={detail.visibility}
              authorView={detail.authorView}
              onDiscarded={reload}
            />
          ) : null}
          <AuthorActions
            postId={detail.id}
            visibility={detail.visibility}
            reload={reload}
            visibilityControl={visibilityControl}
            deleteControl={deleteControl}
          />
        </>
      ) : null}
      <article>
        {/* 017 제목 위 카테고리 경로 */}
        <CategoryPath handle={detail.author.handle} category={detail.category} />
        <h1 style={{ fontSize: '1.75rem', lineHeight: 1.3, margin: '0 0 0.75rem' }}>
          {detail.title}
        </h1>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.5rem',
            color: 'var(--color-text-muted)',
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
          className="post-detail-content"
          ref={contentRef}
          style={{ marginTop: '1.5rem' }}
          // 서버가 발행 때 정화한 HTML만 넣는다 (FR-031·037, 원칙 IV)
          dangerouslySetInnerHTML={{ __html: detail.contentHtml }}
        />
        <TagList tags={detail.tags} />
        <ReactionBar
          likeCount={detail.likeCount}
          viewCount={detail.viewCount}
          likedByMe={detail.viewer.likedByMe}
          likeButton={
            <LikeButton
              key={detail.id}
              postId={detail.id}
              viewer={detail.viewer}
              initialLiked={detail.viewer.likedByMe}
              initialCount={detail.likeCount}
            />
          }
          reportButton={
            // 014: [신고] — 작성자에게는 없다(004 PostActions 표시 규칙)
            isAuthor ? null : (
              <ReportButton targetType="POST" targetId={detail.id} viewer={detail.viewer} />
            )
          }
        />
      </article>
      <AuthorCard
        author={detail.author}
        isMe={isAuthor}
        followButton={
          // 010: 작성자 카드의 [팔로우] — 처음 상태는 상세의 viewer.followingAuthor
          <FollowButton
            key={`${detail.id}-${detail.author.handle}`}
            handle={detail.author.handle}
            initialFollowing={detail.viewer.followingAuthor}
            isMe={isAuthor}
            loggedIn={detail.viewer.loggedIn}
          />
        }
      />
      <CommentSectionSlot
        key={`${detail.id}-${reloadKey}`}
        postId={detail.id}
        commentCount={detail.commentCount}
        viewer={detail.viewer}
        aroundCommentId={commentAround}
        initialPage={state.comments}
      />
    </main>
  );
}
