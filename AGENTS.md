# AGENTS

Minum is a minimalist, high-performance Java web framework with zero external dependencies. It is designed for simplicity, observability, and 100% testability.

- **CRITICAL:** **DO NOT** add any external dependencies (libraries). Use only Java 21+ standard library features.
- **CRITICAL:** Maintain 100% line and branch coverage at all times.

## Architecture Highlights

*   **Virtual Threads**: Minum uses a thread-per-request model backed by Java Virtual Threads. Code should be written in a simple, blocking style; the framework handles the concurrency.
*   **Dependency Injection**: Minum uses a `Context` object as a lightweight DI container. Avoid static state; always pass the `Context` or retrieve services (Logger, Constants, ExecutorService) from it.
*   **Database**: Built-in in-memory database with disk persistence (record-per-file or append-only log).
*   **No Magic**: No reflection, no annotations, and no classpath scanning. Every method call is explicit and traceable.

## Development Workflow

1.  **Understand**: Read the `README.md` and `DIRECTORIES_AND_FILES.md` to get oriented.
2.  **TDD (Test-Driven Development)**: Always write a failing test in the appropriate `src/test/java` directory before making any changes to `src/main/java`.
3.  **Implement**: Write the minimal amount of code needed to satisfy the test.
4.  **Verify**: Run the tests.
5.  **Refactor**: Improve the code while maintaining test correctness.

## Tests & Verification

Run tests frequently during development.

*   `make test`: Standard test run.
*   `make test_quiet`: **Recommended for LLMs.** Runs all tests with minimal output, silencing internal logs and noise. Use this to keep your context window clean.
*   `make test_coverage`: Runs tests and generates a JaCoCo coverage report in `target/site/jacoco/index.html`. Coverage must remain at 100%.
*   `make mutation_test`: Runs Pitest mutation testing. This is slow but essential for verifying the quality of your assertions.

## Quality Standards

*   **Documentation**: Every class and public method must be documented with Javadoc.
*   **Logs**: Use `logger.logDebug`, `logger.logTrace`, etc., appropriately. Avoid `System.out.println`.
*   **Invariants**: Use `com.renomad.minum.utils.Invariants` to enforce state correctness at runtime.
*   **Kaizen**: Focus on small, continuous improvements. Leave the code better than you found it.
