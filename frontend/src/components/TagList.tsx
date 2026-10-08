import { Link } from 'react-router-dom';
import { tagPath } from '../features/tag/tagPath';
import '../features/tag/tag.css';

/**
 * 글의 태그 (005 T039, FR-032). 입력한 순서 그대로 `#이름`으로 보여주고 그 태그의 글 목록으로 연결한다.
 * 주소는 008 `tagPath`(서버 301 대상과 같은 모양 — `c#` → `/tags/c%23`, `c++` → `/tags/c++`).
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
          <Link data-testid="tag" className="tag-link" to={tagPath(tag)}>
            #{tag}
          </Link>
        </li>
      ))}
    </ul>
  );
}
