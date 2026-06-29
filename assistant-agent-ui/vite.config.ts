import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  base: "/agent-console/",
  plugins: [react()],
  build: {
    outDir: "../assistant-agent-start/src/main/resources/static/agent-console",
    emptyOutDir: true
  },
  server: {
    proxy: {
      // 本地后端端口可用 VITE_API_TARGET 覆盖（local profile 跑在 8080）
      "/api": process.env.VITE_API_TARGET || "http://localhost:18080"
    }
  }
});
