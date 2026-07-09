# Spring Boot 4.x Migration and Resource Reduction Plan

Issue: https://github.com/Read-With/BE/issues/133

## Decision

Do not downgrade this service to Spring Framework 4.x. The current codebase is
already on Spring Boot 3.3.1, Java 17, and Jakarta APIs. Moving to Spring
Framework 4.x would require a broad reverse migration from `jakarta.*` to
`javax.*`, would remove current security support, and would not directly reduce
runtime resource usage.

The intended path is:

1. Reduce deployable artifact size and legacy dependency weight first.
2. Stabilize the current Spring Boot 3.x baseline.
3. Upgrade to the latest Spring Boot 3.5.x line.
4. Move to Spring Boot 4.x after dependency compatibility is clear.

## Current Findings

- `bootJar -x test` produced a jar of about 156.83 MB.
- `BOOT-INF/classes/static` accounts for about 66.32 MB.
- `BOOT-INF/lib` accounts for about 90.88 MB.
- EPUB and JSON sample assets under `src/main/resources/static` are bundled into
  the production jar.
- `spring-cloud-starter-aws:2.2.6.RELEASE` pulls in Spring Boot 2 / Spring
  Framework 5 era auto-configuration while the application configures
  `AmazonS3` directly through AWS SDK v1.
- H2 was present on the production runtime classpath even though it is only
  needed by tests.

## First PR Scope

- Remove H2 from the production runtime classpath.
- Remove the unused legacy Spring Cloud AWS starter.
- Keep `aws-java-sdk-s3` because `AmazonConfig` and `AmazonS3Manager` still use
  the AWS SDK v1 S3 client directly.
- Document the migration risk and follow-up plan.

## First PR Measurements

- Production jar size changed from about 156.83 MB to about 147.83 MB.
- `BOOT-INF/lib` changed from about 90.88 MB to about 81.88 MB.
- `BOOT-INF/classes/static` remains about 66.32 MB and is the next major
  reduction target.
- `dependencyInsight` confirms `spring-cloud-starter-aws` is no longer on the
  runtime classpath.
- `bootJar -x test` passes.
- `test` still fails with test class loading errors that predate the framework
  migration work and should be fixed before Boot 3.5 or Boot 4 upgrades.

## Follow-up Work

1. Fix local and CI test baseline before framework upgrades.
   - `clean test` currently fails with test class loading errors.
   - `JAVA_HOME` must point to a JDK root, not a nested runtime directory.

2. Split sample assets from production packaging.
   - Move bundled EPUB and upload fixtures to test fixtures, object storage, or a
     seed-data package.
   - Validate any endpoint or loader that expects files under `/static`.

3. Upgrade within Spring Boot 3 first.
   - Move from 3.3.1 to the latest 3.5.x patch line.
   - Clear deprecations and configuration warnings.

4. Prepare for Spring Boot 4.x.
   - Upgrade Gradle wrapper to a Boot 4 compatible version.
   - Verify Spring AI compatibility and move off milestone dependencies.
   - Either migrate S3 usage to AWS SDK v2 or a Spring Cloud AWS version that
     supports Boot 4.x.
   - Use `spring-boot-properties-migrator` only temporarily during the upgrade.

## Validation Targets

- Jar and container image size.
- Cold start time and idle RSS.
- S3 upload/download flows.
- JWT and OAuth login flows.
- Admin upload and image generation flows.
- Flyway validation.
- Main API smoke tests.
