import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { TagCount } from '../api/types/tags';
import { tagQueryValue } from '../features/tag/tagPath';
import '../features/tag/tag.css';

export interface BlogTagStripProps {
  handle: string;
  /** 글 수 많은 순 (서버 순서 그대로) */
  items: TagCount[];
  /** 처음 보일 개수 (`initialVisible`) */
  initialVisible: number;
  /** 지금 필터 중인 태그 */
  active?: string | null;
}

/**
 * 블로그 태그 줄 (008 T061, US5 #1). 처음 `initialVisible`개를 보이고 [태그 더 보기 +남은 수]로 나머지를 펼친다. 태그가 없으면
 * 줄을 그리지 않는다. 링크는 `/@{handle}?tag={값}`(쿼리 값 인코딩 — `+`는 `%2B`).
 */
export default function BlogTagStrip({ handle, items, initialVisible, active }: BlogTagStripProps) {
  const [expanded, setExpanded] = useState(false);
  if (items.length === 0) {
    return null;
  }
  const visible = expanded ? items : items.slice(0, initialVisible);
  const rest = items.length - visible.length;

  return (
    <nav className="blog-tag-strip" aria-label="블로그 태그 줄">
      <ul aria-label="블로그 태그">
        {visible.map((tag) => (
          <li key={tag.name} style={{ minWidth: 0, maxWidth: '100%' }}>
            <Link
              className="tag-link"
              to={`/@${handle}?tag=${tagQueryValue(tag.name)}`}
              aria-current={tag.name === active ? 'true' : undefined}
            >
              #{tag.name}{' '}
              <span className="tag-index__count">{tag.postCount.toLocaleString('ko-KR')}</span>
            </Link>
          </li>
        ))}
      </ul>
      {rest > 0 ? (
        <button type="button" onClick={() => setExpanded(true)}>
          태그 더 보기 +{rest}
        </button>
      ) : null}
    </nav>
  );
}
