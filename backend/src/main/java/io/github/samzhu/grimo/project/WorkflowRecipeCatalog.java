package io.github.samzhu.grimo.project;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Static workflow recipe catalog used during Project onboarding.
 *
 * @see ProjectController
 */
@Component
public class WorkflowRecipeCatalog {

	private static final List<WorkflowRecipeResponse> RECIPES = List.of(
				new WorkflowRecipeResponse(
						"web-service-development",
						"Web 服務開發",
						"Discuss / Explore / Prototype / Spec / Usage / Tkt / Dev / Review for web services",
						"development",
						List.of(
								new WorkflowStepResponse("discuss", "Discuss", "DEFINING"),
								new WorkflowStepResponse("explore", "Explore", "DEFINING"),
								new WorkflowStepResponse("prototype", "Prototype", "DEFINING"),
								new WorkflowStepResponse("spec", "Spec", "DEFINING"),
								new WorkflowStepResponse("usage", "Usage", "DEFINING"),
								new WorkflowStepResponse("tkt", "Tkt", "DEFINING"),
								new WorkflowStepResponse("dev", "Dev", "RUNNING"),
								new WorkflowStepResponse("ai-review", "AI Review", "RUNNING"),
								new WorkflowStepResponse("human-review", "Human Review", "REVIEW")
						),
						List.of(
								new WorkflowRoleResponse(
										"product-manager",
										"Product Manager",
										"釐清產品目標、MVP、使用情境與 acceptance。",
										List.of("Discuss", "Usage", "Ready Gate")
								),
								new WorkflowRoleResponse(
										"architect",
										"Architect",
										"設計架構、資料流、品質基準與 spec design。",
										List.of("Explore", "Prototype", "Spec", "Project Planning")
								),
								new WorkflowRoleResponse(
										"frontend-engineer",
										"Frontend Engineer",
										"負責前端 UI、互動、可及性與 browser evidence。",
										List.of("Prototype", "Dev")
								),
								new WorkflowRoleResponse(
										"backend-engineer",
										"Backend Engineer",
										"負責 API、DB、workflow integration 與 service behavior。",
										List.of("Explore", "Spec", "Dev")
								),
								new WorkflowRoleResponse(
										"qa-reviewer",
										"QA Reviewer",
										"定義驗收策略、測試覆蓋與 Review Materials。",
										List.of("Usage", "Tkt", "AI Review", "Human Review")
								),
								new WorkflowRoleResponse(
										"release-engineer",
										"Release Engineer",
										"負責 release gate、CI/CD、packaging、cleanup/wrap。",
										List.of("Tkt", "Dev", "Wrap")
								)
						),
						"Review → Rating → Fix until quality_score > 9"
				),
				new WorkflowRecipeResponse(
						"coding",
						"開發工作流",
						"Discuss / Explore / Prototype / Spec / Usage / Tkt / Dev / Review",
						"development",
						List.of(),
						List.of(),
						""
				),
				new WorkflowRecipeResponse(
						"research",
						"研究工作流",
						"Research / Synthesis / Review",
						"research",
						List.of(),
						List.of(),
						""
				),
				new WorkflowRecipeResponse(
						"content",
						"內容工作流",
						"Brief / Draft / Review / Publish",
						"content",
						List.of(),
						List.of(),
						""
				)
		);

	public List<WorkflowRecipeResponse> list() {
		return RECIPES;
	}

	public Optional<WorkflowRecipeResponse> findById(String id) {
		return RECIPES.stream()
				.filter(recipe -> recipe.id().equals(id))
				.findFirst();
	}
}
