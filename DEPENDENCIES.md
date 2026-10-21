# Dependency provenance

The Fabric build uses `local.com.bedrockk:molang:1.1.20`. Despite its coordinate,
this dependency is available in the public Cobblemon repository. The preparation
script fetches the jar and POM from upstream commit
`0bd7b4dab32d20b692de0ff5dc6a48450018a77c` and verifies the SHA-256 values in
`dependencies.lock.json`. The jar checksum is
`c25ad69028b1489d985b5c7e3dd3d1d58ae10595e5e78d0b50c0919d9a8068ba`.

MoLang 1.1.20 source is included from
[Cable / MoLang](https://gitlab.com/cable-mc/molang), commit
`afbcf03a5a01bd9cad10132a7f51b0c6ea70decb`. Its Java sources and upstream build
files are retained, with a build-only correction dated 2026-10-21: the obsolete
self-dependency and remote publishing configuration were removed, and the JUnit
runtime was pinned. No MoLang Java source was changed. Its license is GPL-3.0.

To build and test MoLang separately, set `JAVA_HOME` to a Java 21 JDK and run
the root wrapper with `-p third_party/molang --no-daemon build`. Restore Java 25
before building Cobblemon. This standalone build passed all 13 upstream tests.
It does not replace the checksum-pinned dependency used by the main build.
The rebuilt jar is not byte-identical to the historical binary; compiler and
Lombok output differs. Both source and binary provenance are recorded above.

Other build and runtime dependencies are pinned in `fabric/build-26.2.gradle`.
They are obtained from their public Maven repositories. Binary dependencies,
Gradle caches, and compiled outputs are not part of the source archive.

The embedded Pokemon Showdown JavaScript archive is retained byte for byte.
Its embedded source maps retain the included TypeScript source content. Its
MIT notice is provided in `LICENSES/showdown-LICENSE`.
