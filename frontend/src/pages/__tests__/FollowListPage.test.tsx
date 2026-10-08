import { render, screen, waitFor, within } from '@testing-library/react';
import type { MeSummary } from '../../api/me';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import type { FollowListItem, FollowListPage as Page } from '../../api/types/follow';
import type { BlogHeader } from '../../api/types/reading';
import { SessionContext } from '../../features/auth/sessionContext';
import { FOLLOW_MESSAGES } from '../../features/follow/followMessages';
import { ME, json, requestsTo, stubFetch } from '../../test/fetchRoutes';
import FollowListPage from '../FollowListPage';

const HEADER: BlogHeader = {
  handle: 'na_ms',
  nickname: '나민서',
  bio: null,
  profileImageUrl: null,
  publicPostCount: 3,
  isMe: false,
  followerCount: 2,
  followingCount: 0,
  followedByMe: false,
};

function item(n: number, overrides: Partial<FollowListItem> = {}): FollowListItem {
  return {
    handle: `fan_${n}`,
    nickname: `팬${n}`,
    profileImageUrl: null,
    bio: `첫 줄 ${n}\n둘째 줄`,
    followedByMe: false,
    isMe: false,
    ...overrides,
  };
}

function renderList(path: string) {
  const session = {
    loading: false,
    me: { ...ME, emailVerified: true } as MeSummary,
    refresh: async () => null,
  };
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SessionContext.Provider value={session}>
        <Routes>
          <Route path="/:handle/followers" element={<FollowListPage mode="followers" />} />
          <Route path="/:handle/following" element={<FollowListPage mode="following" />} />
        </Routes>
      </SessionContext.Provider>
    </MemoryRouter>,
  );
}

/** 팔로워·팔로잉 목록 화면 (010 T033, US3, FR-014~016). */
describe('FollowListPage', () => {
  beforeEach(() => resetClientForTests());
  afterEach(() => vi.unstubAllGlobals());

  it('제목·항목(닉네임 @주소·소개 첫 줄)·나 자신은 버튼 없음', async () => {
    const page: Page = {
      items: [item(1, { followedByMe: true }), item(2, { isMe: true })],
      nextCursor: null,
    };
    stubFetch({
      'GET /api/members/na_ms': () => json(200, HEADER),
      'GET /api/members/na_ms/followers': () => json(200, page),
    });
    renderList('/@na_ms/followers');

    expect(await screen.findByRole('heading', { name: '나민서님의 팔로워' })).toBeInTheDocument();
    const rows = await screen.findAllByTestId('follow-list-item');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByRole('link', { name: /팬1 @fan_1/ })).toHaveAttribute(
      'href',
      '/@fan_1',
    );
    expect(within(rows[0]).getByTestId('follow-list-bio')).toHaveTextContent('첫 줄 1');
    expect(within(rows[0]).getByTestId('follow-list-bio')).not.toHaveTextContent('둘째 줄');
    expect(within(rows[0]).getByRole('button', { pressed: true })).toBeInTheDocument();
    expect(within(rows[1]).queryByRole('button')).toBeNull();
    expect(screen.getByTestId('all-seen')).toBeInTheDocument();
  });

  it('[더 보기]는 nextCursor를 그대로 보내고 size는 보내지 않는다', async () => {
    let call = 0;
    const pages: Page[] = [
      { items: [item(1), item(2)], nextCursor: 'n1' },
      { items: [item(2), item(3)], nextCursor: null },
    ];
    const fetchMock = stubFetch({
      'GET /api/members/na_ms': () => json(200, HEADER),
      'GET /api/members/na_ms/following': () => json(200, pages[call++]),
    });
    renderList('/@na_ms/following');
    await waitFor(() => expect(screen.getAllByTestId('follow-list-item')).toHaveLength(2));
    expect(screen.getByRole('heading', { name: '나민서님의 팔로잉' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '더 보기' }));
    // 겹친 fan_2는 한 번만
    await waitFor(() => expect(screen.getAllByTestId('follow-list-item')).toHaveLength(3));
    const calls = requestsTo(fetchMock, 'GET', '/api/members/na_ms/following');
    expect(String(calls[0][0])).not.toContain('size=');
    expect(String(calls[1][0])).toContain('cursor=n1');
  });

  it('빈 목록 문구', async () => {
    stubFetch({
      'GET /api/members/na_ms': () => json(200, HEADER),
      'GET /api/members/na_ms/followers': () => json(200, { items: [], nextCursor: null }),
      'GET /api/members/na_ms/following': () => json(200, { items: [], nextCursor: null }),
    });
    renderList('/@na_ms/followers');
    expect(await screen.findByTestId('empty-follow-list')).toHaveTextContent(
      FOLLOW_MESSAGES.noFollowers,
    );
  });

  it('없는 주소면 공통 404 화면', async () => {
    stubFetch({});
    renderList('/@ghost/followers');
    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
  });

  it('@ 없는 주소는 404 화면 (요청 없음)', async () => {
    const fetchMock = stubFetch({});
    renderList('/na_ms/followers');
    expect(await screen.findByText('볼 수 없는 페이지예요')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
