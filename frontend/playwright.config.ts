import { defineConfig, devices } from "@playwright/test";

const frontendPort = process.env.GRIMO_VISUAL_FRONTEND_PORT || "5173";
const frontendUrl = `http://127.0.0.1:${frontendPort}`;

export default defineConfig({
  testDir: "./e2e",
  testIgnore: /.*\.fullstack\.spec\.ts/,
  fullyParallel: true,
  forbidOnly: true,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: frontendUrl,
    trace: "retain-on-failure",
  },
  webServer: {
    command: `npm run dev -- --port ${frontendPort}`,
    url: frontendUrl,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1366, height: 768 },
      },
    },
  ],
});
