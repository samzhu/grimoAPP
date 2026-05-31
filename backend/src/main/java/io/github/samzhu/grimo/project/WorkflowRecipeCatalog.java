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
					"coding",
					"開發工作流",
					"Discuss / Explore / Prototype / Spec / Usage / Tkt / Dev / Review",
					"development"
			),
			new WorkflowRecipeResponse(
					"research",
					"研究工作流",
					"Research / Synthesis / Review",
					"research"
			),
			new WorkflowRecipeResponse(
					"content",
					"內容工作流",
					"Brief / Draft / Review / Publish",
					"content"
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
