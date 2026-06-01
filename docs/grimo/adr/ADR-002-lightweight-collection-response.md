# ADR-002 — Lightweight Collection and Page Responses Without HATEOAS

**Status:** Accepted for Grimo MVP API contracts  
**Date:** 2026-06-01

## Context

Project Creation Page needs `GET /api/workflow-recipes` to return multiple workflow options, and each workflow item must carry its own `roles[]`, `steps[]`, and preview metadata. The same Project Creation flow also reads `GET /api/projects` after creation so the user can see the new Project in the list. Future list endpoints, such as Task list, will need pagination and total counts. Spring Data Commons provides `PagedModel` for stable JSON rendering of Spring Data `Page`, and Spring HATEOAS provides `CollectionModel`, but Grimo MVP does not need HAL links or hypermedia navigation.

## Decision

Use a lightweight Grimo-owned DTO for non-paged REST collection endpoints:

```java
record CollectionResponse<T>(List<T> content) {}
```

Use a Grimo-owned DTO for paged REST collection endpoints:

```java
record PageResponse<T>(List<T> content, PageMetadata page) {}

record PageMetadata(int size, long totalElements, int totalPages, int number) {}
```

For S002, `GET /api/workflow-recipes` should return `CollectionResponse<WorkflowRecipeResponse>`:

```json
{
  "content": [
    {
      "id": "web-service-development",
      "name": "Web 服務開發",
      "roles": []
    },
    {
      "id": "research",
      "name": "研究工作流",
      "roles": []
    }
  ]
}
```

`GET /api/projects` should return `CollectionResponse<ProjectResponse>`:

```json
{
  "content": [
    {
      "id": "01226N0640J7Q",
      "name": "grimoAPP",
      "workflowRecipeId": "web-service-development",
      "workflowRoles": []
    }
  ]
}
```

Do not introduce Spring Data REST, Spring HATEOAS, HAL, `_links`, `CollectionModel`, or `PagedModel` for S002. Do not directly serialize Spring Data `PageImpl`; if a repository or service uses Spring Data `Page<T>`, map it into `PageResponse<T>` before returning JSON.

## Consequences

- BDD contracts can consistently assert `response.content[*]` for collection endpoints.
- Paged endpoints can consistently assert both `response.content[*]` and `response.page.*`.
- Frontend API clients can treat collection responses uniformly without adopting hypermedia semantics.
- We avoid coupling the MVP API shape to HATEOAS / HAL before navigation links are product requirements.
- If a future endpoint needs hypermedia navigation, it must be introduced by a separate ADR instead of silently switching existing REST DTOs to HAL.

## References

- Spring Data Commons `PagedModel`: <https://docs.spring.io/spring-data/rest/reference/data-commons/repositories/core-extensions.html#core.web.page>
- Spring Data REST repository collection resources: <https://docs.spring.io/spring-data/rest/reference/repository-resources.html>
- Spring HATEOAS `CollectionModel`: <https://docs.spring.io/spring-hateoas/docs/current/api/org/springframework/hateoas/CollectionModel.html>
