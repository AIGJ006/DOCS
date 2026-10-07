import { Link } from 'react-router-dom';
import type { AuthorSummary } from '../api/types/reading';
import DefaultAvatar from './DefaultAvatar';

/**
 * 작은 작성자 영역: 프로필 사진(없으면 기본 아이콘) + 닉네임, 누르면 그 블로그로 (005 FR-007·014·029).
 * 상세 화면은 `withHandle`로 `닉네임 @handle`을 보여준다.
 */
export interface AuthorChipProps {
  author: AuthorSummary;
  /** `닉네임 @handle` 형태로 보여줄지 (상세 화면) */
  withHandle?: boolean;
  size?: number;
}

export default function AuthorChip({ author, withHandle = false, size = 24 }: AuthorChipProps) {
  return (
    <span
      data-testid="author-chip"
      style={{ display: 'inline-flex', alignItems: 'center', gap: '0.375rem', minWidth: 0 }}
    >
      {author.profileImageUrl ? (
        <img
          src={author.profileImageUrl}
          data-testid="author-avatar"
          alt=""
          width={size}
          height={size}
          loading="lazy"
          style={{
            width: size,
            height: size,
            borderRadius: '50%',
            objectFit: 'cover',
            flex: `0 0 ${size}px`,
          }}
        />
      ) : (
        <DefaultAvatar size={size} />
      )}
      <Link
        to={`/@${author.handle}`}
        style={{
          color: 'inherit',
          textDecoration: 'none',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {author.nickname}
        {withHandle ? <span style={{ opacity: 0.7 }}> @{author.handle}</span> : null}
      </Link>
    </span>
  );
}
