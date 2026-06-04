# Framework Reuse Rules

After a technology/framework is chosen and pinned in the architecture doc, default to reusing its types, interfaces, and patterns. Build custom abstractions only after verified gaps.

## Reuse Checklist

Run this during Research after mapping existing dependencies:

1. Does the framework already define an interface for this capability? Use it as the port.
2. Does the framework already define request/response types? Use them instead of inventing parallel DTOs.
3. Does the framework provide a configuration or extension point: builder, factory, path override, advisor, interceptor, or SPI? Use it.
4. Does the framework provide an advisor/interceptor chain? Use it for cross-cutting concerns instead of wrapping the whole class.
5. Does an upstream model/adapter already integrate with the infrastructure this spec needs? Inject and configure it before designing a bridge.

## Custom Implementation Is Allowed Only When

- A verified framework gap exists, confirmed by official docs, source inspection, or POC.
- The gap is documented in §2 with source URL or local file path.
- The custom code follows the framework's patterns and returns framework-compatible types when possible.

## Rewrite Same Interface

When an interface is sound but implementation behavior is defective, rewrite the implementation while keeping method signatures and output types compatible.

Use this for parsers, serializers, codecs, adapters, and extension points where downstream framework consumers already depend on the existing interface.

## Anti-Patterns

- Creating a custom port + custom response types + custom parsing when the framework already provides all three.
- Assuming a library's scope from its name instead of reading public APIs.
- Evaluating framework compliance without first identifying the applicable industry standard.
- Designing a parallel type system that mirrors a clean framework record/value object.
