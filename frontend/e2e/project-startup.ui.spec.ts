import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const projectA = {
  id: "01HZPROJECT001",
  name: "grimoAPP",
  description: "本機 AI 開發工作台",
  projectPath: "/Users/samzhu/workspace/github-samzhu/grimoAPP",
  workflowRecipeId: "web-service-development",
  workflowRecipeName: "Web 服務開發",
  status: "ACTIVE",
  createdAt: "2026-06-07T00:00:00Z",
  updatedAt: "2026-06-07T00:00:00Z",
  workflowRoles: [],
};

const projectB = {
  id: "01HZPROJECT002",
  name: "skills-hub",
  description: "Reusable skills workspace",
  projectPath: "/Users/samzhu/workspace/github-samzhu/skills-hub",
  workflowRecipeId: "web-service-development",
  workflowRecipeName: "Web 服務開發",
  status: "ACTIVE",
  createdAt: "2026-06-07T01:00:00Z",
  updatedAt: "2026-06-07T01:00:00Z",
  workflowRoles: [],
};

const startupViewports = [
  { name: "desktop-1366", width: 1366, height: 768 },
  { name: "desktop-1440", width: 1440, height: 900 },
  { name: "mobile-390", width: 390, height: 844 },
  { name: "tablet-820", width: 820, height: 1180 },
] as const;

async function routeProjectList(page: Page, projects: unknown[]) {
  await page.route("**/api/projects", async (route) => {
    await route.fulfill({ json: { content: projects } });
  });
}

test("AC-S010-1: first-run Project gate does not show fake Project or fixture tasks", async ({
  page,
}) => {
  await routeProjectList(page, []);
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
  await expect(page.getByText("新手引導")).toBeVisible();
  await expect(page.getByText("把本機 repo 變成 Grimo Project")).toBeVisible();
  await expect(page.getByText("Project Setup Copilot")).toBeVisible();
  await expect(page.getByText("用目前 repo 建立 Project")).toBeVisible();
  await expect(page.getByRole("button", { name: "建立新 Project" })).toBeVisible();
  await expect(page.locator(".primary-button")).toHaveCount(1);
  await expect(page.locator(".project-picker")).toHaveCount(0);
  await expect(page.locator(".project-selection-card")).toHaveCount(0);
  await expect(page.getByText("grimo/web")).toHaveCount(0);
  await expect(page.getByText("執行前設定與本機能力檢查")).toHaveCount(0);
});

test("AC-S011-1: first-run Project setup panel is inside Main Content Area", async ({
  page,
}) => {
  await routeProjectList(page, []);
  await page.goto("/");

  const setupPanel = page.locator(
    ".main-content-area .project-selection-gate .project-setup-copilot",
  );
  await expect(setupPanel).toBeVisible();
  await expect(setupPanel.getByText("新手引導")).toBeVisible();
  await expect(setupPanel.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
  await expect(setupPanel.getByText("把本機 repo 變成 Grimo Project")).toBeVisible();
  await expect(setupPanel.getByText("建立前先把 repo、workflow 和第一批 Task context 對齊。")).toBeVisible();
  await expect(setupPanel.getByRole("button", { name: "建立新 Project" })).toBeVisible();
  await expect(page.locator(".project-picker")).toHaveCount(0);
  await expect(page.locator(".project-selection-card")).toHaveCount(0);
  await expect(page.getByText("grimo/web")).toHaveCount(0);
  await expect(page.getByText("執行前設定與本機能力檢查")).toHaveCount(0);
});

test("AC-S011-3: closed session starts on Project list and does not load tasks", async ({
  page,
}) => {
  const taskRequests: string[] = [];
  await routeProjectList(page, [projectA, projectB]);
  await page.route("**/api/projects/*/tasks", async (route) => {
    taskRequests.push(route.request().url());
    await route.fulfill({ json: { content: [] } });
  });
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: true }),
    );
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.locator(".project-context")).toContainText("尚未開啟 Project");
  await expect(page.getByRole("button", { name: /grimoAPP/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /skills-hub/ })).toBeVisible();
  await expect(page.locator(".project-selection-gate")).toHaveCount(0);
  expect(taskRequests).toHaveLength(0);
});

test("AC-S011-4: Projects and Workflow remain available without active Project", async ({
  page,
}) => {
  await routeProjectList(page, [projectA, projectB]);
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: true }),
    );
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await page.getByLabel("展開主選單").click();
  await page.getByRole("button", { name: "專案", exact: true }).click();
  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.locator(".project-context")).toContainText("尚未開啟 Project");

  await page.goto("/");
  await page.getByLabel("展開主選單").click();
  await page.getByRole("button", { name: "Workflow" }).click();
  await expect(page.getByRole("heading", { name: "Workflow 設計" })).toBeVisible();
  await expect(page.locator(".project-context")).toContainText("尚未開啟 Project");
});

test("AC-S011-6: app shell uses canonical layout selectors", async ({ page }) => {
  await routeProjectList(page, []);
  await page.goto("/");

  await expect(page.locator(".app-header")).toBeVisible();
  await expect(page.locator(".main-content-area")).toBeVisible();

  await page.getByLabel("展開主選單").click();

  await expect(page.locator(".side-navigation")).toBeVisible();
});

test("AC-S011-7: Project list failure uses an error setup panel", async ({ page }) => {
  await page.route("**/api/projects", async (route) => {
    await route.fulfill({ status: 500, json: { error: "Project 載入失敗" } });
  });

  await page.goto("/");

  const setupPanel = page.locator(".main-content-area .project-setup-error");
  await expect(setupPanel.getByRole("heading", { name: "無法載入 Project context" })).toBeVisible();
  await expect(setupPanel.getByText("Project 載入失敗")).toBeVisible();
  await expect(setupPanel.getByRole("button", { name: "重試" })).toBeVisible();
  await expect(setupPanel.getByRole("heading", { name: "建立 Project 工作台" })).toHaveCount(0);
});

test("AC-S010-2: open session restores the matching Project and calls only its task API", async ({
  page,
}) => {
  const taskRequests: string[] = [];
  await routeProjectList(page, [projectA, projectB]);
  await page.route("**/api/projects/*/tasks", async (route) => {
    taskRequests.push(route.request().url());
    await route.fulfill({ json: { content: [] } });
  });

  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: false }),
    );
  });

  await page.goto("/");

  await expect(page.locator(".project-context")).toContainText("grimoAPP");
  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
  await expect.poll(() => taskRequests).toEqual([
    expect.stringContaining("/api/projects/01HZPROJECT001/tasks"),
  ]);
  expect(taskRequests.some((url) => url.includes(projectB.id))).toBe(false);
});

test("AC-S010-5: stale Project session shows selection gate and does not auto-select another Project", async ({
  page,
}) => {
  const taskRequests: string[] = [];
  await routeProjectList(page, [projectA, projectB]);
  await page.route("**/api/projects/*/tasks", async (route) => {
    taskRequests.push(route.request().url());
    await route.fulfill({ json: { content: [] } });
  });

  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "missing-project", isClosed: false }),
    );
  });

  await page.goto("/");

  await expect(
    page.getByText("上次開啟的 Project 已不存在或無法載入。你可以建立新 Project。"),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
  await expect(page.locator(".project-picker")).toHaveCount(0);
  await expect(page.locator(".project-selection-card")).toHaveCount(0);
  await expect(page.locator(".project-context")).toContainText("尚未開啟 Project");
  expect(taskRequests).toHaveLength(0);
});

test("AC-S010-5: Project list failure shows retry without fixture fallback", async ({
  page,
}) => {
  let projectRequests = 0;
  await page.route("**/api/projects", async (route) => {
    projectRequests += 1;
    if (projectRequests <= 2) {
      await route.fulfill({ status: 500, json: { error: "Project 載入失敗" } });
      return;
    }
    await route.fulfill({ json: { content: [] } });
  });

  await page.goto("/");

  await expect(page.getByText("Project 載入失敗")).toBeVisible();
  await expect(page.getByRole("button", { name: "重試" })).toBeVisible();
  await expect(page.getByText("grimo/web")).toHaveCount(0);
  await expect(page.getByText("執行前設定與本機能力檢查")).toHaveCount(0);

  await page.getByRole("button", { name: "重試" }).click();

  await expect(page.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
  await expect(page.getByRole("button", { name: "建立新 Project" })).toBeVisible();
});

test("AC-S010-3: closed session opens Project list", async ({
  page,
}) => {
  const taskRequests: string[] = [];
  await routeProjectList(page, [projectA, projectB]);
  await page.route("**/api/projects/*/tasks", async (route) => {
    taskRequests.push(route.request().url());
    await route.fulfill({ json: { content: [] } });
  });

  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: true }),
    );
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.getByRole("button", { name: /grimoAPP/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /skills-hub/ })).toBeVisible();
  await expect(page.locator(".project-selection-gate")).toHaveCount(0);
  await expect(page.locator(".project-context")).toContainText("尚未開啟 Project");
  expect(taskRequests).toHaveLength(0);
});

test("AC-S010-6: no active Project gates Task, attention, and Chat surfaces", async ({
  page,
}) => {
  await routeProjectList(page, [projectA, projectB]);
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: true }),
    );
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.getByText("執行前設定與本機能力檢查")).toHaveCount(0);

  await page.getByLabel("展開主選單").click();
  await page.getByRole("button", { name: "待處理" }).click();
  await expect(page.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
  await expect(page.getByText("優先處理")).toHaveCount(0);
  await expect(page.getByText("GRM-188")).toHaveCount(0);

  await page.getByLabel("展開主選單").click();
  await page.getByRole("button", { name: "Chat" }).click();
  await expect(page.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "工作形成 Chat" })).toHaveCount(0);
  await expect(page.getByText("把討論轉成 Grimo Task")).toHaveCount(0);
});

test("AC-S010-3: Close Project clears session without deleting Project", async ({
  page,
}) => {
  let deleteRequests = 0;
  await routeProjectList(page, [projectA, projectB]);
  await page.route("**/api/projects/*/tasks", async (route) => {
    await route.fulfill({ json: { content: [] } });
  });
  await page.route("**/api/projects/*", async (route) => {
    if (route.request().method() === "DELETE") {
      deleteRequests += 1;
    }
    await route.fallback();
  });

  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: false }),
    );
  });

  await page.goto("/");
  await page.getByRole("button", { name: /目前專案.*grimoAPP/ }).click();
  await page.getByRole("button", { name: "Close Project" }).click();

  await expect(page.locator(".project-context")).toContainText("尚未開啟 Project");
  await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
  await expect(page.getByRole("button", { name: /grimoAPP/ })).toBeVisible();
  await expect(page.locator(".project-selection-gate")).toHaveCount(0);
  await expect
    .poll(() =>
      page.evaluate(() => JSON.parse(window.localStorage.getItem("grimo.projectSession") || "{}")),
    )
    .toEqual({ lastActiveProjectId: "01HZPROJECT001", isClosed: true });
  expect(deleteRequests).toBe(0);
});

test("AC-S010-4: Project Switcher switches Project and loads selected Project tasks", async ({
  page,
}) => {
  const taskRequests: string[] = [];
  await routeProjectList(page, [projectA, projectB]);
  await page.route("**/api/projects/*/tasks", async (route) => {
    taskRequests.push(route.request().url());
    await route.fulfill({ json: { content: [] } });
  });

  await page.addInitScript(() => {
    window.localStorage.setItem(
      "grimo.projectSession",
      JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: false }),
    );
  });

  await page.goto("/");
  await expect(page.locator(".project-context")).toContainText("grimoAPP");

  await page.getByRole("button", { name: /目前專案.*grimoAPP/ }).click();
  await page.getByRole("button", { name: /skills-hub/ }).click();

  await expect(page.locator(".project-context")).toContainText("skills-hub");
  await expect(page.getByRole("heading", { name: "任務工作台" })).toBeVisible();
  await expect.poll(() => taskRequests).toContainEqual(
    expect.stringContaining("/api/projects/01HZPROJECT002/tasks"),
  );
  await expect
    .poll(() =>
      page.evaluate(() => JSON.parse(window.localStorage.getItem("grimo.projectSession") || "{}")),
    )
    .toEqual({ lastActiveProjectId: "01HZPROJECT002", isClosed: false });
});

test.describe("S010 startup visual evidence", () => {
  for (const viewport of startupViewports) {
    test(`Project selection gate ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await routeProjectList(page, []);

      await page.goto("/");

      await expect(page.getByRole("heading", { name: "建立 Project 工作台" })).toBeVisible();
      await expect(page.getByRole("button", { name: /grimoAPP/ })).toHaveCount(0);
      await expect(page).toHaveScreenshot(`project-selection-gate-${viewport.name}.png`, {
        fullPage: true,
      });
    });

    test(`Project Switcher ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await routeProjectList(page, [projectA, projectB]);
      await page.route("**/api/projects/*/tasks", async (route) => {
        await route.fulfill({ json: { content: [] } });
      });
      await page.addInitScript(() => {
        window.localStorage.setItem(
          "grimo.projectSession",
          JSON.stringify({ lastActiveProjectId: "01HZPROJECT001", isClosed: false }),
        );
      });

      await page.goto("/");
      await page.getByRole("button", { name: /目前專案.*grimoAPP/ }).click();

      await expect(page.getByText("Project Switcher")).toHaveCount(0);
      await expect(page.getByRole("button", { name: /skills-hub/ })).toBeVisible();
      await expect(page.getByRole("button", { name: "Close Project" })).toBeVisible();
      await expect(page).toHaveScreenshot(`project-switcher-${viewport.name}.png`, {
        fullPage: true,
      });
    });
  }
});
