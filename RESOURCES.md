# Omitted resources

This source archive omits textures, icons, models, animations, sounds,
localization, bundled resource packs, and structure NBT files. Gameplay data,
shader programs, resource-generation scripts, tests, and the embedded Showdown
battle-engine code remain included.

The upstream source base is Cobblemon commit
`0bd7b4dab32d20b692de0ff5dc6a48450018a77c`:
https://gitlab.com/cable-mc/cobblemon/-/tree/0bd7b4dab32d20b692de0ff5dc6a48450018a77c

Resource locations in that revision:
- `common/src/main/resources/assets/`
- `common/src/main/resources/resourcepacks/`
- Structure NBT files below `common/src/main/resources/data/` and `datapacks/`

The separate asset project is https://gitlab.com/cable-mc/cobblemon-assets.
Its current branch is not a version pin for this port. Retain upstream credits
and the applicable licenses when obtaining resources. The model license is
also included in `LICENSES/Cobblemon-assets-LICENSE` for reference.

The code compiles and the standard `test` task runs without these resources.
Resource-dependent tests in `migrationTest` require omitted models, item definitions, and
structure files. All tests and their assertions are retained. To
assemble a playable distribution, obtain appropriately licensed resources and
adapt them to this port's Minecraft 26.2 resource formats and identifiers.
Copying upstream resources alone is not a verified playable build or an exact
reconstruction of the modified release. No resources are downloaded by the
build, and this archive does not grant additional asset rights.

## Full regression tests with separately supplied resources

Supply a directory containing the matching `assets/`, `data/`, and `datapacks/`
resource trees. It must match this modified release, not just the upstream
base: the tests check the migrated 26.2 formats and exact content counts.
Run the root wrapper with these arguments (quote the property if it has spaces):

```text
-p fabric --no-daemon build migrationTest -PresourceTestRoot=/path/to/matching/resources
```

This directory is read as test input only. Its contents are not copied into
source or packaged jars. Missing or mismatched resources fail the checks;
no tests or assertions are disabled. The shader programs included in this
source package take precedence over the separately supplied resources.
