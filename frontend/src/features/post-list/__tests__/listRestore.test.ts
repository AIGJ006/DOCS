import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PostCard } from '../../../api/types/reading';
import { LIST_RESTORE_TTL_MINUTES } from '../../../config';
import * as listRestore from '../listRestore';

function card(id: number): PostCard {
  return {
    id,
    url: `/@kim755030/posts/${id}`,
    title: `글 ${id}`,
    excerpt: null,
    thumbnailUrl: null,
    firstPublicAt: '2026-10-02T14:03:12.123456Z',
    commentCount: 0,
    likeCount: 0,
    author: { handle: 'kim755030', nickname: '김민서', profileImageUrl: null },
  };
}

const STATE = { items: [card(3), card(2)], nextCursor: 'c1', scrollY: 1234 };

/** 목록 복원 저장소 (005 T066, US6 #1·#2, FR-018, SC-010). */
describe('listRestore', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-07T05:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  it('보관 시간은 30분이다', () => {
    expect(LIST_RESTORE_TTL_MINUTES).toBe(30);
  });

  it('목록 키별로 list-restore:{목록키}에 저장하고 그대로 돌려준다', () => {
    listRestore.save('home', STATE);
    listRestore.save('blog:na_ms', { items: [card(9)], nextCursor: null, scrollY: 0 });

    expect(sessionStorage.getItem('list-restore:home')).not.toBeNull();
    expect(sessionStorage.getItem('list-restore:blog:na_ms')).not.toBeNull();
    const restored = listRestore.load('home');
    expect(restored).toEqual({ ...STATE, savedAt: Date.parse('2026-10-07T05:00:00Z') });
    expect(listRestore.load('blog:na_ms')?.items.map((c) => c.id)).toEqual([9]);
  });

  it('30분 안이면 복원하고 30분이 지나면 null', () => {
    listRestore.save('home', STATE);

    vi.advanceTimersByTime(LIST_RESTORE_TTL_MINUTES * 60_000);
    expect(listRestore.load('home')).not.toBeNull();

    vi.advanceTimersByTime(1);
    expect(listRestore.load('home')).toBeNull();
  });

  it('값이 없으면 null', () => {
    expect(listRestore.load('home')).toBeNull();
  });

  it('JSON이 깨졌거나 형태가 다르면 null', () => {
    sessionStorage.setItem('list-restore:home', '{not json');
    expect(listRestore.load('home')).toBeNull();

    sessionStorage.setItem(
      'list-restore:home',
      JSON.stringify({ items: 'x', savedAt: Date.now() }),
    );
    expect(listRestore.load('home')).toBeNull();

    sessionStorage.setItem(
      'list-restore:home',
      JSON.stringify({ items: [], nextCursor: 5, scrollY: 0, savedAt: Date.now() }),
    );
    expect(listRestore.load('home')).toBeNull();
  });

  it('sessionStorage 접근이 막혀도 예외 없이 null·무시', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError');
    });

    expect(() => listRestore.save('home', STATE)).not.toThrow();
    expect(listRestore.load('home')).toBeNull();
  });
});
