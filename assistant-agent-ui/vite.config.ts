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
      "/api": "http://localhost:18080"
    }
  }
});
