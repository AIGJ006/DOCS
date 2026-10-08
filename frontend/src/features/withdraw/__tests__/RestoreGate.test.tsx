import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { MeSummary } from '../../../api/me';
import { SessionContext } from '../../auth/sessionContext';
import { ME } from '../../../test/fetchRoutes';
import { RestoreGate } from '../RestoreGate';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}</p>;
}

function renderAt(path: string, me: MeSummary | null) {
  return render(
    <SessionContext.Provider value={{ loading: false, me, refresh: async () => me }}>
      <MemoryRouter initialEntries={[path]}>
        <RestoreGate>
          <Where />
          <Routes>
            <Route path="*" element={<p>화면</p>} />
          </Routes>
        </RestoreGate>
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

const WITHDRAWN = {
  ...ME,
  status: 'WITHDRAWN',
  restoreDeadline: '2026-11-07T06:20:00Z',
  restoreExpired: false,
} as MeSummary;

describe('RestoreGate (015 T033, FR-017)', () => {
  it.each(['/write/new', '/settings', '/', '/@kim755030', '/manage/posts', '/settings/withdraw'])(
    '탈퇴 유예면 %s → /account/restore',
    (path) => {
      renderAt(path, WITHDRAWN);
      expect(screen.getByTestId('where')).toHaveTextContent('/account/restore');
    },
  );

  it.each(['/terms', '/privacy', '/reset-password', '/forgot-password', '/account/restore'])(
    '허용 경로 %s는 그대로',
    (path) => {
      renderAt(path, WITHDRAWN);
      expect(screen.getByTestId('where')).toHaveTextContent(path);
    },
  );

  it('활동 중·비로그인은 막지 않는다', () => {
    renderAt('/write/new', ME as MeSummary);
    expect(screen.getByTestId('where')).toHaveTextContent('/write/new');
  });

  it('비로그인도 막지 않는다', () => {
    renderAt('/settings', null);
    expect(screen.getByTestId('where')).toHaveTextContent('/settings');
  });
});
