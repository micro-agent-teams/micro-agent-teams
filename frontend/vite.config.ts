import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    host: "127.0.0.1",
    // Reached only through the host nginx at cheese-prod-client.119net.ghg.org.cn
    // (never directly), so accept that Host header and any other proxied one.
    allowedHosts: true,
    // The page loads from the domain (port 80/443), not :5173 — without this
    // the HMR client tries to open a websocket back to the wrong origin/port.
    // HMR breaking is tolerable (per the ask); a real client port setup isn't
    // known yet, so this just points it at the same host, default ports.
    hmr: {
      host: "cheese-prod-client.119net.ghg.org.cn",
    },
    proxy: {
      // Mirrors production nginx: /api/* -> the auth backend with the /api
      // prefix stripped (it mounts routes at /users/... directly), same
      // origin as the page so the httpOnly REFRESH_TOKEN cookie sticks.
      "/api": {
        target: process.env.AUTH_BACKEND_URL ?? "http://127.0.0.1:8091",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
        // cheese-auth sets REFRESH_TOKEN with Path=/users/auth, but the browser
        // sees our requests under /api/users/auth/... — the paths don't match so
        // the cookie is never sent back on refresh and the session dies after
        // ~15 min. Rewrite the cookie path to / so it rides every /api request.
        cookiePathRewrite: "/",
      },
      "/nt": {
        target: process.env.NT_BACKEND_URL ?? "http://127.0.0.1:8199",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/nt/, ""),
      },
    },
  },
});
