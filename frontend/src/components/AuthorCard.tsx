import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { PostDetailAuthor } from '../api/types/reading';
import DefaultAvatar from './DefaultAvatar';

/**
 * 본문 아래 작성자 카드 (005 T039, FR-034). 프로필 사진(없으면 기본 아이콘)·닉네임·`@handle`·소개(글자만,
 * 줄바꿈 유지)·[블로그 가기]와 [팔로우] 자리를 둔다. 내 글이면 [팔로우] 자리를 비운다.
 *
 * [팔로우]는 `followButton`으로 받는다 — 글 상세가 `features/follow/FollowButton`을 넣는다(비회원이 누르면 004 로그인
 * 안내, 팔로우 중이면 [팔로잉 ✓]).
 */
export interface AuthorCardProps {
  author: PostDetailAuthor;
  /** 내 글인가 — true면 [팔로우] 자리를 만들지 않는다 */
  isMe: boolean;
  followButton?: ReactNode;
}

export default function AuthorCard({ author, isMe, followButton = null }: AuthorCardProps) {
  return (
    <aside
      data-testid="author-card"
      style={{
        display: 'flex',
        gap: '0.75rem',
        alignItems: 'flex-start',
        margin: '2rem 0 0',
        padding: '1rem',
        borderRadius: '0.5rem',
        border: '1px solid var(--color-border)',
        background: 'var(--color-surface)',
      }}
    >
      {author.profileImageUrl ? (
        <img
          src={author.profileImageUrl}
          data-testid="author-card-avatar"
          alt=""
          width={48}
          height={48}
          loading="lazy"
          style={{
            width: 48,
            height: 48,
            borderRadius: '50%',
            objectFit: 'cover',
            flex: '0 0 48px',
          }}
        />
      ) : (
        <DefaultAvatar nickname={author.nickname} handle={author.handle} size={48} />
      )}
      <div style={{ minWidth: 0, flex: 1 }}>
        <p style={{ margin: 0, fontWeight: 600 }}>
          {author.nickname} <span style={{ opacity: 0.7, fontWeight: 400 }}>@{author.handle}</span>
        </p>
        {author.bio ? (
          <p
            data-testid="author-bio"
            style={{ whiteSpace: 'pre-line', margin: '0.5rem 0 0', overflowWrap: 'anywhere' }}
          >
            {author.bio}
          </p>
        ) : null}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: '0.5rem',
            margin: '0.75rem 0 0',
          }}
        >
          <Link to={`/@${author.handle}`}>블로그 가기</Link>
          {isMe ? null : followButton}
        </div>
      </div>
    </aside>
  );
}
