import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, resetClientForTests } from '../../api/client';
import { ME, errorBody, json, stubFetch } from '../../test/fetchRoutes';
import { SessionProvider } from './SessionProvider';
import { useSession } from './useSession';

function Probe() {
  const { loading, me } = useSession();
  if (loading) return <p>loading</p>;
  return (
    <p>{me ? `${me.nickname}:${me.status}:${String(me.reagreementRequired)}` : 'anonymous'}</p>
  );
}

beforeEach(() => resetClientForTests());
afterEach(() => vi.unstubAllGlobals());

describe('SessionProvider', () => {
  it('GET /api/me 결과를 노출한다', async () => {
    stubFetch({ 'GET /api/me': () => json(200, { ...ME, reagreementRequired: true }) });
    render(
      <SessionProvider>
        <Probe />
      </SessionProvider>,
    );
    expect(await screen.findByText('김민서:ACTIVE:true')).toBeInTheDocument();
  });

  it('401이면 비로그인', async () => {
    stubFetch({ 'GET /api/me': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')) });
    render(
      <SessionProvider>
        <Probe />
      </SessionProvider>,
    );
    expect(await screen.findByText('anonymous')).toBeInTheDocument();
  });

  it('다른 요청이 401을 받으면 비로그인으로 바뀐다', async () => {
    stubFetch({
      'GET /api/me': () => json(200, ME),
      'GET /api/x': () => json(401, errorBody('LOGIN_REQUIRED', '로그인이 필요해요')),
    });
    render(
      <SessionProvider>
        <Probe />
      </SessionProvider>,
    );
    await screen.findByText('김민서:ACTIVE:false');
    await apiGet('/api/x').catch(() => undefined);
    expect(await screen.findByText('anonymous')).toBeInTheDocument();
  });
});
