import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../api/client';
import { SessionProvider } from '../features/auth/SessionProvider';
import { ReagreementGate } from '../features/auth/ReagreementGate';
import {
  CURRENT_AGREEMENTS,
  ME,
  errorBody,
  json,
  requestsTo,
  stubFetch,
} from '../test/fetchRoutes';
import ReagreementPage from './ReagreementPage';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname + location.search}</p>;
}

function renderApp(entry: string) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SessionProvider>
        <ReagreementGate>
          <Where />
          <Routes>
            <Route path="/reagree" element={<ReagreementPage />} />
            <Route path="/terms" element={<p>이용약관 본문</p>} />
            <Route path="/privacy" element={<p>처리방침 본문</p>} />
            <Route path="/settings" element={<p>설정 화면</p>} />
            <Route path="/" element={<p>홈 화면</p>} />
          </Routes>
        </ReagreementGate>
      </SessionProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => resetClientForTests());
afterEach(() => vi.unstubAllGlobals());

describe('재동의 화면·가드 (FR-012, SC-011)', () => {
  it('재동의가 필요하면 다른 화면은 /reagree로 보내고 돌아갈 곳을 남긴다', async () => {
    stubFetch({
      'GET /api/me': () => json(200, { ...ME, reagreementRequired: true }),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
    });
    renderApp('/settings?tab=1');
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent(
        '/reagree?returnTo=%2Fsettings%3Ftab%3D1',
      ),
    );
    expect(screen.queryByText('설정 화면')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '바뀐 약관에 동의해 주세요' })).toBeVisible();
  });

  it('약관·처리방침 화면은 재동의 전에도 볼 수 있다', async () => {
    stubFetch({ 'GET /api/me': () => json(200, { ...ME, reagreementRequired: true }) });
    renderApp('/terms');
    expect(await screen.findByText('이용약관 본문')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/terms');
  });

  it('재동의가 필요 없으면 그대로 둔다', async () => {
    stubFetch({ 'GET /api/me': () => json(200, ME) });
    renderApp('/settings');
    expect(await screen.findByText('설정 화면')).toBeInTheDocument();
  });

  it('바뀐 문서 링크·버전을 보이고 두 동의를 모두 체크해야 보낸다 → PUT 뒤 돌아갈 곳으로', async () => {
    const fetchMock = stubFetch({
      'GET /api/me': () => json(200, { ...ME, reagreementRequired: true }),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'PUT /api/me/agreements': () => new Response(null, { status: 204 }),
    });
    const user = userEvent.setup();
    renderApp('/reagree?returnTo=%2Fsettings');
    expect(await screen.findByRole('link', { name: '이용약관 보기' })).toHaveAttribute(
      'href',
      '/terms',
    );
    expect(screen.getByRole('link', { name: '개인정보 처리방침 보기' })).toHaveAttribute(
      'href',
      '/privacy',
    );
    expect(screen.getAllByText('2026-10-07').length).toBeGreaterThan(0);

    const submit = screen.getByRole('button', { name: '동의하고 계속하기' });
    await user.click(submit);
    expect(screen.getByRole('alert')).toHaveTextContent('두 문서에 모두 동의해 주세요');
    expect(requestsTo(fetchMock, 'PUT', '/api/me/agreements')).toHaveLength(0);

    fetchMock.mockImplementation(async (input, init) => {
      const path = String(input).split('?')[0];
      if (path === '/api/me') return json(200, ME);
      if (path === '/api/agreements/current') return json(200, CURRENT_AGREEMENTS);
      if (path === '/api/me/agreements' && init?.method === 'PUT') {
        return new Response(null, { status: 204 });
      }
      return json(404, errorBody('NOT_FOUND', '없음'));
    });
    await user.click(screen.getByLabelText('바뀐 이용약관에 동의해요 (필수)'));
    await user.click(screen.getByLabelText('바뀐 개인정보 처리방침에 동의해요 (필수)'));
    await user.click(submit);

    await waitFor(() => expect(screen.getByText('설정 화면')).toBeInTheDocument());
    const [call] = requestsTo(fetchMock, 'PUT', '/api/me/agreements');
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      termsVersion: '2026-10-07',
      privacyVersion: '2026-10-07',
    });
  });

  it('외부 주소 returnTo는 무시하고 홈으로', async () => {
    let reagreed = false;
    stubFetch({
      'GET /api/me': () => json(200, { ...ME, reagreementRequired: !reagreed }),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'PUT /api/me/agreements': () => {
        reagreed = true;
        return new Response(null, { status: 204 });
      },
    });
    const user = userEvent.setup();
    renderApp('/reagree?returnTo=%2F%2Fevil.com');
    await user.click(await screen.findByLabelText('바뀐 이용약관에 동의해요 (필수)'));
    await user.click(screen.getByLabelText('바뀐 개인정보 처리방침에 동의해요 (필수)'));
    await user.click(screen.getByRole('button', { name: '동의하고 계속하기' }));
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  });

  it('버전이 바뀌었으면(AGREEMENT_VERSION_MISMATCH) 알리고 새 버전을 다시 읽는다', async () => {
    let version = '2026-10-07';
    const fetchMock = stubFetch({
      'GET /api/me': () => json(200, { ...ME, reagreementRequired: true }),
      'GET /api/agreements/current': () =>
        json(200, {
          terms: { ...CURRENT_AGREEMENTS.terms, version },
          privacy: { ...CURRENT_AGREEMENTS.privacy, version },
        }),
      'PUT /api/me/agreements': () => {
        version = '2026-12-01';
        return json(
          400,
          errorBody('VALIDATION_FAILED', '입력한 내용을 확인해 주세요', [
            {
              field: 'agreements',
              code: 'AGREEMENT_VERSION_MISMATCH',
              message: '약관이 바뀌었어요. 새로 고친 뒤 다시 동의해 주세요',
            },
          ]),
        );
      },
    });
    const user = userEvent.setup();
    renderApp('/reagree');
    await user.click(await screen.findByLabelText('바뀐 이용약관에 동의해요 (필수)'));
    await user.click(screen.getByLabelText('바뀐 개인정보 처리방침에 동의해요 (필수)'));
    await user.click(screen.getByRole('button', { name: '동의하고 계속하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('약관이 바뀌었어요');
    await waitFor(() => expect(screen.getAllByText('2026-12-01').length).toBeGreaterThan(0));
    expect(requestsTo(fetchMock, 'GET', '/api/agreements/current').length).toBeGreaterThan(1);
  });

  it('다른 요청이 403 REAGREEMENT_REQUIRED를 받으면 재동의 화면으로 간다', async () => {
    const { apiGet } = await import('../api/client');
    stubFetch({
      'GET /api/me': () => json(200, ME),
      'GET /api/agreements/current': () => json(200, CURRENT_AGREEMENTS),
      'GET /api/me/settings': () =>
        json(403, errorBody('REAGREEMENT_REQUIRED', '바뀐 약관에 동의한 뒤 이용할 수 있어요')),
    });
    renderApp('/settings');
    expect(await screen.findByText('설정 화면')).toBeInTheDocument();
    await apiGet('/api/me/settings').catch(() => undefined);
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent('/reagree?returnTo=%2Fsettings'),
    );
  });
});
