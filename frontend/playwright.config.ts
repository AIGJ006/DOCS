import { defineConfig, devices } from '@playwright/test';

// 브라우저 종단 간 시험 (002 research B-13): 오프라인 전환, 두 탭 충돌, XSS 알림창 0회, 375px 레이아웃.
// 실행 전에 docker compose로 app(8080)을 띄운다. 브라우저 설치는 `npx playwright install`.
// 이미 설치된 Chromium을 쓰려면 E2E_CHROMIUM_PATH에 실행 파일 경로를 준다.
const executablePath = process.env.E2E_CHROMIUM_PATH || undefined;
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
    launchOptions: executablePath ? { executablePath } : {},
  },
  projects: [
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
    {
      name: 'mobile',
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 }, isMobile: true },
    },
  ],
});
