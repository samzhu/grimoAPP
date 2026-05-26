package io.github.samzhu.grimo.poc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.markpollack.workflow.core.AgentContext;
import io.github.markpollack.workflow.flows.Step;
import io.github.markpollack.workflow.flows.steps.Steps;
import io.github.markpollack.workflow.flows.workflow.Gate;
import io.github.markpollack.workflow.flows.workflow.GateDecision;
import io.github.markpollack.workflow.flows.workflow.LocalStepRunner;
import io.github.markpollack.workflow.flows.workflow.RunOptions;
import io.github.markpollack.workflow.flows.workflow.TraceRecorder;
import io.github.markpollack.workflow.flows.workflow.Workflow;
import io.github.markpollack.workflow.flows.workflow.WorkflowExecutor;
import org.junit.jupiter.api.Test;

/**
 * PRD D19-D21 與 docs/grimo/references/agentworks.md 的 POC：驗證
 * Agent Workflow DSL 不依賴 LLM 或外部服務，也能表達 Grimo main steps
 * 與 automatic quality loop primitives。
 *
 * 執行方式：./gradlew test --tests '*PollackAgentWorkflowDslPocTests'
 * 通過代表 sequential steps、branch、gate、loop、parallel、recovery 與
 * sub-workflow composition 可用於 Grimo workflow recipe design。
 */
class PollackAgentWorkflowDslPocTests {

	@Test
	void sequentialWorkflowStoresStepOutputsInContextAndTrace() {
		var recorder = TraceRecorder.inMemory();
		var executor = new WorkflowExecutor(new LocalStepRunner(), recorder);
		var ctx = AgentContext.withRunId("dsl-sequential-poc");
		Step<String, String> discuss = Step.named("discuss", (agentContext, input) -> input.trim());
		Step<String, String> spec = Step.named("spec", (agentContext, input) ->
				agentContext.require(Steps.outputOf("discuss")) + " -> spec");

		var graph = Workflow.<String, String>define("definition-flow")
				.step(discuss)
				.then(spec)
				.compile();

		var output = executor.execute(graph, ctx, "  build task  ");

		assertThat(output).isEqualTo("build task -> spec");
		assertThat(recorder.getTrace("dsl-sequential-poc"))
				.extracting(transition -> transition.toStep().replaceFirst("-\\d+$", ""))
				.containsExactly("discuss", "spec");
	}

	@Test
	void branchWorkflowRoutesToMatchingPathFromOfficialDslPattern() {
		var ctx = AgentContext.withRunId("dsl-branch-poc");
		var executor = new WorkflowExecutor();
		Step<String, String> classify = Step.named("classify", (agentContext, input) -> input.toLowerCase());
		Step<String, String> ready = Step.named("ready-path", (agentContext, input) -> input + " -> ready");
		Step<String, String> needsHuman = Step.named("needs-human-path", (agentContext, input) -> input + " -> needs-human");

		var graph = Workflow.<String, String>define("dispatcher-branch")
				.step(classify)
				.branch(output -> ((String) output).contains("ready"))
				.then(ready)
				.otherwise(needsHuman)
				.compile();

		var output = executor.execute(graph, ctx, "READY task");

		assertThat(output).isEqualTo("ready task -> ready");
		assertThat(graph.nodes()).hasSize(5);
		assertThat(graph.edges()).hasSize(5);
	}

	@Test
	void gateWorkflowRoutesPassAndFailQualityLoopDecisions() {
		Gate<ReviewCandidate> qualityGate = (ctx, candidate) ->
				candidate.score() > 9.0 ? GateDecision.PASS : GateDecision.FAIL;
		Step<ReviewCandidate, ReviewCandidate> review = Step.named("review", (ctx, candidate) -> candidate);
		Step<ReviewCandidate, String> approve = Step.named("approve", (ctx, candidate) -> "approved:" + candidate.output());
		Step<ReviewCandidate, String> fix = Step.named("fix", (ctx, candidate) -> "fix:" + candidate.output());
		var graph = Workflow.<ReviewCandidate, String>define("quality-gate")
				.step(review)
				.gate(qualityGate)
				.onPass(approve)
				.onFail(fix)
				.end()
				.compile();
		var executor = new WorkflowExecutor();

		assertThat(executor.execute(graph, AgentContext.withRunId("gate-pass"), new ReviewCandidate("spec", 9.5)))
				.isEqualTo("approved:spec");
		assertThat(executor.execute(graph, AgentContext.withRunId("gate-fail"), new ReviewCandidate("spec", 8.0)))
				.isEqualTo("fix:spec");
	}

	@Test
	void repeatUntilOutputWorkflowRunsUntilOutputPredicatePasses() {
		var recorder = TraceRecorder.inMemory();
		var executor = new WorkflowExecutor(new LocalStepRunner(), recorder);
		var ctx = AgentContext.withRunId("dsl-loop-poc");
		Step<Integer, Integer> improveScore = Step.named("improve-score", (agentContext, score) -> score + 1);
		var graph = Workflow.<Integer, Integer>define("quality-loop")
				.repeatUntilOutput(output -> ((Integer) output) >= 3)
				.step(improveScore)
				.end()
				.compile();

		var output = executor.execute(graph, ctx, 0, RunOptions.maxIterations(10));

		assertThat(output).isEqualTo(3);
		assertThat(recorder.getTrace("dsl-loop-poc"))
				.extracting(transition -> transition.label())
				.contains("continue", "exit");
		assertThat(recorder.getTrace("dsl-loop-poc"))
				.extracting(transition -> transition.toStep().replaceFirst("-\\d+$", ""))
				.contains("improve-score");
	}

	@Test
	void parallelWorkflowFansOutAndCollectsBranchResults() {
		var executor = new WorkflowExecutor();
		Step<String, String> title = Step.named("title", (ctx, input) -> "title:" + input);
		Step<String, String> risk = Step.named("risk", (ctx, input) -> "risk:" + input);
		var graph = Workflow.<String, List<Object>>define("parallel-analysis")
				.parallel(title, risk)
				.compile();

		var output = executor.execute(graph, AgentContext.withRunId("parallel-poc"), "task");

		assertThat(output).containsExactly("title:task", "risk:task");
	}

	@Test
	void onErrorWorkflowRunsRecoveryStepThenConvergesToNextStep() {
		var executor = new WorkflowExecutor();
		var attempts = new AtomicInteger();
		Step<String, String> flaky = Step.named("flaky", (ctx, input) -> {
			attempts.incrementAndGet();
			throw new IllegalStateException("temporary");
		});
		Step<String, String> recover = Step.named("recover", (ctx, input) -> input + " -> recovered");
		Step<String, String> wrap = Step.named("wrap", (ctx, input) -> input + " -> wrapped");
		var graph = Workflow.<String, String>define("error-recovery")
				.step(flaky)
				.onError(IllegalStateException.class, recover)
				.then(wrap)
				.compile();

		var output = executor.execute(graph, AgentContext.withRunId("error-poc"), "task");

		assertThat(output).isEqualTo("task -> recovered -> wrapped");
		assertThat(attempts).hasValue(1);
	}

	@Test
	void subWorkflowCompositionTreatsWorkflowAsReusableStep() {
		var executor = new WorkflowExecutor();
		Step<String, String> review = Step.named("review", (ctx, input) -> input + " -> reviewed");
		Step<String, String> fix = Step.named("fix", (ctx, input) -> input + " -> fixed");
		var reviewFixSubWorkflow = Workflow.<String, String>define("review-fix-sub-workflow")
				.step(review)
				.then(fix)
				.build();
		var graph = Workflow.<String, String>define("phase-with-quality-loop")
				.step(reviewFixSubWorkflow)
				.then(Step.named("next-phase", (ctx, input) -> input + " -> next"))
				.compile();

		var output = executor.execute(graph, AgentContext.withRunId("sub-workflow-poc"), "discuss");

		assertThat(output).isEqualTo("discuss -> reviewed -> fixed -> next");
	}

	private record ReviewCandidate(String output, double score) {
	}

}
