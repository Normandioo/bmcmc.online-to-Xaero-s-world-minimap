#!/usr/bin/env bash
# Uploads the mod jar of this release to Modrinth, so launchers update it for the players. Never fails the release: problems are warnings.
# Needs the repository secret MODRINTH_TOKEN (a Modrinth personal access token that may create versions); without it nothing is done.
set -uo pipefail
slug="bmcmc.online-to-xaeros-world-map"
if [ -z "${MODRINTH_TOKEN:-}" ]; then echo "::notice title=Modrinth::no MODRINTH_TOKEN secret, nothing uploaded"; exit 0; fi
if [[ "${GITHUB_REF:-}" == refs/tags/v* ]]; then tag="$GITHUB_REF_NAME"; else tag="v$(grep '^mod_version=' gradle.properties | cut -d= -f2)"; fi
number="${tag#v}"
api=https://api.modrinth.com/v2
ua="User-Agent: Normandioo/bmcmc.online-to-xaero-s-world-minimap (release workflow)"
jar=$(find dist -name 'cumania-territories-*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort | head -1)
if [ -z "$jar" ]; then echo "::warning title=Modrinth::no mod jar found in dist"; exit 0; fi

project=$(curl -fsS -H "$ua" "$api/project/$slug" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])") || { echo "::warning title=Modrinth::project $slug not found"; exit 0; }
if curl -fsS -H "$ua" "$api/project/$project/version" | python3 -c "import json,sys; sys.exit(0 if any(v['version_number']=='$number' for v in json.load(sys.stdin)) else 1)"; then
  echo "Modrinth already has $number"; exit 0
fi
dep() { curl -fsS -H "$ua" "$api/project/$1" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" 2>/dev/null; }
fabric=$(dep fabric-api); worldmap=$(dep xaeros-world-map); minimap=$(dep xaeros-minimap)

notes=".github/release-notes/$tag.md"
export number tag project fabric worldmap minimap notes
curl -fsS -H "$ua" "$api/tag/game_version" > /tmp/mc_versions.json || { echo "::warning title=Modrinth::cannot read the game versions"; exit 0; }
python3 - "$tag" > /tmp/modrinth.json <<'P'
import json, os, sys
tag = sys.argv[1]
known = {v['version'] for v in json.load(open('/tmp/mc_versions.json'))}
wanted = [tag.split('+mc')[1]] if '+mc' in tag else ['26.1', '26.1.1', '26.1.2', '26.2', '26.3']
versions = [v for v in wanted if v in known]
deps = [{'project_id': p, 'dependency_type': t} for p, t in ((os.environ['fabric'], 'required'), (os.environ['worldmap'], 'required'), (os.environ['minimap'], 'optional')) if p]
log = open(os.environ['notes']).read().strip() if os.path.exists(os.environ['notes']) else ''
print(json.dumps({'name': tag, 'version_number': os.environ['number'], 'changelog': log, 'dependencies': deps, 'game_versions': versions,
                  'version_type': 'release', 'loaders': ['fabric'], 'featured': True, 'project_id': os.environ['project'],
                  'file_parts': ['file'], 'primary_file': 'file'}))
P
if ! python3 -c "import json; sys=__import__('sys'); sys.exit(0 if json.load(open('/tmp/modrinth.json'))['game_versions'] else 1)"; then
  echo "::warning title=Modrinth::none of this release's Minecraft versions is known to Modrinth yet"; exit 0
fi
code=$(curl -sS -o /tmp/modrinth.out -w '%{http_code}' -H "$ua" -H "Authorization: $MODRINTH_TOKEN" -F "data=</tmp/modrinth.json;type=application/json" -F "file=@$jar;type=application/java-archive" "$api/version")
if [ "$code" = 200 ]; then echo "::notice title=Modrinth::uploaded $number"; else echo "::warning title=Modrinth upload failed ($code)::$(head -c 300 /tmp/modrinth.out)"; fi
exit 0
