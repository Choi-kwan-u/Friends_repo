"""Compile the exporter and check its output using Python's JSON parser.

Requires Python 3 and a JDK supporting --release 8. No third-party packages.
Run this script from any working directory; generated files use a temporary dir.
"""

import json
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]


def java_string(value):
    return json.dumps(value, ensure_ascii=True)


def java_model(data):
    lines = [
        "import level.*; import java.nio.file.*; import java.util.*;",
        "public class ExportFixtureCheck {",
        "public static void main(String[] args) throws Exception {",
        "List<WaveDefinition> waves = new ArrayList<WaveDefinition>();",
    ]
    for wave in data["waves"]:
        formation = wave["formation"]
        rows = []
        for row in formation["grid"]:
            cells = [
                "null" if cell is None else "new EnemyCell(%s, %d)" % (
                    java_string(cell["enemyType"]), cell["health"]
                )
                for cell in row
            ]
            rows.append("{" + ",".join(cells) + "}")
        lines.append(
            "waves.add(new WaveDefinition(%s, new EnemyCell[][] {%s}, %d, %d, %d, %d));"
            % (java_string(wave["id"]), ",".join(rows), formation["initialX"],
               formation["initialY"], wave["movementBaseFrames"], wave["shootingIntervalMs"])
        )
    lines.append(
        "new JsonLevelWriter().write(Paths.get(args[0]), new LevelDefinition(%d, %s, %s, waves));"
        % (data["schemaVersion"], java_string(data["id"]), java_string(data["name"]))
    )
    lines.append("}}")
    return "\n".join(lines)


def main():
    fixtures = [
        ROOT / "examples/levels/campaign-stage-01.json",
        ROOT / "test/level/fixtures/export-expected.json",
    ]
    with tempfile.TemporaryDirectory(prefix="level-export-check-") as temporary:
        build = Path(temporary)
        subprocess.run([
            "javac", "--release", "8", "-encoding", "UTF-8", "-d", str(build),
            *map(str, sorted((ROOT / "src/level").glob("*.java"))),
        ], check=True)
        for fixture in fixtures:
            expected = json.loads(fixture.read_text(encoding="utf-8"))
            source = build / "ExportFixtureCheck.java"
            source.write_text(java_model(expected), encoding="utf-8")
            subprocess.run([
                "javac", "--release", "8", "-encoding", "UTF-8", "-cp", str(build),
                "-d", str(build), str(source),
            ], check=True)
            output = build / "export.json"
            subprocess.run([
                "java", "-cp", str(build), "ExportFixtureCheck", str(output),
            ], check=True)
            raw = output.read_bytes()
            if not raw.endswith(b"\n") or b"\r" in raw:
                raise AssertionError("Expected LF-only output with a final newline")
            if json.loads(raw.decode("utf-8")) != expected:
                raise AssertionError("Export changed values: " + str(fixture))
            print("PASS: " + str(fixture.relative_to(ROOT)))


if __name__ == "__main__":
    main()
