import type { ManagePostItem } from '../../api/managePosts';

/** 테스트용 관리 목록 줄 (006 프런트 테스트 공용). */
export function manageItem(id: number, overrides: Partial<ManagePostItem> = {}): ManagePostItem {
  return {
    id,
    title: `글 ${id}`,
    status: 'DRAFT',
    visibility: 'PUBLIC',
    editing: false,
    hidden: false,
    updatedAt: '2026-10-03T05:03:00Z',
    publishedAt: null,
    editedAt: null,
    deletedAt: null,
    purgeAt: null,
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    ...overrides,
  };
}
