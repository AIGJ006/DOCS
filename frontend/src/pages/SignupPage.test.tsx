import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { SessionProvider } from '../features/auth/SessionProvider';
import {
  CURRENT_AGREEMENTS,
  ME,
  errorBody,
  json,
  requestsTo,
  stubFetch,
} from '../test/fetchRoutes';
import SignupPage from './SignupPage';

function renderPage() {
  return render(
    <MemoryRouter>
      <SessionProvider>
        <SignupPage />
      </SessionProvider>
    </MemoryRouter>,
  );
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('이메일'), ' Kim755030@Naver.com ');
  await user.type(screen.getByLabelText('비밀번호'), 'Blog#2026a');
  await user.type(screen.getByLabelText('비밀번호 확인'), 'Blog#2026a');
  await user.type(screen.getByLabelText('닉네임'), '김민서');
  await user.click(screen.getByLabelText(/이용약관/));
  await user.click(screen.getByLabelText(/개인정보 처리방침/));
}

beforeEach(() => {
  resetClientForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('SignupPage', () => {
  it('이메일로 블로그 주소를 미리 채우고, 현재 약관 버전과 함께 가입을 보낸 뒤 안내를 보인다', async () => {
    let me: Response = json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요'));
    const fetchMock = stubFetch({
      'GET /api/me': () => me,
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'POST /api/auth/signup': () => {
        me = json(200, ME);
        return json(201, { handle: 'kim755030', nickname: '김민서', emailVerified: false });
      },
    });
    const user = userEvent.setup();
    renderPage();

    await fillForm(user);
    expect(screen.getByLabelText('블로그 주소')).toHaveValue('kim755030');
    expect(screen.getByRole('link', { name: '이용약관' })).toHaveAttribute('href', '/terms');
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('인증 메일을 보냈어요')).toBeInTheDocument();
    const [call] = requestsTo(fetchMock, 'POST', '/api/auth/signup');
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      email: 'Kim755030@Naver.com', // type=email 칸은 앞뒤 공백을 브라우저가 지운다
      handle: 'kim755030',
      password: 'Blog#2026a',
      passwordConfirm: 'Blog#2026a',
      nickname: '김민서',
      agreements: { termsVersion: '2026-10-07', privacyVersion: '2026-10-07' },
    });
  });

  it('입력하는 동안 비밀번호 규칙 체크리스트를 보인다', async () => {
    stubFetch({ 'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS) });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('비밀번호'), 'abc');
    expect(screen.getByRole('list', { name: '비밀번호 규칙' })).toHaveTextContent('최대 16자');
  });

  it('서버 칸 오류를 칸마다 모두 보이고, 이미 가입된 이메일이면 로그인·비밀번호 찾기 링크를 준다', async () => {
    stubFetch({
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'POST /api/auth/signup': () =>
        json(
          400,
          errorBody('VALIDATION_FAILED', '입력값을 확인해 주세요', [
            {
              field: 'email',
              code: 'EMAIL_ALREADY_REGISTERED',
              message: '이미 가입된 이메일이에요. [로그인] [비밀번호 찾기]',
            },
            {
              field: 'nickname',
              code: 'NICKNAME_DUPLICATE',
              message: '이미 사용 중인 닉네임이에요',
            },
          ]),
        ),
    });
    const user = userEvent.setup();
    renderPage();
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('이미 사용 중인 닉네임이에요')).toBeInTheDocument();
    expect(screen.getByText(/이미 가입된 이메일이에요/)).toBeInTheDocument();
    expect(screen.queryByText(/\[로그인\]/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: '비밀번호 찾기' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
    expect(screen.getByLabelText('이메일')).toHaveAttribute('aria-invalid', 'true');
  });

  it('블로그 주소가 이미 쓰이면 서버가 준 추천 주소를 버튼으로 넣을 수 있다', async () => {
    stubFetch({
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'POST /api/auth/signup': () =>
        json(
          400,
          errorBody(
            'VALIDATION_FAILED',
            '입력값을 확인해 주세요',
            [
              {
                field: 'handle',
                code: 'HANDLE_DUPLICATE',
                message: '이미 사용 중인 주소예요. `kim755030_2`는 어떠세요?',
              },
            ],
            { handleSuggestion: 'kim755030_2' },
          ),
        ),
    });
    const user = userEvent.setup();
    renderPage();
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    await user.click(await screen.findByRole('button', { name: 'kim755030_2 쓰기' }));
    expect(screen.getByLabelText('블로그 주소')).toHaveValue('kim755030_2');
  });

  it('필수 동의를 하지 않으면 보내지 않고 안내한다', async () => {
    const fetchMock = stubFetch({
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
    });
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText('이메일'), 'kim@naver.com');
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(
      await screen.findByText('이용약관과 개인정보 처리방침에 동의해 주세요'),
    ).toBeInTheDocument();
    expect(requestsTo(fetchMock, 'POST', '/api/auth/signup')).toHaveLength(0);
  });

  it('칸 오류가 아닌 실패(503 등)는 서버 문구를 그대로 알린다', async () => {
    stubFetch({
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'POST /api/auth/signup': () =>
        json(503, errorBody('TEMPORARILY_UNAVAILABLE', '잠시 후 다시 시도해 주세요')),
    });
    const user = userEvent.setup();
    renderPage();
    await fillForm(user);
    await user.click(screen.getByRole('button', { name: '가입하기' }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요'),
    );
  });
});
