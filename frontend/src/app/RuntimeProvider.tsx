import type { ReactNode } from "react";
import {
  AssistantRuntimeProvider,
  type ChatModelAdapter,
  useLocalRuntime,
} from "@assistant-ui/react";

const modelAdapter: ChatModelAdapter = {
  async run() {
    return {
      content: [
        {
          type: "text",
          text:
            "POC 回覆：這裡使用 assistant-ui 的 ThreadPrimitive 與 ComposerPrimitive。之後 Spring Boot 只要提供 chat endpoint，即可替換這個本地 adapter。",
        },
      ],
    };
  },
};

export function RuntimeProvider({ children }: { children: ReactNode }) {
  const runtime = useLocalRuntime(modelAdapter);
  return (
    <AssistantRuntimeProvider runtime={runtime}>
      {children}
    </AssistantRuntimeProvider>
  );
}

