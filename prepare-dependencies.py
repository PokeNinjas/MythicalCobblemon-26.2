"""Fetch the pinned public MoLang binary required by the Fabric build."""
from pathlib import Path
from urllib.request import urlopen
import hashlib
import json

root = Path(__file__).resolve().parent
lock = json.loads((root / 'dependencies.lock.json').read_text())
for dependency in lock['molang']:
    target = root / 'dependencies/maven' / dependency['path'].removeprefix('deps/')
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == dependency['sha256']:
        print('Verified', target.relative_to(root))
        continue
    data = urlopen(dependency['url'], timeout=60).read()
    if hashlib.sha256(data).hexdigest() != dependency['sha256']:
        raise SystemExit('Dependency checksum mismatch: ' + dependency['path'])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    print('Downloaded and verified', target.relative_to(root))
