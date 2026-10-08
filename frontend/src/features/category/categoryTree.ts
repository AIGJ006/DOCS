import type { BlogCategories, CategoryNode } from '../../api/categories';

/** 선택 상자 한 줄: 최상위, 그 아래 하위(들여 씀) 순서. */
export interface CategoryOption {
  id: number;
  name: string;
  /** 상위 이름 (최상위면 null) */
  parentName: string | null;
  depth: 0 | 1;
}

/** 나무 → 선택 상자 순서 (최상위, 그 하위들, 다음 최상위 …). */
export function flattenCategories(items: CategoryNode[]): CategoryOption[] {
  const options: CategoryOption[] = [];
  for (const top of items) {
    options.push({ id: top.id, name: top.name, parentName: null, depth: 0 });
    for (const child of top.children) {
      options.push({ id: child.id, name: child.name, parentName: top.name, depth: 1 });
    }
  }
  return options;
}

/** 같은 상위 안에서 `id`를 한 칸 옮긴 번호 배열. 끝이라 못 옮기면 null. */
export function moveSibling(ids: number[], id: number, direction: -1 | 1): number[] | null {
  const index = ids.indexOf(id);
  const target = index + direction;
  if (index < 0 || target < 0 || target >= ids.length) {
    return null;
  }
  const next = ids.slice();
  next[index] = ids[target];
  next[target] = id;
  return next;
}

/** 나무에서 카테고리 하나 찾기 (상위 번호 함께). */
export function findCategory(
  items: CategoryNode[],
  id: number,
): { node: CategoryNode; parent: CategoryNode | null } | null {
  for (const top of items) {
    if (top.id === id) {
      return { node: top, parent: null };
    }
    const child = top.children.find((c) => c.id === id);
    if (child) {
      return { node: child, parent: top };
    }
  }
  return null;
}

/** 카테고리 수 (최상위 + 하위). */
export function countCategories(items: CategoryNode[]): number {
  return items.reduce((sum, top) => sum + 1 + top.children.length, 0);
}

/** 나무에서 번호로 카테고리 찾기 (머리 "이름 · 글 N개"). */
export function findBlogCategory(categories: BlogCategories, id: number): CategoryNode | null {
  for (const top of categories.items) {
    if (top.id === id) {
      return top;
    }
    const child = top.children.find((c) => c.id === id);
    if (child) {
      return child;
    }
  }
  return null;
}
