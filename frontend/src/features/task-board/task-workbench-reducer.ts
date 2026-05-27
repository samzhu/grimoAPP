import type { View } from "../../app/view-types";

export type TaskWorkbenchState = {
  view: View;
  selectedTaskId: string;
  query: string;
  isNavOpen: boolean;
  isDetailOpen: boolean;
  isDetailPinned: boolean;
  isCreateTaskOpen: boolean;
  isTaskPageOpen: boolean;
};

export type TaskWorkbenchAction =
  | { type: "nav.toggled" }
  | { type: "nav.closed" }
  | { type: "view.selected"; view: View }
  | { type: "query.changed"; query: string }
  | { type: "task.selected"; taskId: string }
  | { type: "detail.closed" }
  | { type: "detail.pinToggled" }
  | { type: "taskPage.opened" }
  | { type: "taskPage.closed" }
  | { type: "createTask.opened" }
  | { type: "createTask.closed" };

export function createInitialTaskWorkbenchState(
  selectedTaskId: string,
): TaskWorkbenchState {
  return {
    view: "tasks",
    selectedTaskId,
    query: "",
    isNavOpen: false,
    isDetailOpen: false,
    isDetailPinned: false,
    isCreateTaskOpen: false,
    isTaskPageOpen: false,
  };
}

export function taskWorkbenchReducer(
  state: TaskWorkbenchState,
  action: TaskWorkbenchAction,
): TaskWorkbenchState {
  switch (action.type) {
    case "nav.toggled":
      return {
        ...state,
        isNavOpen: !state.isNavOpen,
      };
    case "nav.closed":
      return {
        ...state,
        isNavOpen: false,
      };
    case "view.selected":
      return {
        ...state,
        view: action.view,
        isDetailOpen: false,
        isTaskPageOpen: false,
      };
    case "query.changed":
      return {
        ...state,
        query: action.query,
      };
    case "task.selected":
      return {
        ...state,
        selectedTaskId: action.taskId,
        isDetailOpen: true,
      };
    case "detail.closed":
      return {
        ...state,
        isDetailOpen: false,
      };
    case "detail.pinToggled":
      return {
        ...state,
        isDetailPinned: !state.isDetailPinned,
      };
    case "taskPage.opened":
      return {
        ...state,
        isTaskPageOpen: true,
        isDetailOpen: false,
      };
    case "taskPage.closed":
      return {
        ...state,
        isTaskPageOpen: false,
        isDetailOpen: true,
      };
    case "createTask.opened":
      return {
        ...state,
        isCreateTaskOpen: true,
      };
    case "createTask.closed":
      return {
        ...state,
        isCreateTaskOpen: false,
      };
    default:
      return state;
  }
}
