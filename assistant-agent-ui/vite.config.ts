import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

// 去掉 vite 给模块脚本/预加载链接加的 crossorigin 属性。
// 同源部署时该属性会让浏览器对 JS/CSS 发 Origin 头、触发后端 CORS 校验，
// 静态资源没有 CORS 配置就会被拒 403、整页白屏。去掉即从源头规避（无需代理改头）。
function stripCrossorigin(): Plugin {
  return {
    name: "strip-crossorigin",
    transformIndexHtml(html) {
      return html.replace(/\s+crossorigin\b(=("|')?[^"' >]*("|')?)?/g, "");
    }
  };
}

export default defineConfig({
  base: "/agent-console/",
  plugins: [react(), stripCrossorigin()],
  build: {
    outDir: "../assistant-agent-start/src/main/resources/static/agent-console",
    emptyOutDir: true
  },
  server: {
    proxy: {
      // 默认指向本地 dev 后端(local profile = 8080)；docker(18080)等场景用 VITE_API_TARGET 覆盖
      "/api": process.env.VITE_API_TARGET || "http://localhost:8080"
    }
  }
});
