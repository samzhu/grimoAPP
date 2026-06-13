import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

const workflowRecipes = {
  content: [
    {
      id: "web-service-development",
      name: "Web 服務開發",
      description: "從需求到 release 的 Web 服務開發工作流",
      category: "coding",
      steps: [
        { id: "discuss", name: "Discuss", taskState: "DEFINING" },
        { id: "dev", name: "Dev", taskState: "RUNNING" },
      ],
      roles: [
        {
          id: "product-manager",
          name: "Product Manager",
          description: "釐清產品目標與 acceptance。",
          primarySteps: ["Discuss"],
        },
      ],
      qualityLoopSummary: "Review -> Rating -> Fix until quality_score > 9",
    },
  ],
};

const createdProject = {
  id: "01HZ7Y2P8M9QK",
  name: "grimoAPP",
  description: "本機 AI 開發工作台",
  projectPath: "/tmp/grimo-s003-repo",
  workflowRecipeId: "web-service-development",
  workflowRecipeName: "Web 服務開發",
  status: "ACTIVE",
  createdAt: "2026-06-01T00:00:00Z",
  updatedAt: "2026-06-01T00:00:00Z",
  workflowRoles: [],
};

const selectedNativeProjectPath = "/Users/samzhu/workspace/github-samzhu/grimoAPP";
const defaultRoot = "/Users/samzhu/.grimo/projects";
const homeRoot = "/Users/samzhu";
const workspaceRoot = "/Users/samzhu/workspace/github-samzhu";
const selectedFolderPath = `${defaultRoot}/grimoAPP`;
const createdFolderPath = `${workspaceRoot}/grimoAPP`;

type ProjectApiMockOptions = {
  localDirectoryError?: string;
  defaultRootPath?: string;
};

async function mockProjectApis(page: Page, options: ProjectApiMockOptions = {}) {
  const createBodies: unknown[] = [];
  const localDirectoryRequests: string[] = [];
  const createLocalDirectoryBodies: unknown[] = [];
  const nativeDialogBodies: unknown[] = [];
  const browserDefaultRoot = options.defaultRootPath ?? defaultRoot;
  const browserSelectedFolderPath = `${browserDefaultRoot}/grimoAPP`;

  await page.route("**/api/projects", async (route) => {
    if (route.request().method() === "POST") {
      createBodies.push(route.request().postDataJSON());
      await route.fulfill({ json: createdProject });
      return;
    }
    await route.fulfill({ json: { content: [] } });
  });
  await page.route("**/api/workflow-recipes", async (route) => {
    await route.fulfill({ json: workflowRecipes });
  });
  await page.route("**/api/local-directories**", async (route) => {
    localDirectoryRequests.push(route.request().url());
    if (route.request().method() === "POST") {
      createLocalDirectoryBodies.push(route.request().postDataJSON());
      await route.fulfill({
        status: 201,
        json: { name: "grimoAPP", path: createdFolderPath },
      });
      return;
    }
    if (options.localDirectoryError) {
      await route.fulfill({
        status: 400,
        json: { error: options.localDirectoryError },
      });
      return;
    }
    const url = new URL(route.request().url());
    const path = url.searchParams.get("path");
    const location = url.searchParams.get("location");
    const listingPath = location === "home"
      ? homeRoot
      : location === "default" || !path
        ? browserDefaultRoot
        : path;
    const parentPath = listingPath === browserDefaultRoot
      ? browserDefaultRoot.replace(/\/[^/]+$/, "")
      : listingPath === homeRoot
        ? "/Users"
        : browserDefaultRoot;
    const directories = listingPath === browserDefaultRoot
      ? [
          { name: "AlphaTool", path: `${browserDefaultRoot}/AlphaTool` },
          { name: "grimoAPP", path: browserSelectedFolderPath },
        ]
      : listingPath === homeRoot
        ? [{ name: "github-samzhu", path: workspaceRoot }]
        : listingPath === workspaceRoot
          ? [{ name: "skills-hub", path: `${workspaceRoot}/skills-hub` }]
          : [];
    await route.fulfill({
      json: {
        path: listingPath,
        parentPath,
        directories,
      },
    });
  });
  await page.route("**/api/native-folder-dialogs/project-path", async (route) => {
    nativeDialogBodies.push(route.request().postDataJSON());
    await route.fulfill({
      status: 500,
      json: { error: "S014 不應呼叫 native folder dialog" },
    });
  });
  await page.route(`**/api/projects/${createdProject.id}/tasks`, async (route) => {
    await route.fulfill({ json: { content: [] } });
  });

  return {
    createBodies,
    createLocalDirectoryBodies,
    nativeDialogBodies,
    getLocalDirectoryRequests: () => localDirectoryRequests.length,
    localDirectoryRequests,
  };
}

async function openProjectsView(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "展開主選單" }).click();
  await page.getByRole("button", { name: "專案" }).click();
}

test("AC-S003-1: list view hides Project create fields before user starts creation", async ({ page }) => {
  await mockProjectApis(page);

  await test.step("Given the user opens Project management", async () => {
    await openProjectsView(page);
  });

  await test.step("Then the list view is the first screen", async () => {
    await expect(page.getByRole("heading", { name: "專案管理" })).toBeVisible();
    await expect(page.getByText("Project list")).toBeVisible();
    await expect(page.getByText("尚未建立專案")).toBeVisible();
    await expect(page.getByRole("button", { name: "新增專案" })).toBeVisible();
    await expect(page.getByLabel("專案名稱")).toHaveCount(0);
    await expect(page.getByLabel("專案描述")).toHaveCount(0);
    await expect(page.getByLabel("專案路徑")).toHaveCount(0);
  });
});

test("AC-S003-2: create view can return to Project list without clearing loaded projects", async ({ page }) => {
  await mockProjectApis(page);
  await openProjectsView(page);

  await test.step('When the user clicks "新增專案"', async () => {
    await page.getByRole("button", { name: "新增專案" }).click();
  });

  await test.step("Then the Project Creation Page is visible", async () => {
    await expect(page.getByRole("heading", { name: "新增專案", level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: "返回列表" })).toBeVisible();
    await expect(page.getByLabel("專案名稱")).toBeVisible();
    await expect(page.getByLabel("專案描述")).toBeVisible();
    await expect(page.getByLabel("專案路徑")).toBeVisible();
    await expect(page.getByLabel("專案工作流")).toBeVisible();
  });

  await test.step("And returning keeps the loaded list state", async () => {
    await page.getByRole("button", { name: "返回列表" }).click();
    await expect(page.getByText("Project list")).toBeVisible();
    await expect(page.getByText("尚未建立專案")).toBeVisible();
  });
});

test("AC-S003-7: create view submits projectPath only and does not open directory browser", async ({ page }) => {
  const api = await mockProjectApis(page);
  await openProjectsView(page);
  await page.getByRole("button", { name: "新增專案" }).click();

  await test.step("Then the projectPath field stays a text input", async () => {
    await expect(page.getByLabel("專案路徑")).toBeVisible();
    await expect(page.getByText("未填會使用 Grimo 預設路徑")).toBeVisible();
    await expect(page.getByRole("button", { name: "選擇資料夾" })).toBeVisible();
    await expect(page.getByText("上層")).toHaveCount(0);
    await expect(page.getByText("選取此資料夾")).toHaveCount(0);
    await expect(page.getByText("使用此資料夾")).toHaveCount(0);
    await expect(page.locator(".directory-browser")).toHaveCount(0);
  });

  await test.step("When the user submits a Project", async () => {
    await page.getByLabel("專案名稱").fill("grimoAPP");
    await page.getByLabel("專案描述").fill("本機 AI 開發工作台");
    await page.getByLabel("專案路徑").fill("/tmp/grimo-s003-repo");
    await page.getByLabel("專案工作流").selectOption("web-service-development");
    await page.getByRole("button", { name: "建立專案" }).click();
  });

  await test.step("Then the request body contains projectPath only", async () => {
    expect(api.getLocalDirectoryRequests()).toBe(0);
    expect(api.createBodies).toHaveLength(1);
    expect(api.createBodies[0]).toMatchObject({
      name: "grimoAPP",
      description: "本機 AI 開發工作台",
      projectPath: "/tmp/grimo-s003-repo",
      workflowRecipeId: "web-service-development",
    });
    expect(api.createBodies[0]).not.toHaveProperty("workspacePath");
    expect(api.createBodies[0]).not.toHaveProperty("projectPathSource");
    expect(api.createBodies[0]).not.toHaveProperty("browserProjectPathKey");
  });
});

test("AC-S014-1/2/7: create view opens Grimo folder browser modal instead of native dialog", async ({ page }) => {
  const api = await mockProjectApis(page);
  await openProjectsView(page);
  await page.getByRole("button", { name: "新增專案" }).click();

  await test.step('When the user clicks "選擇資料夾"', async () => {
    await page.getByRole("button", { name: "選擇資料夾" }).click();
  });

  await test.step("Then the modal shows default root and does not call native dialog", async () => {
    await expect(page.getByRole("dialog", { name: "選擇 Project 資料夾" })).toBeVisible();
    await expect(page.locator(".folder-current-path")).toHaveText(defaultRoot);
    await expect(page.getByRole("button", { name: "使用此資料夾" })).toBeDisabled();
    await expect(page.getByRole("button", { name: "回家目錄" })).toBeVisible();
    await expect(page.getByRole("button", { name: "回 Grimo 預設位置" })).toBeVisible();
    await expect(page.getByRole("button", { name: /AlphaTool/ })).toBeVisible();
    expect(api.getLocalDirectoryRequests()).toBe(1);
    expect(api.nativeDialogBodies).toHaveLength(0);
    expect(api.createBodies).toHaveLength(0);
  });
});

test("AC-S014-3/4: folder browser navigates and fills projectPath only", async ({ page }) => {
  const api = await mockProjectApis(page);
  await openProjectsView(page);
  await page.getByRole("button", { name: "新增專案" }).click();

  await test.step("Given the folder browser is open", async () => {
    await page.getByRole("button", { name: "選擇資料夾" }).click();
  });

  await test.step("When the user enters a child folder and goes back to parent", async () => {
    await page.getByRole("button", { name: /grimoAPP/ }).click();
    await expect(page.getByText(selectedFolderPath)).toBeVisible();
    await page.getByRole("button", { name: "上層" }).click();
    await expect(page.locator(".folder-current-path")).toHaveText(defaultRoot);
  });

  await test.step("When the user uses a selectable child folder", async () => {
    await page.getByRole("button", { name: /grimoAPP/ }).click();
    await page.getByRole("button", { name: "使用此資料夾" }).click();
  });

  await test.step("Then only the Project Path input is filled", async () => {
    await expect(page.getByRole("dialog", { name: "選擇 Project 資料夾" })).toHaveCount(0);
    await expect(page.getByLabel("專案路徑")).toHaveValue(selectedFolderPath);
    expect(api.createBodies).toHaveLength(0);
    expect(api.nativeDialogBodies).toHaveLength(0);
  });
});

test("AC-S014-5: folder browser creates a new folder and fills projectPath only", async ({ page }) => {
  const api = await mockProjectApis(page);
  await openProjectsView(page);
  await page.getByRole("button", { name: "新增專案" }).click();

  await test.step("When the user creates a new folder from the current browser location", async () => {
    await page.getByRole("button", { name: "選擇資料夾" }).click();
    await page.getByRole("button", { name: "回家目錄" }).click();
    await page.getByRole("button", { name: "github-samzhu" }).click();
    await page.getByRole("button", { name: "建立新資料夾" }).click();
    await page.getByLabel("資料夾名稱").fill("grimoAPP");
    await page.getByRole("button", { name: "建立並使用" }).click();
  });

  await test.step("Then the created folder path fills projectPath without creating Project", async () => {
    expect(api.createLocalDirectoryBodies).toEqual([{ parentPath: workspaceRoot, name: "grimoAPP" }]);
    await expect(page.getByLabel("專案路徑")).toHaveValue(createdFolderPath);
    expect(api.createBodies).toHaveLength(0);
    expect(api.nativeDialogBodies).toHaveLength(0);
  });
});

test("AC-S014-6/7: folder browser error keeps form values and does not fallback to native dialog", async ({ page }) => {
  const api = await mockProjectApis(page, { localDirectoryError: "請選擇有效的本機資料夾" });
  await openProjectsView(page);
  await page.getByRole("button", { name: "新增專案" }).click();

  await test.step("Given the user has filled Project Creation form fields", async () => {
    await page.getByLabel("專案名稱").fill("grimoAPP");
    await page.getByLabel("專案描述").fill("本機 AI 開發工作台");
    await page.getByLabel("專案路徑").fill("/Users/samzhu/workspace/github-samzhu");
  });

  await test.step("When directory listing fails", async () => {
    await page.getByRole("button", { name: "選擇資料夾" }).click();
  });

  await test.step("Then the modal shows the error and keeps manual input editable", async () => {
    await expect(page.getByText("請選擇有效的本機資料夾")).toBeVisible();
    await expect(page.getByLabel("專案名稱")).toHaveValue("grimoAPP");
    await expect(page.getByLabel("專案描述")).toHaveValue("本機 AI 開發工作台");
    await expect(page.getByLabel("專案路徑")).toHaveValue("/Users/samzhu/workspace/github-samzhu");
    await expect(page.getByLabel("專案路徑")).toBeEditable();
    expect(api.nativeDialogBodies).toHaveLength(0);
  });
});

const folderBrowserViewports = [
  { name: "desktop-1366", width: 1366, height: 768 },
  { name: "desktop-1440", width: 1440, height: 900 },
  { name: "tablet-820", width: 820, height: 1180 },
  { name: "mobile-390", width: 390, height: 844 },
];

test("AC-S014-8: folder browser modal remains usable across desktop and responsive viewports", async ({
  page,
}) => {
  const longRoot =
    "/Users/samzhu/workspace/github-samzhu/very-long-parent-folder/another-long-folder-name/.grimo/projects";
  await mockProjectApis(page, { defaultRootPath: longRoot });

  for (const viewport of folderBrowserViewports) {
    await test.step(`Given the folder browser is open at ${viewport.name}`, async () => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await openProjectsView(page);
      await page.getByRole("button", { name: "新增專案" }).click();
      await page.getByRole("button", { name: "選擇資料夾" }).click();
      await expect(page.getByRole("dialog", { name: "選擇 Project 資料夾" })).toBeVisible();
      await page.getByRole("button", { name: "建立新資料夾" }).click();
      await page.getByLabel("資料夾名稱").fill("new-project-folder");
    });

    await test.step(`Then modal controls fit and stay scoped at ${viewport.name}`, async () => {
      const dialog = page.getByRole("dialog", { name: "選擇 Project 資料夾" });
      await expect(dialog.getByRole("button", { name: "使用此資料夾" })).toBeDisabled();
      await expect(dialog.getByRole("button", { name: "建立並使用" })).toBeVisible();
      await expect(dialog.getByRole("button", { name: "關閉" })).toBeVisible();
      await expect(dialog.getByRole("button", { name: "建立專案" })).toHaveCount(0);

      const metrics = await page.locator(".folder-browser-modal").evaluate((element) => {
        const rect = element.getBoundingClientRect();
        return {
          left: rect.left,
          right: rect.right,
          top: rect.top,
          bottom: rect.bottom,
          viewportWidth: window.innerWidth,
          viewportHeight: window.innerHeight,
          scrollWidth: element.scrollWidth,
          clientWidth: element.clientWidth,
        };
      });
      expect(metrics.left).toBeGreaterThanOrEqual(0);
      expect(metrics.top).toBeGreaterThanOrEqual(0);
      expect(metrics.right).toBeLessThanOrEqual(metrics.viewportWidth);
      expect(metrics.bottom).toBeLessThanOrEqual(metrics.viewportHeight);
      expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 2);

      await expect(page).toHaveScreenshot(`project-folder-browser-${viewport.name}.png`, {
        fullPage: true,
      });
    });
  }
});
