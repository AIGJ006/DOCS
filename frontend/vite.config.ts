import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { themeHead } from './vite/themeHead';

// 개발 서버는 API·소셜 로그인 경로를 backend(8080)로 넘긴다. build 결과는 frontend/dist.
// backend/Dockerfile이 dist를 backend 정적 경로로 복사해 API와 같은 도메인에서 서빙한다.
const backend = 'http://localhost:8080';

export default defineConfig(({ mode }) => ({
  // 016: VITE_DARK_MODE가 false가 아니면 <head> 맨 앞에 theme-init.js·color-scheme 메타를 넣는다.
  plugins: [react(), themeHead(loadEnv(mode, process.cwd(), 'VITE_').VITE_DARK_MODE)],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/oauth2': backend,
      '/login/oauth2': backend,
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
}));
