# Spring Boot 4.x Migration and Resource Review

Issue: https://github.com/Read-With/BE/issues/133

## Decision

Do not downgrade this service to Spring Framework 4.x. The codebase was already
on Spring Boot 3.3.1, Java 17, and Jakarta APIs, so moving to Spring Framework
4.x would be a reverse migration to older `javax.*` APIs and unsupported
security/runtime baselines.

This branch migrates the service to Spring Boot 4.x instead.

## Implemented Scope

- Upgraded Spring Boot from `3.3.1` to `4.1.0`.
- Upgraded the Gradle wrapper from `8.13` to `8.14.3`, which satisfies the
  Spring Boot 4 Gradle baseline.
- Upgraded Spring AI from `1.0.0-M3` to `2.0.0`.
- Switched the OpenAI starter from
  `spring-ai-openai-spring-boot-starter` to
  `spring-ai-starter-model-openai`.
- Upgraded `springdoc-openapi-starter-webmvc-ui` from `2.3.0` to `3.0.3`.
- Added `spring-boot-starter-webmvc-test` and updated `@AutoConfigureMockMvc`
  imports for Boot 4 test packages.
- Migrated application `ObjectMapper` injection and JSON helpers from Jackson 2
  databind types to Boot 4's Jackson 3 `tools.jackson.*` types.
- Updated Spring AI image option builder calls for the Spring AI 2.0 API.
- Removed production runtime H2 and the unused legacy Spring Cloud AWS starter
  in the earlier commit on this branch.
- Removed bundled EPUB/upload/static samples from `src/main/resources/static`
  so production builds rely on S3-backed data instead of packaging local sample
  assets.

## Resource Findings

Spring Boot 4 does not reduce this service's deployable size by itself.

- Initial Boot 3.3.1 jar before dependency cleanup: about `156.83 MB`.
- After removing production H2 and legacy Spring Cloud AWS: about `147.83 MB`.
- After the Boot 4.1.0 migration: about `209.73 MB`.
- After removing `src/main/resources/static`: about `144.56 MB`.

Current Boot 4 jar breakdown after static asset removal:

| Group | Size | Count |
| --- | ---: | ---: |
| `BOOT-INF/lib` | `143.75 MB` | `190` |
| `BOOT-INF/classes` | `1.32 MB` | `359` |
| other | `0.39 MB` | `123` |

The static resource payload is no longer present in the production jar. The main
remaining size driver is library weight. Boot 4 also brings Jackson 3 while some
third-party libraries still pull Jackson 2, so both Jackson generations are
present on the runtime classpath.

## Validation

Validated locally with Java 17, matching Dockerfile and dev GitHub Actions:

- `./gradlew compileJava --no-daemon`
- `./gradlew compileTestJava --no-daemon`
- `./gradlew test --no-daemon`
- `./gradlew clean build -x test --no-daemon`

All commands pass.

## Deployment Notes

Merging this branch to `dev` and deploying the resulting `dev` build will deploy
Spring Boot `4.1.0`. If deployment fails, roll back by reverting the merge commit
or redeploying the previous known-good `dev` revision.

Specific smoke checks after deploy:

- Application boot and health/log startup.
- JWT authenticated endpoints.
- OAuth login flow.
- Book list/detail endpoints.
- Favorite endpoints.
- Graph endpoints.
- Admin upload and image generation flows.
- S3 upload/download flows.
- Flyway migration and validation.

## Follow-up Resource Work

1. Keep EPUB/upload/regression samples outside production resources. The
   Gutenberg regression test now skips when local samples are absent.
2. Review Spring AI/OpenAI dependency footprint. The new starter pulls webclient,
   restclient, reactor, Netty, Kotlin, and OpenAI client dependencies.
3. Consider migrating S3 usage from AWS SDK v1 to AWS SDK v2 if image size and
   dependency hygiene matter for the deployment target.
4. Keep an eye on third-party Jackson 2 dependencies while the app code now uses
   Boot 4/Jackson 3 types.
