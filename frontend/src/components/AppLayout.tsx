import type { ReactNode } from 'react';
import { SessionProvider } from '../features/auth/SessionProvider';
import SiteHeader from './SiteHeader';

/**
 * 모든 화면의 공통 틀: 로그인 상태(001 `SessionProvider`) + 공통 머리말 + 화면.
 * 화면마다의 폭·여백은 각 화면(`<main>`)이 정한다.
 */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <SiteHeader />
      {children}
    </SessionProvider>
  );
}
