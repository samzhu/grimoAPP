# ADR-005: Project Path Folder Browser

**Status:** Accepted and implemented for S014
**Date:** 2026-06-13
**Spec:** `docs/grimo/specs/2026-06-12-S014-project-path-folder-browser.md`

## Context

Project Creation needs a way for users to choose an existing repo / codebase path without manually typing a long local path. S013 implemented a local Spring Boot native folder dialog bridge with Swing `JFileChooser`, but user feedback rejected Swing as the primary interaction: the product should keep the choice inside Grimo while still returning a backend-operable absolute `projectPath`.

Browser-native `showDirectoryPicker()` still cannot satisfy the contract because it returns a browser `FileSystemDirectoryHandle`, not a backend absolute path. The existing `GET /api/local-directories` shape can return filesystem-backed absolute paths without reading files, executing shell commands, changing the Project API, or introducing Electron/Tauri.

## Decision

Use a backend-backed Grimo Project Path Folder Browser for S014:

- Frontend opens an in-app modal when the user clicks `選擇資料夾`.
- Frontend calls `GET /api/local-directories` to browse local filesystem directories.
- A request without `path` starts at `~/.grimo/projects/` and creates that root if missing.
- The modal provides Finder-like shortcuts for `回家目錄` and `回 Grimo 預設位置`, implemented as `location=home|default`; it does not add a modal path-jump text input.
- The modal lets users create a new child folder by entering a folder name; successful creation immediately selects that new folder as the `projectPath`.
- `~/.grimo/projects/` is only the Grimo-managed browsing root, not a selectable Project Path. If the user wants Grimo-managed storage, they leave `projectPath` blank and `POST /api/projects` creates `~/.grimo/projects/<projectId>`.
- Selecting a folder only fills the existing `projectPath` input. Project creation still happens only through page-level `POST /api/projects`.
- `POST /api/projects` remains unchanged and still receives only `projectPath`.
- S014 does not use Swing / OS folder chooser as primary UX or fallback. Folder browser errors stay inside the modal, and manual `projectPath` remains editable.

## Consequences

- S013 Native Folder Dialog Bridge is shipped history; S014 removes the production bridge and frontend API wrapper.
- Frontend S014 implementation must not call a native folder dialog endpoint, including after directory listing errors.
- The folder browser lists immediate readable child directories only and can create one named child directory at the current location; it does not read file contents, execute shell commands, create Projects, write DB rows, persist dialog selections, or expose browser handles.
- Browser `FileSystemDirectoryHandle` remains out of the backend `projectPath` contract.
- Electron and Tauri remain future packaging candidates, not S014 MVP dependencies.

## Evidence

- Backend evidence: `cd backend && ./gradlew test --tests '*LocalDirectoryApiTests'`
- Frontend evidence: `npm --prefix frontend run test:visual -- project-management.ui.spec.ts --grep "AC-S014"`
- Full-stack evidence: `npm --prefix frontend run test:fullstack -- project-onboarding.fullstack.spec.ts --grep "AC-S014"`
