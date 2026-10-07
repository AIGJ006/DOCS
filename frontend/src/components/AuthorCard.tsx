import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { PostDetailAuthor } from '../api/types/reading';
import DefaultAvatar from './DefaultAvatar';

/**
 * 본문 아래 작성자 카드 (005 T039, FR-034). 프로필 사진(없으면 기본 아이콘)·닉네임·`@handle`·소개(글자만,
 * 줄바꿈 유지)·[블로그 가기]와 [팔로우] 자리를 둔다. 내 글이면 [팔로우] 자리를 비운다.
 *
 * (구현 메모) [팔로우]는 010 팔로우·피드 기능 소유다 — 그 기능이 `followButton`에 버튼을 넣는다(비회원이 누르면
 * 004 `useAuthGate`로 로그인 안내, 팔로우 중이면 [팔로잉 ✓]). 010 전까지는 자리만 비어 있다.
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
        background: 'var(--card-bg, #f8f9fa)',
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
        <DefaultAvatar size={48} />
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
        <p style={{ display: 'flex', gap: '0.5rem', margin: '0.75rem 0 0' }}>
          <Link to={`/@${author.handle}`}>블로그 가기</Link>
          {isMe ? null : followButton}
        </p>
      </div>
    </aside>
  );
}
