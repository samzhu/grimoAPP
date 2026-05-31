import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: /.*\.fullstack\.spec\.ts/,
  forbidOnly: true,
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  workers: 1,
  use: {
    baseURL: "http://127.0.0.1:5173",
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command:
        "GRIMO_DATASOURCE_URL='jdbc:sqlite:file:grimo-fullstack?mode=memory&cache=shared' ../backend/gradlew -p ../backend bootRun",
      url: "http://127.0.0.1:8080/api/workflow-recipes",
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: "npm run dev -- --port 5173",
      url: "http://127.0.0.1:5173",
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
