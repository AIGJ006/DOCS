import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { SessionProvider } from '../features/auth/SessionProvider';
import { ME, errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import SettingsPage from './SettingsPage';

vi.mock('../features/profile/uploadProfileImage', () => ({
  uploadProfileImage: vi.fn(async () => 55),
}));

vi.mock('../components/ProfileImageCropper', () => ({
  default: ({ onCropped }: { onCropped: (blob: Blob) => void }) => (
    <button type="button" onClick={() => onCropped(new Blob(['x'], { type: 'image/webp' }))}>
      가짜 자르기 완료
    </button>
  ),
}));

const PROFILE = {
  handle: 'kim755030',
  nickname: '김민서',
  bio: '백엔드 개발을 공부하고 있어요.',
  profileImageId: null,
  profileImageUrl: null,
  nicknameChangeAvailableAt: null,
};

const SETTINGS = {
  email: 'kim755030@naver.com',
  provider: 'LOCAL',
  previousLogin: null,
  defaultVisibility: 'PUBLIC',
  lastActiveVisible: true,
  passwordChangeAvailable: true,
};

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/settings']}>
      <SessionProvider>
        <Where />
        <Routes>
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/login" element={<p>로그인 화면</p>} />
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  );
}

function routes(overrides: Record<string, (init: RequestInit | undefined) => Response> = {}) {
  return stubFetch({
    'GET /api/me': () => json(200, ME),
    'GET /api/me/profile': () => json(200, PROFILE),
    'GET /api/me/settings': () => json(200, SETTINGS),
    'PATCH /api/me/profile': (init) => json(200, { ...PROFILE, ...JSON.parse(String(init?.body)) }),
    'PATCH /api/me/settings': (init) =>
      json(200, { ...SETTINGS, ...JSON.parse(String(init?.body)) }),
    'GET /api/me/friends': () => json(200, { items: [], nextCursor: null }),
    'GET /api/me/friend-requests': () => json(200, { items: [], nextCursor: null }),
    ...overrides,
  });
}

function bodyOf(mock: ReturnType<typeof stubFetch>, method: string, path: string, index = 0) {
  return JSON.parse(String(requestsTo(mock, method, path)[index]?.[1]?.body));
}

beforeEach(() => resetClientForTests());
afterEach(() => vi.unstubAllGlobals());

describe('SettingsPage (FR-046~053)', () => {
  it('비로그인이면 로그인 화면으로 보내고 돌아올 곳을 남긴다', async () => {
    stubFetch({ 'GET /api/me': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')) });
    renderPage();
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent('/login?returnTo=%2Fsettings'),
    );
  });

  it('프로필·계정 정보를 보이고 블로그 주소·이메일은 읽기 전용', async () => {
    routes();
    renderPage();
    expect(await screen.findByLabelText('닉네임')).toHaveValue('김민서');
    expect(screen.getByLabelText('소개')).toHaveValue('백엔드 개발을 공부하고 있어요.');
    expect(screen.getByText('@kim755030')).toBeInTheDocument();
    expect(screen.getByText('kim755030@naver.com')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '이메일' })).not.toBeInTheDocument();
    expect(screen.getByTestId('default-avatar')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '이용약관' })).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', { name: '개인정보 처리방침' })).toHaveAttribute(
      'href',
      '/privacy',
    );
    expect(screen.getByRole('heading', { name: '비밀번호 변경' })).toBeInTheDocument();
  });

  it('[저장]은 바꾼 칸만 보낸다', async () => {
    const mock = routes();
    const user = userEvent.setup();
    renderPage();
    const bio = await screen.findByLabelText('소개');
    await user.clear(bio);
    await user.type(bio, '새 소개');
    await user.click(screen.getByRole('button', { name: '저장' }));
    await screen.findByText('저장했어요');
    expect(bodyOf(mock, 'PATCH', '/api/me/profile')).toEqual({ bio: '새 소개' });
  });

  it('바꾼 칸이 없으면 보내지 않는다', async () => {
    const mock = routes();
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('닉네임');
    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('바꾼 내용이 없어요')).toBeInTheDocument();
    expect(requestsTo(mock, 'PATCH', '/api/me/profile')).toHaveLength(0);
  });

  it('실패한 칸을 모두 보이고 입력값은 그대로 둔다 (SC-008)', async () => {
    routes({
      'PATCH /api/me/profile': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력값을 확인해 주세요', [
            {
              field: 'nickname',
              code: 'NICKNAME_DUPLICATE',
              message: '이미 사용 중인 닉네임이에요',
            },
            { field: 'bio', code: 'BIO_TOO_MANY_LINES', message: '소개는 4줄까지 쓸 수 있어요' },
          ]),
        ),
    });
    const user = userEvent.setup();
    renderPage();
    const nickname = await screen.findByLabelText('닉네임');
    await user.clear(nickname);
    await user.type(nickname, '다른이름');
    await user.type(screen.getByLabelText('소개'), '{Enter}1{Enter}2{Enter}3{Enter}4');
    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('이미 사용 중인 닉네임이에요')).toBeInTheDocument();
    expect(screen.getByText('소개는 4줄까지 쓸 수 있어요')).toBeInTheDocument();
    expect(nickname).toHaveValue('다른이름');
  });

  it('닉네임 30일 제한 중이면 칸을 막고 다음 변경 가능일을 보인다', async () => {
    routes({
      'GET /api/me/profile': () =>
        json(200, { ...PROFILE, nicknameChangeAvailableAt: '2026-11-07T03:00:00Z' }),
    });
    renderPage();
    expect(await screen.findByLabelText('닉네임')).toBeDisabled();
    expect(screen.getByText(/다음 변경 가능일 2026\.11\.07/)).toBeInTheDocument();
  });

  it('409 NICKNAME_CHANGE_TOO_SOON이면 그 문구를 닉네임 칸에 보인다', async () => {
    routes({
      'PATCH /api/me/profile': () =>
        json(
          409,
          errorBody(
            'NICKNAME_CHANGE_TOO_SOON',
            '닉네임은 바꾼 뒤 30일이 지나야 다시 바꿀 수 있어요',
            [],
            { nextChangeAvailableAt: '2026-11-07T03:00:00Z' },
          ),
        ),
    });
    const user = userEvent.setup();
    renderPage();
    const nickname = await screen.findByLabelText('닉네임');
    await user.clear(nickname);
    await user.type(nickname, '다른이름');
    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(
      await screen.findByText(/닉네임은 바꾼 뒤 30일이 지나야 다시 바꿀 수 있어요/),
    ).toBeInTheDocument();
  });

  it('소개 글자 수는 코드 포인트로 센다', async () => {
    routes({ 'GET /api/me/profile': () => json(200, { ...PROFILE, bio: null }) });
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('소개'), '😀가');
    expect(screen.getByText('2/200')).toBeInTheDocument();
  });

  it('사진을 고르면 [저장] 때 올리고 그 ID로 연결한다', async () => {
    const mock = routes();
    const user = userEvent.setup();
    const createObjectURL = vi.fn(() => 'blob:cropped');
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }));
    renderPage();
    await user.click(await screen.findByRole('button', { name: '사진 바꾸기' }));
    await user.click(screen.getByRole('button', { name: '가짜 자르기 완료' }));
    expect(screen.getByRole('img', { name: '새 프로필 사진 미리보기' })).toHaveAttribute(
      'src',
      'blob:cropped',
    );
    expect(requestsTo(mock, 'PATCH', '/api/me/profile')).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: '저장' }));
    await screen.findByText('저장했어요');
    const { uploadProfileImage } = await import('../features/profile/uploadProfileImage');
    expect(uploadProfileImage).toHaveBeenCalledTimes(1);
    expect(bodyOf(mock, 'PATCH', '/api/me/profile')).toEqual({ profileImageId: 55 });
  });

  it('[기본 이미지로]는 profileImageId null을 보낸다', async () => {
    const mock = routes({
      'GET /api/me/profile': () =>
        json(200, { ...PROFILE, profileImageId: 9, profileImageUrl: 'https://img.example/p.webp' }),
    });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: '기본 이미지로' }));
    await user.click(screen.getByRole('button', { name: '저장' }));
    await screen.findByText('저장했어요');
    expect(bodyOf(mock, 'PATCH', '/api/me/profile')).toEqual({ profileImageId: null });
  });

  it('새 글 기본 공개 범위·최근 활동 공개를 바꾸면 바로 저장한다', async () => {
    const mock = routes();
    const user = userEvent.setup();
    renderPage();
    await user.selectOptions(await screen.findByLabelText('새 글 기본 공개 범위'), 'PRIVATE');
    await waitFor(() =>
      expect(bodyOf(mock, 'PATCH', '/api/me/settings')).toEqual({ defaultVisibility: 'PRIVATE' }),
    );
    await user.click(screen.getByLabelText('최근 활동을 친구에게 보이기'));
    await waitFor(() =>
      expect(bodyOf(mock, 'PATCH', '/api/me/settings', 1)).toEqual({ lastActiveVisible: false }),
    );
  });

  it('직전 로그인: 없으면 "첫 로그인", 있으면 YYYY.MM.DD HH:mm(Asia/Seoul)과 로그인 방식', async () => {
    routes();
    const { unmount } = renderPage();
    const account = await screen.findByRole('region', { name: '계정' });
    expect(within(account).getByText('첫 로그인')).toBeInTheDocument();
    expect(
      within(account).getByText('끄면 나도 친구의 최근 활동을 볼 수 없어요', { exact: false }),
    ).toBeInTheDocument();
    unmount();
    routes({
      'GET /api/me/settings': () =>
        json(200, {
          ...SETTINGS,
          previousLogin: { at: '2026-10-06T01:30:00Z', provider: 'GOOGLE' },
        }),
    });
    renderPage();
    expect(await screen.findByText('2026.10.06 10:30, Google')).toBeInTheDocument();
  });

  it('친구 목록에 최근 활동 구간을 보인다', async () => {
    routes({
      'GET /api/me/friends': () =>
        json(200, {
          items: [
            {
              handle: 'bob',
              nickname: '밥밥이',
              profileImageUrl: null,
              friendsSince: '2026-09-01T00:00:00Z',
              lastActive: { bucket: 'DAYS_AGO', days: 3 },
            },
          ],
          nextCursor: null,
        }),
    });
    renderPage();
    expect(await screen.findByText('최근 활동 3일 전')).toBeInTheDocument();
  });

  it('소셜 계정은 비밀번호 변경을 보이지 않고 로그인 수단을 보인다', async () => {
    routes({
      'GET /api/me/settings': () =>
        json(200, { ...SETTINGS, provider: 'GITHUB', passwordChangeAvailable: false }),
    });
    renderPage();
    const account = await screen.findByRole('region', { name: '계정' });
    expect(within(account).getByText('GitHub')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '비밀번호 변경' })).not.toBeInTheDocument();
  });
});

describe('SettingsPage 친구 (FR-055·056)', () => {
  it('받은 친구 요청 [수락]·[거절]과 내 친구 [친구 끊기]', async () => {
    const mock = routes({
      'GET /api/me/friend-requests': () =>
        json(200, {
          items: [
            {
              handle: 'carol',
              nickname: '캐롤',
              profileImageUrl: null,
              requestedAt: '2026-10-01T00:00:00Z',
            },
            {
              handle: 'dave',
              nickname: '데이브',
              profileImageUrl: null,
              requestedAt: '2026-09-30T00:00:00Z',
            },
          ],
          nextCursor: null,
        }),
      'GET /api/me/friends': (init) =>
        json(200, {
          items: [
            {
              handle: 'bob',
              nickname: '밥밥이',
              profileImageUrl: 'https://img.example/bob.webp',
              friendsSince: '2026-09-01T00:00:00Z',
            },
          ],
          nextCursor: init ? null : null,
        }),
      'PUT /api/members/carol/friend': () => json(200, { status: 'FRIENDS' }),
      'DELETE /api/members/dave/friend': () => json(200, { status: 'NONE' }),
      'DELETE /api/members/bob/friend': () => json(200, { status: 'NONE' }),
    });
    const user = userEvent.setup();
    renderPage();
    const requests = await screen.findByRole('region', { name: '받은 친구 요청' });
    expect(await within(requests).findByText('캐롤')).toBeInTheDocument();
    expect(within(requests).getByText('@carol')).toBeInTheDocument();
    const friends = screen.getByRole('region', { name: '내 친구' });
    expect(await within(friends).findByText('밥밥이')).toBeInTheDocument();
    expect(friends.querySelector('img')).toHaveAttribute('src', 'https://img.example/bob.webp');

    await user.click(within(requests).getAllByRole('button', { name: '수락' })[0]);
    await waitFor(() => expect(within(requests).queryByText('캐롤')).not.toBeInTheDocument());
    expect(requestsTo(mock, 'PUT', '/api/members/carol/friend')).toHaveLength(1);

    await user.click(within(requests).getByRole('button', { name: '거절' }));
    await waitFor(() => expect(within(requests).queryByText('데이브')).not.toBeInTheDocument());
    expect(within(requests).getByText('받은 요청이 없어요')).toBeInTheDocument();

    await user.click(within(friends).getByRole('button', { name: '친구 끊기' }));
    await user.click(await screen.findByRole('button', { name: '끊기' }));
    await waitFor(() => expect(within(friends).queryByText('밥밥이')).not.toBeInTheDocument());
    expect(requestsTo(mock, 'DELETE', '/api/members/bob/friend')).toHaveLength(1);
  });

  it('[더 보기]로 다음 페이지를 붙이고 같은 사람은 한 번만', async () => {
    const page1 = {
      items: [
        {
          handle: 'f1',
          nickname: '친구일',
          profileImageUrl: null,
          friendsSince: '2026-09-02T00:00:00Z',
        },
      ],
      nextCursor: 'c1',
    };
    const page2 = {
      items: [
        {
          handle: 'f1',
          nickname: '친구일',
          profileImageUrl: null,
          friendsSince: '2026-09-02T00:00:00Z',
        },
        {
          handle: 'f2',
          nickname: '친구이',
          profileImageUrl: null,
          friendsSince: '2026-09-01T00:00:00Z',
        },
      ],
      nextCursor: null,
    };
    const mock = routes({
      'GET /api/me/friend-requests': () => json(200, { items: [], nextCursor: null }),
    });
    mock.mockImplementation(async (input, init) => {
      const url = String(input);
      const path = url.split('?')[0];
      if (path === '/api/me') return json(200, ME);
      if (path === '/api/me/profile') return json(200, PROFILE);
      if (path === '/api/me/settings') return json(200, SETTINGS);
      if (path === '/api/me/friend-requests') return json(200, { items: [], nextCursor: null });
      if (path === '/api/me/friends') return json(200, url.includes('cursor=c1') ? page2 : page1);
      return json(404, errorBody('NOT_FOUND', String(init?.method)));
    });
    const user = userEvent.setup();
    renderPage();
    const friends = await screen.findByRole('region', { name: '내 친구' });
    await within(friends).findByText('친구일');
    await user.click(within(friends).getByRole('button', { name: '더 보기' }));
    expect(await within(friends).findByText('친구이')).toBeInTheDocument();
    expect(within(friends).getAllByText('친구일')).toHaveLength(1);
    expect(within(friends).queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
  });
});
