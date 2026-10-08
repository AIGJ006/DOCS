import { Link } from 'react-router-dom';

/**
 * 글의 태그 (005 T039, FR-032). 입력한 순서 그대로 `#이름`으로 보여주고 그 태그의 글 목록으로 연결한다.
 *
 * (구현 메모) `/tags/{이름}` 화면은 008 태그 기능 소유다 — 주소 규칙만 지금 맞춰 둔다.
 */
export interface TagListProps {
  tags: string[];
}

export default function TagList({ tags }: TagListProps) {
  if (tags.length === 0) {
    return null;
  }
  return (
    <ul
      data-testid="tag-list"
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: '0.5rem',
        listStyle: 'none',
        margin: '1.5rem 0 0',
        padding: 0,
      }}
    >
      {tags.map((tag) => (
        <li key={tag}>
          <Link
            data-testid="tag"
            to={`/tags/${encodeURIComponent(tag)}`}
            style={{
              display: 'inline-block',
              padding: '0.125rem 0.5rem',
              borderRadius: '999px',
              background: 'var(--tag-bg, #f1f3f5)',
              color: 'inherit',
              textDecoration: 'none',
              fontSize: '0.875rem',
            }}
          >
            #{tag}
          </Link>
        </li>
      ))}
    </ul>
  );
}
