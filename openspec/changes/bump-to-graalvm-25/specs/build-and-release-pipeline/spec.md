## ADDED Requirements

### Requirement: Native reachability metadata in the supported unified format

The native production image build SHALL provide GraalVM reachability metadata in
GraalVM's current supported single-file reachability-metadata format, discovered via
classpath auto-detection under `META-INF/native-image/`. The build SHALL NOT depend on
deprecated per-file configuration-resource flags (`-H:ReflectionConfigurationResources`
and the equivalent resource/serialization/JNI flags) to load that metadata.

#### Scenario: Metadata is auto-detected without explicit configuration flags

- **WHEN** the native production image is built
- **THEN** reachability metadata is loaded via classpath auto-detection from
  `META-INF/native-image/` and the build defines no deprecated
  `-H:*ConfigurationResources` flags

#### Scenario: Unified metadata keeps the native image functional end-to-end

- **WHEN** the native image built from the unified metadata is smoke-tested
- **THEN** `GET /health` returns 200 with the git SHA, database migrations apply, and
  the ADR-13 image-resize path succeeds — proving the unified metadata covers the same
  reachability as the previous split-file set
