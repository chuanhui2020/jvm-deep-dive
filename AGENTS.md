# Repository Guidelines

## Project Structure & Module Organization

This checkout is currently empty: no source tree, build configuration, tests, or Git metadata is present. Establish the layout alongside the first implementation. For Java modules, use `src/main/java/` for production code, `src/test/java/` for tests, and corresponding `resources/` directories for fixtures and configuration. Keep explanations in `docs/` and reusable development scripts in `scripts/`. Group independent JVM experiments by topic.

## Build, Test, and Development Commands

No build, test, or local-run commands are configured yet. Use `Get-ChildItem -Force` to inspect the root and `rg --files --hidden` to list files.

When introducing a build system, commit its wrapper and document the required JDK version and exact commands in `README.md`. For example, a Maven setup should support `./mvnw test` and `./mvnw verify`; on Windows, use `./mvnw.cmd`. These commands apply only after the wrapper and build descriptor exist. Document how to run each experiment, including required JVM flags.

## Coding Style & Naming Conventions

For new Java code, use four-space indentation, lowercase package names, `UpperCamelCase` class names, `lowerCamelCase` methods and variables, and `UPPER_SNAKE_CASE` constants. Match public class names to filenames. Prefer descriptive experiment names such as `ClassLoadingDemo`. No formatter or linter is configured; introduce shared configuration before enforcing automated style checks.

## Testing Guidelines

No testing framework or coverage threshold is established. Add automated tests with the first executable module; JUnit Jupiter is a suitable default for Java. Name test classes `*Test.java` and describe behavior in test methods. Keep tests deterministic. For experiments involving GC, timing, or JIT behavior, document the JDK, flags, reproduction steps, and expected observations.

## Commit & Pull Request Guidelines

No Git history is available to infer existing conventions. Use short, imperative commit subjects, for example, `Add class-loading experiment`. Keep commits focused. Pull requests should explain the purpose, affected experiments, validation commands and results, and any JDK requirements. Link related issues when applicable.

## Security & Configuration

Exclude credentials, machine-specific paths, generated `.class` files, and build output from version control. Document required environment variables with placeholder values.
