import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "GRIMO_");
  const apiProxyTarget = env.GRIMO_API_PROXY_TARGET || "http://127.0.0.1:8080";

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": apiProxyTarget,
      },
    },
  };
});
