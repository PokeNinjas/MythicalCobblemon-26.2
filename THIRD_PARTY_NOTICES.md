# Third-party notices

This distribution includes work by The Cobblemon Team and the contributors
credited in the existing source and asset files. All existing notices and
licenses remain applicable.

| Component | Version | License / notice |
| --- | --- | --- |
| Cobblemon source | modified 1.8.2 / Minecraft 26.2 | Root `LICENSE`, MPL-2.0 |
| Cobblemon visual/audio resources | omitted; see `RESOURCES.md` | Asset license retained for reference in `LICENSES/Cobblemon-assets-LICENSE` |
| Cable / Bedrockk MoLang | 1.1.20 | `LICENSES/MoLang-GPL-3.0.txt`, source in `third_party/molang` |
| Pokemon Showdown | embedded battle engine | `LICENSES/showdown-LICENSE`, MIT |
| MongoDB Java driver, BSON and BSON record codec | 4.10.2 | `LICENSES/mongodb-LICENSE.txt` and `LICENSES/mongodb-NOTICES`, Apache-2.0 |
| Fabric Permissions API | 0.7.0 | `LICENSES/permissions-LICENSE`, MIT |
| GraalVM SDK, Polyglot, Regex and Truffle libraries | 25.0.4 | `LICENSES/graal-sdk-LICENSE` and `LICENSES/graal-truffle-LICENSE`, UPL-1.0 |
| GraalJS | 25.0.4 | `LICENSES/graal-js-LICENSE` and `LICENSES/graal-js-THIRD_PARTY`, UPL-1.0 and MIT with included third-party notices |
| GraalVM shaded ICU4J and XZ | 25.0.4 | GraalJS third-party notices and additional shaded-library notices in `LICENSES/` |
| Fabric Language Kotlin and its nested libraries | 1.14.1+kotlin.2.4.20 | Original nested notices preserved; additional extracted licenses in `LICENSES/` |
| Kotlin | 2.4.20 | `LICENSES/kotlin-LICENSE` and `LICENSES/kotlin-NOTICE`, Apache-2.0 |
| Kotlin atomicfu | 0.33.0 | `LICENSES/kotlinx.atomicfu-LICENSE.txt` |
| Kotlin coroutines | 1.11.0 | `LICENSES/kotlinx.coroutines-LICENSE.txt` |
| Kotlin datetime | 0.8.0 | `LICENSES/kotlinx-datetime-LICENSE.txt` |
| Kotlin IO | 0.9.1 | `LICENSES/kotlinx-io-LICENSE` |
| Kotlin serialization | 1.11.0 | `LICENSES/kotlinx.serialization-LICENSE.txt` |

The build retains the original nested library jars and adds these notices to
the outer jar. It does not replace their licenses with Cobblemon's MPL license.
The Fabric Loader and Fabric API remain separately supplied runtime components.

`LICENSES/graal-THIRD_PARTY_LICENSE.txt` is the upstream GraalVM notice bundle,
including its ICU and XZ notices. It also describes components of the broader
GraalVM distribution that are not necessarily included in this mod.

MoLang is GPL-3.0 licensed. Its source and license are supplied, and it must not
be represented as MPL-only. When distributing the combined Cobblemon/MoLang
program, the MPL-covered source is additionally available under GPL-3.0 under
MPL section 3.3. The original MPL notices remain intact. See the included GPL
text and https://www.mozilla.org/en-US/MPL/2.0/FAQ/#q14-may-i-combine-mpl-licensed-code-and-lgpl-licensed-code-in-the-same-executable-program.
This does not relicense independently licensed assets or other projects.

Cobblemon models and other visual/audio resources are not included in this
code-only archive. The original model license is retained for reference in
`LICENSES/Cobblemon-assets-LICENSE`; it does not relicense the included code.
Credit: The Cobblemon Team and the contributors credited by upstream.
See `RESOURCES.md` for the pinned upstream resource source and the omissions.
Obtaining resources separately does not change their licenses.
