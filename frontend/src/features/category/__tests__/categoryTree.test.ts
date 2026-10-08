import { describe, expect, it } from 'vitest';
import type { CategoryNode } from '../../../api/categories';
import { countCategories, findCategory, flattenCategories, moveSibling } from '../categoryTree';

const tree: CategoryNode[] = [
  {
    id: 1,
    name: '개발',
    postCount: 3,
    children: [
      { id: 3, name: 'Spring', postCount: 2, children: [] },
      { id: 4, name: 'React', postCount: 1, children: [] },
    ],
  },
  { id: 2, name: '일상', postCount: 0, children: [] },
];

describe('categoryTree', () => {
  it('최상위 다음에 하위를 들여 쓴 순서로 편다', () => {
    expect(flattenCategories(tree).map((o) => [o.id, o.depth, o.parentName])).toEqual([
      [1, 0, null],
      [3, 1, '개발'],
      [4, 1, '개발'],
      [2, 0, null],
    ]);
  });

  it('한 칸 옮기고 끝이면 null', () => {
    expect(moveSibling([1, 2, 3], 2, -1)).toEqual([2, 1, 3]);
    expect(moveSibling([1, 2, 3], 2, 1)).toEqual([1, 3, 2]);
    expect(moveSibling([1, 2, 3], 1, -1)).toBeNull();
    expect(moveSibling([1, 2, 3], 3, 1)).toBeNull();
    expect(moveSibling([1, 2, 3], 9, 1)).toBeNull();
  });

  it('찾기와 개수', () => {
    expect(findCategory(tree, 4)?.parent?.id).toBe(1);
    expect(findCategory(tree, 2)?.parent).toBeNull();
    expect(findCategory(tree, 9)).toBeNull();
    expect(countCategories(tree)).toBe(4);
  });
});
