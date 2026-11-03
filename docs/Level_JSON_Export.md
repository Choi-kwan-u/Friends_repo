# Level JSON export

Related issue: #16. `JsonLevelWriter` implements the `LevelWriter` contract
introduced in #19. It writes a supplied base definition; it does not read JSON,
capture live gameplay, select a file, or start a stage.

```java
LevelWriter writer = new JsonLevelWriter();
writer.write(java.nio.file.Paths.get("custom-stage.json"), definition);
```

The caller obtains the definition and confirms any overwrite before calling
`write`. The parent directory must already exist. Relative paths are resolved
against the process working directory. No directory or player-settings entry
is created automatically.

## Output contract

- UTF-8, two-space indentation, LF line endings, and one final newline.
- Stable field order and deterministic output for the same definition.
- Exactly the shared v1 fields, including formation origins and base timing.
- Wave order, mixed enemy identifiers, health, and every null grid position
  are preserved, including entirely empty rows and columns.
- No mode, official/custom provenance, unlock state, score, or live difficulty
  adjustments are added. Pass original design values rather than adjusted ones.
- Quotes, backslashes, and control characters are escaped. Non-ASCII text is
  retained; supplementary characters are written as paired Unicode escapes.
  Unpaired UTF-16 surrogates fail with `LevelFormatException` before file access.
  The error identifies the field (including wave/grid indexes) and character index.

The writer uses the merged model's structural rules. It does not introduce
unagreed gameplay limits or claim that every exportable formation is playable.
It has no external JSON-library dependency.

## Replacement and failure behavior

Serialization finishes before any destination file is changed. The writer then
creates a temporary file in the destination directory, writes all UTF-8 bytes,
closes the file, and requests an atomic move to the destination. Existing regular
files are replaced where the filesystem supports atomic replacement. Directories,
symbolic links (including dangling links), and other non-regular targets are
rejected.

Filesystem errors propagate as `IOException`. An unsupported atomic move fails
with `AtomicMoveNotSupportedException`; there is no non-atomic fallback. A
provider may also reject replacing an existing file. A failed serialization,
temporary-file write, or rejected atomic replacement does not truncate the
existing destination. Failed operations attempt to remove their temporary file;
if cleanup also fails, that error is attached to the original exception.

This is an atomic replacement request, not a power-loss durability guarantee.
Replacing a file also replaces its inode/metadata with that of the temporary
file; preserving existing permissions or other metadata is outside this API.
Concurrent filesystem changes and overwrite coordination belong to the caller.

## Verification

Run `level.JsonLevelWriterTest` with JUnit Jupiter 5. Include `test/` as a test
resource root on the runtime classpath, or copy its non-Java resources into the
test output directory. The reference JSON is loaded as a classpath resource,
so the working directory does not matter. Reference-file line endings are
normalized to LF for comparison, including on Windows.

Eight tests cover exact UTF-8 output, sparse grids and wave order, immutable input
values, deterministic overwrites, malformed-string failures, null arguments,
missing parents, directory/symlink rejection, and temporary-file cleanup after an
injected atomic-move failure, and field-specific Unicode errors. The symlink check is skipped if symlinks are not
available. The failure injection tests cleanup without relying on OS permissions.

For an independent JSON-parser check, run:

```sh
python3 test/level/verify_json_export.py
```

The script requires Python 3 and a JDK supporting `--release 8`, without external
packages. It resolves inputs relative to its own location and uses a temporary
build directory. An absolute script path also works from another directory.
It reconstructs the repository sample and escaping fixture as Java models,
exports them, and parses the actual output with Python's JSON parser. Both must
match every original field. This check is also useful for machines without JUnit.
Production sources and the new tests compile with Java 8 compatibility, and
the existing standalone model checks pass. Some existing currency tests use
Java 9 collection factories and cannot be compiled with `--release 8`.

No production importer is implemented here. A Java import/export round trip
with 331leo's reader and gameplay integration remains follow-up work for #16.
