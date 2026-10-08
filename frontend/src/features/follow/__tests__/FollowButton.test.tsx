import { act, fireEvent, render, screen } from '@testing-library/react';
import type { MeSummary } from '../../../api/me';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../../api/client';
import { SessionContext } from '../../auth/sessionContext';
import { ME, errorBody, json, requestsTo, stubFetch } from '../../../test/fetchRoutes';
import FollowButton, { type FollowButtonProps } from '../FollowButton';
import { FOLLOW_DEBOUNCE_MS } from '../useFollowToggle';

const PATH = '/api/members/na_ms/follow';

function renderButton(props: Partial<FollowButtonProps> = {}, { loggedIn = true } = {}) {
  const session = {
    loading: false,
    me: loggedIn ? ({ ...ME, emailVerified: true } as MeSummary) : null,
    refresh: async () => null,
  };
  return render(
    <MemoryRouter initialEntries={['/@na_ms']}>
      <SessionContext.Provider value={session}>
        <FollowButton handle="na_ms" initialFollowing={false} isMe={false} {...props} />
      </SessionContext.Provider>
    </MemoryRouter>,
  );
}

async function flush(ms = FOLLOW_DEBOUNCE_MS) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** 팔로우 버튼 (010 T016, FR-009~012). */
describe('FollowButton', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetClientForTests();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    resetClientForTests();
  });

  it('[팔로우]를 누르면 즉시 [팔로잉 ✓]이 되고 수가 +1, 확인 창 없음', async () => {
    stubFetch({ [`PUT ${PATH}`]: () => json(200, { following: true, followerCount: 13 }) });
    const onCountChange = vi.fn();
    renderButton({ initialCount: 12, onCountChange });

    const button = screen.getByRole('button', { name: '팔로우' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-pressed', 'true');
    expect(button).toHaveTextContent('팔로잉 ✓');
    expect(onCountChange).toHaveBeenLastCalledWith(13);
    expect(screen.queryByRole('dialog')).toBeNull();
    await flush();
    expect(onCountChange).toHaveBeenLastCalledWith(13);
  });

  it('[팔로잉 ✓]에 마우스를 올리거나 초점을 주면 [언팔로우], 누르면 바로 언팔로우', async () => {
    const fetchMock = stubFetch({
      [`DELETE ${PATH}`]: () => json(200, { following: false, followerCount: 4 }),
    });
    renderButton({ initialFollowing: true, initialCount: 5 });

    const button = screen.getByRole('button', { pressed: true });
    expect(button).toHaveTextContent('팔로잉 ✓');
    fireEvent.mouseEnter(button);
    expect(button).toHaveTextContent('언팔로우');
    expect(button).not.toHaveTextContent('✓');
    fireEvent.mouseLeave(button);
    expect(button).toHaveTextContent('팔로잉 ✓');
    fireEvent.focus(button);
    expect(button).toHaveTextContent('언팔로우');

    fireEvent.click(button);
    expect(button).toHaveAttribute('aria-pressed', 'false');
    expect(button).toHaveTextContent('팔로우');
    await flush();
    expect(requestsTo(fetchMock, 'DELETE', PATH)).toHaveLength(1);
  });

  it('0.3초 안에 여러 번 누르면 마지막 상태 한 번만 보낸다', async () => {
    const fetchMock = stubFetch({
      [`PUT ${PATH}`]: () => json(200, { following: true, followerCount: 1 }),
      [`DELETE ${PATH}`]: () => json(200, { following: false, followerCount: 0 }),
    });
    renderButton();
    const button = screen.getByRole('button', { name: '팔로우' });

    fireEvent.click(button);
    fireEvent.click(button);
    fireEvent.click(button);
    await flush();

    expect(requestsTo(fetchMock, 'PUT', PATH)).toHaveLength(1);
    expect(requestsTo(fetchMock, 'DELETE', PATH)).toHaveLength(0);
  });

  it('실패하면 되돌리고 "잠시 후 다시 시도해 주세요"', async () => {
    stubFetch({
      [`PUT ${PATH}`]: () =>
        json(429, errorBody('TOO_MANY_REQUESTS', '잠시 후 다시 시도해 주세요')),
    });
    const onCountChange = vi.fn();
    renderButton({ initialCount: 2, onCountChange });

    fireEvent.click(screen.getByRole('button', { name: '팔로우' }));
    await flush();

    expect(screen.getByRole('button', { name: '팔로우' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('status')).toHaveTextContent('잠시 후 다시 시도해 주세요');
    expect(onCountChange).toHaveBeenLastCalledWith(2);
  });

  it('비회원이 누르면 요청 없이 로그인 안내', async () => {
    const fetchMock = stubFetch({});
    renderButton({}, { loggedIn: false });

    fireEvent.click(screen.getByRole('button', { name: '팔로우' }));
    await flush();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요해요');
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F%40na_ms',
    );
    expect(screen.getByRole('button', { name: '팔로우' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('loggedIn을 주면 세션 대신 그 값으로 판단한다', () => {
    stubFetch({});
    renderButton({ loggedIn: false });
    fireEvent.click(screen.getByRole('button', { name: '팔로우' }));
    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요해요');
  });

  it('403 정지 응답이면 계정 안내로 바꾼다', async () => {
    stubFetch({
      [`PUT ${PATH}`]: () => json(403, errorBody('ACCOUNT_SUSPENDED', '정지된 계정이에요')),
    });
    renderButton();
    fireEvent.click(screen.getByRole('button', { name: '팔로우' }));
    await flush();
    expect(screen.getByRole('alert')).toHaveTextContent('정지된 계정이에요');
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
  });

  it('내 블로그면 버튼이 없다', () => {
    renderButton({ isMe: true });
    expect(screen.queryByRole('button')).toBeNull();
  });
});
