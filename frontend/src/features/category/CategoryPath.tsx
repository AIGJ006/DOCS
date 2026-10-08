import { Link } from 'react-router-dom';
import type { PostCategoryPath } from '../../api/categories';
import './category.css';

/** 글 상세 제목 위 카테고리 경로 "개발 › Spring" (017 US4, FR-041). 분류 없음이면 그리지 않는다. */
export default function CategoryPath({
  handle,
  category,
}: {
  handle: string;
  category: PostCategoryPath | null | undefined;
}) {
  if (!category) {
    return null;
  }
  return (
    <nav className="post-category-path" aria-label="카테고리">
      {category.parent ? (
        <>
          <Link to={`/@${handle}?category=${category.parent.id}`}>{category.parent.name}</Link>
          {' › '}
        </>
      ) : null}
      <Link to={`/@${handle}?category=${category.id}`}>{category.name}</Link>
    </nav>
  );
}
