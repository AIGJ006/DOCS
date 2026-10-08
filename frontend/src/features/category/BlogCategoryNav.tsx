import { Link } from 'react-router-dom';
import type { BlogCategories, CategoryNode } from '../../api/categories';
import './category.css';

export interface BlogCategoryNavProps {
  handle: string;
  categories: BlogCategories;
  /** 지금 고른 카테고리 번호 (전체면 null) */
  active: number | null;
  /** 좁은 화면이면 접힌 버튼으로 */
  collapsed: boolean;
}

function countText(n: number) {
  return `(${n.toLocaleString('ko-KR')})`;
}

/**
 * 블로그 카테고리 목록 (017 US3, FR-028·FR-033). "분류 전체보기"와 관리 화면 순서의 나무, 각 노출 글 수. 고른 항목은
 * `aria-current="page"`. 좁은 화면(`collapsed`)에서는 `<details>`로 접어 글 목록 위에 둔다.
 */
export default function BlogCategoryNav({
  handle,
  categories,
  active,
  collapsed,
}: BlogCategoryNavProps) {
  const link = (node: CategoryNode) => (
    <Link
      to={`/@${handle}?category=${node.id}`}
      aria-current={active === node.id ? 'page' : undefined}
    >
      {node.name}
      <span className="blog-category-nav__count">{countText(node.postCount)}</span>
    </Link>
  );
  const list = (
    <ul>
      <li>
        <Link to={`/@${handle}`} aria-current={active === null ? 'page' : undefined}>
          분류 전체보기
          <span className="blog-category-nav__count">{countText(categories.totalCount)}</span>
        </Link>
      </li>
      {categories.items.map((top) => (
        <li key={top.id}>
          {link(top)}
          {top.children.length > 0 ? (
            <ul>
              {top.children.map((child) => (
                <li key={child.id}>{link(child)}</li>
              ))}
            </ul>
          ) : null}
        </li>
      ))}
    </ul>
  );
  return (
    <nav className="blog-category-nav" aria-label="카테고리">
      {collapsed ? (
        <details>
          <summary>카테고리</summary>
          {list}
        </details>
      ) : (
        <>
          <h2 style={{ fontSize: '1rem', margin: '0 0 0.5rem' }}>카테고리</h2>
          {list}
        </>
      )}
    </nav>
  );
}
