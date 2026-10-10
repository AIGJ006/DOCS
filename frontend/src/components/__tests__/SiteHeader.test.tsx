import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { SessionProvider } from '../../features/auth/SessionProvider';
import { ME, errorBody, json, stubFetch } from '../../test/fetchRoutes';
import AppLayout from '../AppLayout';
import SiteHeader from '../SiteHeader';

function renderHeader(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SessionProvider>
        <SiteHeader />
        <Routes>
          <Route path="*" element={<p>본문</p>} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

const anonymous = () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요'));

let assign: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetClientForTests();
  assign = vi.fn();
  vi.stubGlobal('location', { ...window.location, assign });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('공통 머리말 (SiteHeader)', () => {
  it('비로그인: 서비스 이름(홈), [로그인]·[회원 가입]만 보이고 글쓰기·내 메뉴는 없다', async () => {
    stubFetch({ 'GET /api/me': anonymous });
    renderHeader('/somebody');

    const header = screen.getByRole('banner');
    expect(within(header).getByRole('link', { name: 'BaseLOG' })).toHaveAttribute('href', '/');
    const login = await within(header).findByRole('link', { name: '로그인' });
    // 지금 보던 화면으로 돌아오게 returnTo를 붙인다
    expect(login).toHaveAttribute('href', '/login?returnTo=%2Fsomebody');
    expect(within(header).getByRole('link', { name: '회원 가입' })).toHaveAttribute(
      'href',
      '/signup',
    );
    expect(within(header).queryByRole('link', { name: '글쓰기' })).toBeNull();
    expect(within(header).queryByRole('button', { name: /계정 메뉴/ })).toBeNull();
    // 010: [피드]는 로그인 회원에게만
    expect(within(header).queryByRole('link', { name: '피드' })).toBeNull();
  });

  it('016: 테마 버튼이 없다 — 비로그인·로그인 모두(테마는 설정 화면에서 고른다)', async () => {
    stubFetch({ 'GET /api/me': anonymous });
    const { unmount } = renderHeader('/');
    let header = screen.getByRole('banner');
    await within(header).findByRole('link', { name: '로그인' });
    expect(within(header).queryByRole('button', { name: /테마/ })).toBeNull();
    expect(header.querySelector('[data-testid="theme-toggle"]')).toBeNull();
    unmount();

    resetClientForTests();
    stubFetch({ 'GET /api/me': () => json(200, ME) });
    renderHeader('/');
    header = screen.getByRole('banner');
    await within(header).findByRole('button', { name: /계정 메뉴/ });
    expect(within(header).queryByRole('button', { name: /테마/ })).toBeNull();
    expect(header.querySelector('[data-testid="theme-toggle"]')).toBeNull();
  });

  it('로그인 화면에서는 [로그인]에 returnTo를 붙이지 않는다', async () => {
    stubFetch({ 'GET /api/me': anonymous });
    renderHeader('/login?returnTo=%2Fsettings');
    const login = await within(screen.getByRole('banner')).findByRole('link', { name: '로그인' });
    expect(login).toHaveAttribute('href', '/login');
  });

  it('로그인: [글쓰기]·내 블로그·계정 메뉴(내 글 관리·설정·로그아웃)', async () => {
    stubFetch({ 'GET /api/me': () => json(200, ME) });
    const user = userEvent.setup();
    renderHeader('/');

    const header = screen.getByRole('banner');
    expect(await within(header).findByRole('link', { name: '글쓰기' })).toHaveAttribute(
      'href',
      '/write/new',
    );
    expect(within(header).getByRole('link', { name: '내 블로그' })).toHaveAttribute(
      'href',
      '/@kim755030',
    );
    expect(within(header).queryByRole('link', { name: '로그인' })).toBeNull();
    // 010: [피드] → /feed
    expect(within(header).getByRole('link', { name: '피드' })).toHaveAttribute('href', '/feed');

    const menuButton = within(header).getByRole('button', { name: /계정 메뉴/ });
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(menuButton).toHaveTextContent('김민서');
    expect(within(header).queryByRole('link', { name: '내 글 관리' })).toBeNull();

    await user.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    expect(within(header).getByRole('link', { name: '내 글 관리' })).toHaveAttribute(
      'href',
      '/manage/posts',
    );
    expect(within(header).getByRole('link', { name: '설정' })).toHaveAttribute('href', '/settings');
    expect(within(header).getByRole('button', { name: '로그아웃' })).toBeInTheDocument();

    // Esc로 닫힌다
    await user.keyboard('{Escape}');
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(within(header).queryByRole('link', { name: '내 글 관리' })).toBeNull();
  });

  it('메뉴 밖을 누르면 닫힌다', async () => {
    stubFetch({ 'GET /api/me': () => json(200, ME) });
    const user = userEvent.setup();
    renderHeader('/');
    const menuButton = await screen.findByRole('button', { name: /계정 메뉴/ });
    await user.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    await user.click(screen.getByText('본문'));
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
  });

  it('로그아웃을 누르면 기존 로그아웃 API를 부르고 홈으로 간다', async () => {
    const fetchMock = stubFetch({
      'GET /api/me': () => json(200, ME),
      'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    });
    const user = userEvent.setup();
    renderHeader('/');

    await user.click(await screen.findByRole('button', { name: /계정 메뉴/ }));
    await user.click(screen.getByRole('button', { name: '로그아웃' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('/'));
    expect(
      fetchMock.mock.calls.some(
        ([input, init]) => String(input).endsWith('/api/auth/logout') && init?.method === 'POST',
      ),
    ).toBe(true);
  });

  it('로그아웃이 실패하면 알린다', async () => {
    stubFetch({
      'GET /api/me': () => json(200, ME),
      'POST /api/auth/logout': () =>
        json(500, errorBody('INTERNAL_ERROR', '잠시 후 다시 시도해 주세요')),
    });
    const user = userEvent.setup();
    renderHeader('/');

    await user.click(await screen.findByRole('button', { name: /계정 메뉴/ }));
    await user.click(screen.getByRole('button', { name: '로그아웃' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('로그아웃하지 못했어요');
    expect(assign).not.toHaveBeenCalled();
  });

  it('글쓰기 화면(/write/*)에서는 [글쓰기]를 숨긴다 — 머리말 자체는 남는다', async () => {
    stubFetch({ 'GET /api/me': () => json(200, ME) });
    renderHeader('/write/new');
    const header = screen.getByRole('banner');
    expect(await within(header).findByRole('button', { name: /계정 메뉴/ })).toBeInTheDocument();
    expect(within(header).queryByRole('link', { name: '글쓰기' })).toBeNull();
  });

  it('세션을 읽는 동안에는 로그인·계정 버튼을 그리지 않는다', () => {
    stubFetch({ 'GET /api/me': () => new Promise<Response>(() => undefined) });
    renderHeader('/');
    const header = screen.getByRole('banner');
    expect(within(header).getByRole('link', { name: 'BaseLOG' })).toBeInTheDocument();
    expect(within(header).queryByRole('link', { name: '로그인' })).toBeNull();
    expect(within(header).queryByRole('button', { name: /계정 메뉴/ })).toBeNull();
  });
});

describe('AppLayout', () => {
  it('로그인 상태를 읽어 머리말 아래에 화면을 그린다', async () => {
    stubFetch({ 'GET /api/me': anonymous });
    render(
      <MemoryRouter>
        <AppLayout>
          <main>화면</main>
        </AppLayout>
      </MemoryRouter>,
    );
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveTextContent('화면');
    await screen.findByRole('link', { name: '로그인' });
  });

  it('012: 검색창(이름 "검색")이 줄 맨 앞에 있고 좁은 화면용 [검색] 링크가 있다', async () => {
    stubFetch({ 'GET /api/me': anonymous });
    renderHeader('/');

    const header = screen.getByRole('banner');
    const box = within(header).getByRole('searchbox', { name: '검색' });
    const link = within(header).getByRole('link', { name: '검색' });
    expect(link).toHaveAttribute('href', '/search');
    const nav = within(header).getByRole('navigation', { name: '사이트 메뉴' });
    const last = nav.lastElementChild;
    expect(last?.contains(box)).toBe(false);
    expect(nav.firstElementChild?.contains(box)).toBe(true);

    await userEvent.type(box, '트랜잭션{Enter}');
    expect(screen.getByText('본문')).toBeInTheDocument();
  });
});
