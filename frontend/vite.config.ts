import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// server 設定為 docs/spec/12-frontend.md §12.7 的強制契約:
// host port 與容器 port 必須一致(5173),否則 HMR 靜默失效。
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    watch: { usePolling: true },
  },
});
