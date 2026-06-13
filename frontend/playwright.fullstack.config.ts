import { defineConfig, devices } from "@playwright/test";

const backendPort = process.env.GRIMO_FULLSTACK_BACKEND_PORT || "18080";
const frontendPort = process.env.GRIMO_FULLSTACK_FRONTEND_PORT || "5174";
const backendUrl = `http://127.0.0.1:${backendPort}`;
const frontendUrl = `http://127.0.0.1:${frontendPort}`;

export default defineConfig({
  testDir: "./e2e",
  testMatch: /.*\.fullstack\.spec\.ts/,
  forbidOnly: true,
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  workers: 1,
  use: {
    baseURL: frontendUrl,
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command:
        `JAVA_TOOL_OPTIONS='-Duser.home=../temp/grimo-fullstack-home' SERVER_PORT='${backendPort}' GRIMO_DATASOURCE_URL='jdbc:sqlite:file:grimo-fullstack?mode=memory&cache=shared' ../backend/gradlew -p ../backend bootRun`,
      url: `${backendUrl}/api/workflow-recipes`,
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: `GRIMO_API_PROXY_TARGET='${backendUrl}' npm run dev -- --port ${frontendPort}`,
      url: frontendUrl,
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
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
