import type { ReactNode } from 'react';
import { SessionProvider } from '../features/auth/SessionProvider';
import ThemeSystemFollower from '../features/theme/ThemeSystemFollower';
import SiteHeader from './SiteHeader';

/**
 * 모든 화면의 공통 틀: 로그인 상태(001 `SessionProvider`) + 공통 머리말 + 화면.
 * 화면마다의 폭·여백은 각 화면(`<main>`)이 정한다.
 * 016: 테마가 "시스템"이면 어느 화면에서나 기기 설정 변경을 따라간다(`ThemeSystemFollower`, 선택 칸은 설정 화면).
 */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <ThemeSystemFollower />
      <SiteHeader />
      {children}
    </SessionProvider>
  );
}
