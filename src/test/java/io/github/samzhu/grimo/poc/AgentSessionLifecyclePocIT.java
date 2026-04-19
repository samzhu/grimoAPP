package io.github.samzhu.grimo.poc;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springaicommunity.agents.claude.ClaudeAgentSessionRegistry;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentSession;
import org.springaicommunity.agents.model.AgentSessionRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S011 POC — 探索 AgentSession 生命週期。
 *
 * <p>目的：
 * <ol>
 *   <li>驗證 create → prompt → resume 的完整生命週期</li>
 *   <li>釐清 sessionId 來自何處（CLI or Java）</li>
 *   <li>測試 resume() 是否保留上下文</li>
 *   <li>探索 find() 的實際行為</li>
 *   <li>評估跨 JVM 恢復的可行性</li>
 * </ol>
 *
 * <p>執行：{@code ./gradlew integrationTest --tests '*AgentSessionLifecyclePocIT*'}
 * <p>需要主機安裝 claude CLI 並已登入。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentSessionLifecyclePocIT {

    private static AgentSessionRegistry registry;
    private static boolean claudeAvailable;

    // 跨測試共享，探索生命週期
    private static AgentSession session;
    private static String savedSessionId;

    @BeforeAll
    static void checkClaude() {
        try {
            var process = new ProcessBuilder("which", "claude").start();
            claudeAvailable = process.waitFor() == 0;
        } catch (Exception e) {
            claudeAvailable = false;
        }
        Assumptions.assumeTrue(claudeAvailable, "claude CLI not found — skipping POC");

        registry = ClaudeAgentSessionRegistry.builder()
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    @Test
    @Order(1)
    void poc1_createSession_andInspect() {
        // Given: a working directory
        Path workDir = Path.of("").toAbsolutePath();

        // When: create a session
        session = registry.create(workDir);

        // Then: sessionId 應該來自 Claude CLI
        savedSessionId = session.getSessionId();
        System.out.println("=== POC1 ===");
        System.out.println("sessionId: " + savedSessionId);
        System.out.println("sessionId length: " + savedSessionId.length());
        System.out.println("session class: " + session.getClass().getName());

        assertThat(savedSessionId).isNotNull().isNotEmpty();
        assertThat(savedSessionId).isNotEqualTo("default");
    }

    @Test
    @Order(2)
    void poc2_prompt_andGetResponse() {
        Assumptions.assumeTrue(session != null, "poc1 must pass first");

        // When: send a prompt with identifiable context
        AgentResponse response = session.prompt(
                "My secret code word is PINEAPPLE. Just acknowledge it briefly.");

        // Then: 得到回應
        String text = response.getText();
        System.out.println("=== POC2 ===");
        System.out.println("response text: " + text);
        System.out.println("response metadata: " + response.getMetadata());

        assertThat(text).isNotNull().isNotEmpty();
    }

    @Test
    @Order(3)
    void poc3_findSession_inRegistry() {
        Assumptions.assumeTrue(savedSessionId != null, "poc1 must pass first");

        // When: find by sessionId in the same JVM
        Optional<AgentSession> found = registry.find(savedSessionId);

        // Then: 應在記憶體中找到
        System.out.println("=== POC3 ===");
        System.out.println("find() present: " + found.isPresent());

        assertThat(found).isPresent();
    }

    @Test
    @Order(4)
    void poc4_closeSession_thenFindAgain() throws Exception {
        Assumptions.assumeTrue(session != null, "poc1 must pass first");

        // When: close the session
        session.close();

        // Then: find() 之後行為是什麼？
        Optional<AgentSession> found = registry.find(savedSessionId);
        System.out.println("=== POC4 ===");
        System.out.println("find() after close: " + found.isPresent());
        if (found.isPresent()) {
            System.out.println("session status after close: " + found.get().getStatus());
        }
    }

    @Test
    @Order(5)
    void poc5_resumeSession_andVerifyContext() {
        Assumptions.assumeTrue(savedSessionId != null, "poc1 must pass first");

        // 嘗試用 resume() 恢復已關閉的 session
        Optional<AgentSession> found = registry.find(savedSessionId);
        Assumptions.assumeTrue(found.isPresent(), "session must still be in registry");

        AgentSession deadSession = found.get();
        System.out.println("=== POC5 ===");
        System.out.println("status before resume: " + deadSession.getStatus());

        try {
            AgentSession resumed = deadSession.resume();
            System.out.println("resume() succeeded");
            System.out.println("status after resume: " + resumed.getStatus());

            // 驗證上下文保留：問 CLI 記不記得 secret code
            AgentResponse response = resumed.prompt(
                    "What was the secret code word I told you earlier? Just say the word.");
            String text = response.getText();
            System.out.println("context check response: " + text);
            System.out.println("contains PINEAPPLE: " + text.toUpperCase().contains("PINEAPPLE"));

            resumed.close();
        } catch (Exception e) {
            System.out.println("resume() failed: " + e.getClass().getName() + " — " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    void poc6_freshRegistry_resumeWithSavedId() {
        Assumptions.assumeTrue(savedSessionId != null, "poc1 must pass first");

        // 模擬 JVM 重啟：建立全新的 registry
        AgentSessionRegistry freshRegistry = ClaudeAgentSessionRegistry.builder()
                .timeout(Duration.ofMinutes(5))
                .build();

        // find() 在新 registry 中應該找不到
        Optional<AgentSession> found = freshRegistry.find(savedSessionId);
        System.out.println("=== POC6 ===");
        System.out.println("find() in fresh registry: " + found.isPresent());

        // 嘗試直接用 CLIOptions.resume 建立新 session
        // 這裡要探索：能否繞過 registry 直接恢復？
        try {
            // 用 registry.create() 建一個新 session，然後看能否用 resume
            // 這不是正確做法，但可以探索 API 邊界
            System.out.println("savedSessionId for manual resume: " + savedSessionId);
            System.out.println("Claude CLI transcript should be at: ~/.claude/projects/...");

            // 探索：ClaudeAgentSessionRegistry 有沒有 reconnect 之類的 API？
            var methods = freshRegistry.getClass().getDeclaredMethods();
            System.out.println("Registry methods:");
            for (var m : methods) {
                System.out.println("  " + m.getName() + "(" +
                        java.util.Arrays.stream(m.getParameterTypes())
                                .map(Class::getSimpleName)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("") + ")");
            }
        } catch (Exception e) {
            System.out.println("exploration failed: " + e.getMessage());
        }
    }
}
