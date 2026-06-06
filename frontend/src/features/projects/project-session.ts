export type ProjectSession = {
  lastActiveProjectId: string | null;
  isClosed: boolean;
};

const projectSessionKey = "grimo.projectSession";

const closedProjectSession: ProjectSession = {
  lastActiveProjectId: null,
  isClosed: true,
};

export function readProjectSession(): ProjectSession {
  const storedSession = window.localStorage.getItem(projectSessionKey);
  if (!storedSession) {
    return closedProjectSession;
  }

  try {
    const parsed = JSON.parse(storedSession) as Partial<ProjectSession>;
    return {
      lastActiveProjectId:
        typeof parsed.lastActiveProjectId === "string"
          ? parsed.lastActiveProjectId
          : null,
      isClosed: parsed.isClosed !== false,
    };
  } catch {
    return closedProjectSession;
  }
}

export function saveOpenProjectSession(projectId: string) {
  window.localStorage.setItem(
    projectSessionKey,
    JSON.stringify({ lastActiveProjectId: projectId, isClosed: false }),
  );
}

export function saveClosedProjectSession(lastActiveProjectId: string | null) {
  window.localStorage.setItem(
    projectSessionKey,
    JSON.stringify({ lastActiveProjectId, isClosed: true }),
  );
}

export function clearProjectSession() {
  window.localStorage.removeItem(projectSessionKey);
}
