import { Link } from 'react-router-dom';
import type { PostCard as PostCardData } from '../api/types/reading';
import AuthorChip from './AuthorChip';
import RelativeTime from './RelativeTime';

/**
 * 글 카드 (005 FR-007~015, 10 §2). 정보 구성은 공통이고 색·글꼴·비율은 CSS 변수로 둔다(research R-09).
 * 제목·요약은 React 텍스트 노드로 넣어 HTML로 해석되지 않는다(원칙 IV).
 */
export interface PostCardProps {
  card: PostCardData;
  /** 홈은 true, 개인 블로그는 false (작성자 영역 생략) */
  showAuthor?: boolean;
}

const CLAMP = {
  display: '-webkit-box',
  WebkitBoxOrient: 'vertical' as const,
  overflow: 'hidden',
};

export default function PostCard({ card, showAuthor = true }: PostCardProps) {
  return (
    <article
      data-testid="post-card"
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        boxSizing: 'border-box',
        minWidth: 0,
        border: '1px solid var(--card-border, #e9ecef)',
        borderRadius: 'var(--card-radius, 0.5rem)',
        background: 'var(--card-bg, #fff)',
        overflow: 'hidden',
      }}
    >
      <Link
        to={card.url}
        style={{ color: 'inherit', textDecoration: 'none', display: 'block', minWidth: 0 }}
      >
        <span
          data-testid="card-thumb"
          data-empty={card.thumbnailUrl === null ? 'true' : 'false'}
          style={{
            display: 'block',
            width: '100%',
            aspectRatio: 'var(--card-thumb-ratio, 16 / 9)',
            background: 'var(--card-thumb-empty-bg, #f1f3f5)',
          }}
        >
          {card.thumbnailUrl ? (
            <img
              src={card.thumbnailUrl}
              alt={card.title}
              loading="lazy"
              style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
            />
          ) : null}
        </span>
        <h3
          data-testid="card-title"
          title={card.title}
          style={{
            ...CLAMP,
            WebkitLineClamp: 1,
            margin: '0.75rem 0.75rem 0.25rem',
            fontSize: 'var(--card-title-size, 1rem)',
            lineHeight: 1.4,
          }}
        >
          {card.title}
        </h3>
        <p
          data-testid="card-excerpt"
          style={{
            ...CLAMP,
            WebkitLineClamp: 3,
            minHeight: 'calc(1.5em * 3)',
            margin: '0 0.75rem 0.5rem',
            fontSize: 'var(--card-excerpt-size, 0.875rem)',
            lineHeight: 1.5,
            color: 'var(--card-excerpt-color, #495057)',
          }}
        >
          {card.excerpt ?? ''}
        </p>
      </Link>
      <div
        style={{
          marginTop: 'auto',
          padding: '0 0.75rem 0.75rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
          flexWrap: 'wrap',
          fontSize: 'var(--card-meta-size, 0.8125rem)',
          color: 'var(--card-meta-color, #868e96)',
          minWidth: 0,
        }}
      >
        {showAuthor ? <AuthorChip author={card.author} /> : null}
        <RelativeTime value={card.firstPublicAt} />
        <span data-testid="card-comments" title="댓글 수">
          💬 {card.commentCount}
        </span>
        <span data-testid="card-likes" title="좋아요 수">
          ♥ {card.likeCount}
        </span>
      </div>
    </article>
  );
}
