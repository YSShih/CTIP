import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// server 設定為 docs/spec/12-frontend.md §12.7 的強制契約:
// host port 與容器 port 必須一致(5173),否則 HMR 靜默失效。
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    watch: { usePolling: true },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    css: false,
    coverage: {
      // 14 §14.6:features/** 與 components/** 行覆蓋 ≥ 70%,v8 provider,generated 排除
      provider: 'v8',
      include: ['src/components/**', 'src/features/**'],
      exclude: ['**/*.test.*', 'src/api/generated/**'],
      thresholds: { lines: 70 },
    },
  },
});
