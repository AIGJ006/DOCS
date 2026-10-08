import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { MeSummary } from '../../api/me';
import { SessionContext } from '../auth/sessionContext';
import AdminRouteGate from './AdminRouteGate';

const ME = {
  memberId: 7,
  handle: 'kim',
  nickname: '김민서',
  role: 'USER',
  status: 'ACTIVE',
  provider: 'LOCAL',
  emailVerified: true,
} as MeSummary;

function LoginProbe() {
  const location = useLocation();
  return <p data-testid="login">{location.pathname + location.search}</p>;
}

function renderAt(path: string, me: MeSummary | null, loading = false) {
  render(
    <SessionContext.Provider value={{ loading, me, refresh: async () => me }}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/admin/*"
            element={
              <AdminRouteGate>
                <p>관리자 화면</p>
              </AdminRouteGate>
            }
          />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    </SessionContext.Provider>,
  );
}

/** 관리자 화면 라우트 가드 (004 T067, FR-044, US7). */
describe('AdminRouteGate', () => {
  it('비로그인이면 지금 주소를 returnTo로 붙여 로그인 화면으로 보낸다', () => {
    renderAt('/admin/reports?page=2', null);

    expect(screen.getByTestId('login')).toHaveTextContent(
      '/login?returnTo=%2Fadmin%2Freports%3Fpage%3D2',
    );
    expect(screen.queryByText('관리자 화면')).toBeNull();
  });

  it('로그인한 일반 회원에게는 공통 404 화면을 보인다 (관리자 화면이 있는지 드러나지 않음)', () => {
    renderAt('/admin/reports', ME);

    expect(screen.getByText('볼 수 없는 페이지예요')).toBeInTheDocument();
    expect(screen.queryByText('관리자 화면')).toBeNull();
  });

  it('관리자에게는 하위 화면을 그린다', () => {
    renderAt('/admin/reports', { ...ME, role: 'ADMIN' });

    expect(screen.getByText('관리자 화면')).toBeInTheDocument();
  });

  it('로그인 상태를 읽는 동안에는 아무것도 보이지 않는다', () => {
    renderAt('/admin/reports', null, true);

    expect(screen.queryByText('관리자 화면')).toBeNull();
    expect(screen.queryByTestId('login')).toBeNull();
    expect(screen.queryByText('볼 수 없는 페이지예요')).toBeNull();
  });
});
