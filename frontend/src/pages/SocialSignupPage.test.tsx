import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { CURRENT_AGREEMENTS, errorBody, json, requestsTo, stubFetch } from '../test/fetchRoutes';
import SocialSignupPage from './SocialSignupPage';

vi.mock('../features/profile/socialPhotoImport', () => ({
  importSocialPhoto: vi.fn(async () => ({ ok: true, imageId: 1 })),
}));

const DRAFT = {
  provider: 'GOOGLE',
  handlePrefix: 'go-',
  suggestedHandleBody: 'alice_k',
  suggestedNickname: '앨리스',
  email: 'alice.k@gmail.com',
  emailRequired: false,
  profilePhotoUrl: 'https://lh3.googleusercontent.com/a/x=s256-c',
  existingAccountNotice: false,
  expiresAt: '2026-10-08T00:10:00Z',
};

let assign: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetClientForTests();
  assign = vi.fn();
  vi.stubGlobal('location', { ...window.location, assign });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderPage() {
  return render(
    <MemoryRouter>
      <SocialSignupPage />
    </MemoryRouter>,
  );
}

describe('SocialSignupPage', () => {
  it('미리 채운 값을 보이고 가입을 마치면 사진을 복사한 뒤 전체 페이지로 이동한다', async () => {
    const { importSocialPhoto } = await import('../features/profile/socialPhotoImport');
    const fetchMock = stubFetch({
      'GET /api/auth/social-signup': () => json(200, DRAFT),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'POST /api/auth/social-signup': () =>
        json(201, {
          handle: 'go-alice_k',
          nickname: '앨리스',
          emailVerified: true,
          profilePhotoUrl: DRAFT.profilePhotoUrl,
          redirectTo: '/@someone/posts/1',
        }),
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByLabelText('블로그 주소')).toHaveValue('alice_k');
    expect(screen.getAllByText('go-').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('닉네임')).toHaveValue('앨리스');
    expect(screen.getByLabelText('프로필 사진 사용')).toBeChecked();
    expect(screen.queryByLabelText('이메일')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('이용약관에 동의해요 (필수)'));
    await user.click(screen.getByLabelText('개인정보 처리방침에 동의해요 (필수)'));
    await user.click(screen.getByRole('button', { name: '가입 완료' }));

    await waitFor(() => expect(assign).toHaveBeenCalledWith('/@someone/posts/1'));
    expect(importSocialPhoto).toHaveBeenCalledWith(DRAFT.profilePhotoUrl);
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/social-signup');
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      handleBody: 'alice_k',
      nickname: '앨리스',
      email: null,
      useProfilePhoto: true,
      agreements: { termsVersion: '2026-10-07', privacyVersion: '2026-10-07' },
    });
  });

  it('GitHub 이메일이 필요하면 이메일 칸을 보이고, 같은 이메일 계정 안내를 보인다', async () => {
    stubFetch({
      'GET /api/auth/social-signup': () =>
        json(200, {
          ...DRAFT,
          provider: 'GITHUB',
          handlePrefix: 'gi-',
          suggestedNickname: null,
          email: null,
          emailRequired: true,
          profilePhotoUrl: null,
          existingAccountNotice: true,
        }),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
    });
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByLabelText('이메일')).toBeInTheDocument();
    expect(screen.queryByLabelText('프로필 사진 사용')).not.toBeInTheDocument();
    expect(screen.getAllByText('닉네임을 입력해 주세요').length).toBeGreaterThan(0);
    expect(screen.getByText('이 이메일로 가입한 계정이 이미 있어요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '기존 계정으로 로그인' })).toHaveAttribute(
      'href',
      '/login',
    );
    await user.click(screen.getByRole('button', { name: '새 계정 만들기' }));
    expect(screen.queryByText('이 이메일로 가입한 계정이 이미 있어요')).not.toBeInTheDocument();
  });

  it('대기 정보가 만료됐으면 다시 소셜 로그인으로 안내한다', async () => {
    stubFetch({
      'GET /api/auth/social-signup': () =>
        json(410, errorBody('SOCIAL_SIGNUP_EXPIRED', '소셜 로그인 정보가 만료됐어요')),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
    });
    renderPage();
    expect(await screen.findByText('소셜 로그인 정보가 만료됐어요')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '다시 소셜 로그인' })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('칸 오류를 칸 아래에 보인다', async () => {
    stubFetch({
      'GET /api/auth/social-signup': () => json(200, DRAFT),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'POST /api/auth/social-signup': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
            { field: 'handleBody', code: 'HANDLE_RESERVED', message: '사용할 수 없는 주소예요' },
          ]),
        ),
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('블로그 주소');
    await user.click(screen.getByLabelText('이용약관에 동의해요 (필수)'));
    await user.click(screen.getByLabelText('개인정보 처리방침에 동의해요 (필수)'));
    await user.click(screen.getByRole('button', { name: '가입 완료' }));
    expect(await screen.findByText('사용할 수 없는 주소예요')).toBeInTheDocument();
    expect(assign).not.toHaveBeenCalled();
  });
});
