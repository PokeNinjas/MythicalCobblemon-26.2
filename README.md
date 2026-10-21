# Cobblemon for Minecraft 26.2 (unofficial port)

Code-only source for the modified Fabric release `1.8.2+26.2.mythical.8`.
Based on Cobblemon by The Cobblemon Team. This is an independent modified
distribution, not an official Cobblemon release.

## Build

Requirements: a Java 25 JDK, Python 3.9 or newer, and internet access for the
first dependency download. Set `JAVA_HOME` to the JDK installation.

From this directory:

```text
python prepare-dependencies.py
```

On Windows:

```text
.\gradlew.bat -p fabric --no-daemon build
```

On Linux or macOS:

```text
sh ./gradlew -p fabric --no-daemon build
```

The wrapper pins Gradle 9.7.1 and verifies its distribution checksum. The build
pins Minecraft, Fabric, Kotlin, and library versions. Outputs are under
`fabric/build/libs/`. The Fabric API mod is a separate runtime requirement.
No private repository, credentials, private server, launcher, or sibling project
is required. `build.ps1` is a Windows convenience entry point after dependency
preparation. Once dependencies are cached, `--offline` can be used.

`common/src` and `fabric/src` contain the implementation, gameplay data, and tests.
Artwork, models, animations, sounds, localization, bundled resource packs, and
structure NBT files are omitted. Shader source and resource-generation scripts
are retained. See `RESOURCES.md` for the upstream resource source.
The resulting jar is for compilation and source review, not gameplay. It is
incomplete without the omitted resources and must not be installed as a mod.
The `migrationTest` task runs the complete compatibility regression suite,
including checks that need the omitted resources. See `RESOURCES.md` for the
external test-resource option. No assertions are skipped when it is run. Building does not start Minecraft or a server.

## Changes

This distribution adapts the common and Fabric implementation to Minecraft 26.2
and Java 25. It includes the current form and move persistence fixes, TM teaching
validation, riding updates, and trainer battle feedback. The source preserves
the existing asset and registry identifiers.

The source package replaces local build paths with pinned public dependencies,
adds dependency notices, identifies the modified distribution, and uses neutral
build metadata. The historical Kotlin module identifier is retained because it
forms part of compiled method names used by existing integrations. Mixin order
matches the distributed jar. Those packaging changes mean a rebuilt jar is not expected to
have the same whole-file checksum as a previously distributed jar.

## Licensing

Cobblemon source remains under the Mozilla Public License 2.0 in `LICENSE`.
Existing file headers, copyright notices, and applicable licenses are retained.
Assets with separate licenses remain subject to those licenses; the root MPL
does not relicense them. See `THIRD_PARTY_NOTICES.md` and `LICENSES/` for bundled
libraries and the embedded battle engine.

The pinned MoLang dependency is GPL-3.0 licensed. Its public source is included
in `third_party/molang`, with provenance and standalone build instructions in
`DEPENDENCIES.md`. For distribution with MoLang, the MPL-covered source is also
available under GPL-3.0 pursuant to MPL section 3.3; both license texts and the
original notices are retained. See `THIRD_PARTY_NOTICES.md` for the scope.
This does not change independent asset licenses or licenses of other projects.
