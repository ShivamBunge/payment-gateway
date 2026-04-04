---
description: "Guidelines for Java Spring Boot development with a focus on clean architecture and security."
applyTo: "**/*.java"
---

# Spring Boot Project Standards

## Architecture & Dependency Injection
- **Layered Structure:** Follow the Controller -> Service -> Repository pattern.
- **Constructor Injection:** Always use constructor injection for dependencies.
- **Final Fields:** Declare all injected dependencies as `private final`.
- **Stateless Services:** Business logic must reside in `@Service` classes and remain stateless.

## Coding Style & Annotations
- **Lombok:** Use `@RequiredArgsConstructor`, `@Data`, and `@Builder` to reduce boilerplate.
- **Validation:** Use `@Valid` or `@Validated` for incoming request DTOs in Controllers.
- **DTOs:** Always use DTOs for API requests/responses; do not expose JPA Entities directly.
- **Logging:** Use SLF4J (`@Slf4j` from Lombok) for logging. Avoid `System.out.println`.

## Database & Persistence
- **Spring Data JPA:** Prefer standard repository interfaces.
- **Transactions:** Use `@Transactional` at the Service layer, not the Repository or Controller.
- **Config:** Use `application.yml` for configuration instead of `.properties`.

## Testing
- **Frameworks:** Use JUnit 5 and AssertJ for unit tests.
- **Mocking:** Use Mockito (`@Mock`, `@InjectMocks`) for service-level testing.
- **Web Testing:** Use `MockMvc` for Controller integration tests.