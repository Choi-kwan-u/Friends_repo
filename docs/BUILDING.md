# Building and testing

Use **JDK 17, 21, or 25**, available through `JAVA_HOME` or `PATH`. Compilation, tests, and game launch use the JDK running Gradle; a separate JDK 17 installation is not required. The build targets Java 17 with `--release 17` and uses the included **Gradle 9.8.2 Wrapper**. Gradle, JUnit, JaCoCo, and Spotless are downloaded automatically; the first build needs internet access. No separate Gradle installation or IDE plugin is required.

## Build and run

From the repository root, run:

```powershell
# Windows PowerShell
.\gradlew.bat clean build
.\gradlew.bat run
```

```bash
# macOS / Linux
sh ./gradlew clean build
sh ./gradlew run
```

`build` compiles the game, runs the checks below, and creates the JAR and distributions under `build/`. Use `run` to open the game for manual testing.

## IntelliJ IDEA

1. Open the repository folder containing `settings.gradle` as a Gradle project.
2. Set **Project SDK** and **Gradle JVM** to the same JDK (17, 21, or 25), keeping the project language level at **17**. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, use the **Wrapper** and select **Gradle** for building and running tests.
3. Reload the Gradle project, then select **Invaders - Run** from the run configuration menu. The shared configurations in `.run/` use the same tasks as the command line.

Gradle configures `src/`, `res/`, and `test/`; manual source-root or classpath setup is unnecessary. If the shared configurations are not visible yet, run the corresponding task from the Gradle tool window after reloading.

## Tasks and reports

Run a task with `.\gradlew.bat <task>` on Windows or `sh ./gradlew <task>` on macOS/Linux.

| Task | IntelliJ configuration | Purpose |
| --- | --- | --- |
| `run` | Invaders - Run | Compile and launch the game |
| `build` | Invaders - Build | Compile, run `check`, and package the game |
| `check` | Invaders - Check | Run JUnit, standalone regression tests, Spotless, and combined coverage |
| `test` | Invaders - JUnit Tests | Run JUnit 5 tests |
| `smokeTest` | Invaders - Regression Tests | Run registered `main()` regression tests |
| `jacocoTestReport` | Invaders - Coverage | Run both test groups and generate JaCoCo reports |
| `spotlessCheck` | Invaders - Format Check | Check whitespace without changing files |
| `spotlessApply` | Invaders - Format | Apply whitespace cleanup |

- JUnit results: `build/reports/tests/test/index.html`.
- Combined JaCoCo coverage: `build/reports/jacoco/test/html/index.html` and `build/reports/jacoco/test/jacocoTestReport.xml`.
- Standalone regression results appear in the console and contribute to the combined coverage report.

Spotless currently handles trailing whitespace, final newlines, and LF line endings. It does not change Java indentation or import order. A broader Java formatting style remains open for course-wide discussion. Coverage is reported without enforcing a minimum percentage.

Test sources from `test/` and `tests/` are consolidated under `test/`, following their Java package directories. The build rejects Java files left under `tests/` so they cannot be silently skipped. JUnit 5 tests are discovered automatically. Standalone `main()` suites are listed in `smokeTestClasses` in `build.gradle`; register new standalone `*Test.java` suites there so `check` runs them. `test/shop/ShopPreview.java` is a manual tool outside the automated checks.

To run one suite, use `test --tests "engine.FileManagerTest"` for JUnit or `smokeLevelModelTest` for a standalone test. Use `tasks` to list all available tasks. Existing PowerShell scripts in `scripts/` also delegate to the Wrapper. Use these Gradle tasks instead of manual `javac` commands in older team documents.

## CI and local files

[GitHub Actions](../.github/workflows/ci.yml) runs `clean build` with JDK 17 on Windows and Linux for pull requests and pushes to `main`. These two jobs verify the minimum supported Java version; JDK 21 and 25 are checked separately when changing JDK support or build tooling. Available test and coverage reports are kept as workflow artifacts for seven days, including on failed builds.

`clean` removes generated files under `build/`. Gradle `run` keeps local scores and player assets in the repository root; records and achievements use the existing `.space-invaders/` directory under the user's home. Build output, local saves, and personal IDE settings are excluded from Git.
