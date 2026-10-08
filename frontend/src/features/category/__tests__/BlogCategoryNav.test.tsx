import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { BlogCategories } from '../../../api/categories';
import BlogCategoryNav from '../BlogCategoryNav';
import { findBlogCategory } from '../categoryTree';
import CategoryPath from '../CategoryPath';

const DATA: BlogCategories = {
  totalCount: 12,
  items: [
    {
      id: 1,
      name: '개발',
      postCount: 4,
      children: [{ id: 3, name: 'Spring', postCount: 2, children: [] }],
    },
    { id: 2, name: '<b>일상</b>', postCount: 0, children: [] },
  ],
};

/** 블로그 카테고리 목록·글 상세 경로 (017 T036·T043, US3 #1·#3, US4). */
describe('BlogCategoryNav', () => {
  it('분류 전체보기와 나무, 글 수, 현재 항목', () => {
    render(
      <MemoryRouter>
        <BlogCategoryNav handle="kim" categories={DATA} active={3} collapsed={false} />
      </MemoryRouter>,
    );
    const nav = screen.getByRole('navigation', { name: '카테고리' });
    const links = within(nav).getAllByRole('link');
    expect(links.map((a) => a.textContent)).toEqual([
      '분류 전체보기(12)',
      '개발(4)',
      'Spring(2)',
      '<b>일상</b>(0)',
    ]);
    expect(links[0]).toHaveAttribute('href', '/@kim');
    expect(links[2]).toHaveAttribute('href', '/@kim?category=3');
    expect(links[2]).toHaveAttribute('aria-current', 'page');
    expect(links[0]).not.toHaveAttribute('aria-current');
  });

  it('좁은 화면에서는 접힌 버튼', () => {
    render(
      <MemoryRouter>
        <BlogCategoryNav handle="kim" categories={DATA} active={null} collapsed />
      </MemoryRouter>,
    );
    expect(screen.getByText('카테고리', { selector: 'summary' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '분류 전체보기(12)' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('번호로 찾기', () => {
    expect(findBlogCategory(DATA, 3)?.name).toBe('Spring');
    expect(findBlogCategory(DATA, 9)).toBeNull();
  });
});

describe('CategoryPath', () => {
  it('상위 › 자기, 분류 없음이면 없음', () => {
    const { rerender } = render(
      <MemoryRouter>
        <CategoryPath
          handle="kim"
          category={{ id: 3, name: 'Spring', parent: { id: 1, name: '개발' } }}
        />
      </MemoryRouter>,
    );
    const nav = screen.getByRole('navigation', { name: '카테고리' });
    expect(nav).toHaveTextContent('개발 › Spring');
    expect(within(nav).getByRole('link', { name: '개발' })).toHaveAttribute(
      'href',
      '/@kim?category=1',
    );

    rerender(
      <MemoryRouter>
        <CategoryPath handle="kim" category={null} />
      </MemoryRouter>,
    );
    expect(screen.queryByRole('navigation', { name: '카테고리' })).toBeNull();
  });
});
