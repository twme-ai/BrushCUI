#!/usr/bin/env bash
# End-to-end test: Paper + WorldEdit/FAWE + WorldEditDisplay + BrushCUI, driven by mineflayer.
#
#   BRUSHCUI_E2E_EDITOR   worldedit (default) | fawe
#   BRUSHCUI_E2E_WED_JAR  WorldEditDisplay jar to test with (default: release 2.8.1)
#   BRUSHCUI_E2E_EXPECT_OUTLINE  1 if that WorldEditDisplay supports the +wed|outline hint (2.9.0+)
set -euo pipefail

readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly EDITOR="${BRUSHCUI_E2E_EDITOR:-worldedit}"
readonly PAPER_API="https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds/latest"
readonly PACKETEVENTS_URL="https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar"
readonly PACKETEVENTS_SHA256="060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7"
readonly WORLDEDIT_URL="https://cdn.modrinth.com/data/1u6JkXh5/versions/F5ea2ov3/worldedit-bukkit-7.4.5.jar"
readonly WORLDEDIT_SHA256="e5696a6d064b9969437a8888be91b0941148a28e0c3736de1554a00254a5d142"
readonly FAWE_URL="https://cdn.modrinth.com/data/z4HZZnLr/versions/A1gBu4MM/FastAsyncWorldEdit-Paper-2.16.1.jar"
readonly FAWE_SHA256="a1706c41412a108e3d2fdbd9e9dd0c60904f2ba45414c6edf11c1262812e1838"
readonly WED_URL="https://github.com/TWME-TW/WorldEditDisplay/releases/download/v2.8.1/WorldEditDisplay-2.8.1-paper.jar"
readonly CACHE_DIR="${PROJECT_DIR}/.e2e-cache"
readonly PORT="${BRUSHCUI_E2E_PORT:-$(node -e 'const s=require("net").createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close()})')}"
readonly TEMP_DIR="$(mktemp -d)"
readonly SERVER_DIR="${TEMP_DIR}/server"
SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
    kill "${SERVER_PID}" 2>/dev/null || true
    for _ in $(seq 1 15); do
      kill -0 "${SERVER_PID}" 2>/dev/null || break
      sleep 1
    done
    kill -KILL "${SERVER_PID}" 2>/dev/null || true
    wait "${SERVER_PID}" 2>/dev/null || true
  fi
  rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

fetch() { # url sha256 destination
  if [[ ! -f "$3" ]] || ! echo "$2  $3" | sha256sum --check --status; then
    curl --fail --silent --show-error --location "$1" --output "$3"
  fi
  echo "$2  $3" | sha256sum --check --status
}

mvn -B -q -f "${PROJECT_DIR}/pom.xml" clean package
mkdir -p "${CACHE_DIR}" "${SERVER_DIR}/plugins"

paper_metadata="$(curl --fail --silent --show-error --location "${PAPER_API}")"
fetch "$(jq -er '.downloads["server:default"].url' <<<"${paper_metadata}")" \
  "$(jq -er '.downloads["server:default"].checksums.sha256' <<<"${paper_metadata}")" \
  "${CACHE_DIR}/paper-1.21.11.jar"
fetch "${PACKETEVENTS_URL}" "${PACKETEVENTS_SHA256}" "${CACHE_DIR}/packetevents.jar"
case "${EDITOR}" in
  worldedit) fetch "${WORLDEDIT_URL}" "${WORLDEDIT_SHA256}" "${CACHE_DIR}/worldedit.jar"; editor_jar="${CACHE_DIR}/worldedit.jar" ;;
  fawe) fetch "${FAWE_URL}" "${FAWE_SHA256}" "${CACHE_DIR}/fawe.jar"; editor_jar="${CACHE_DIR}/fawe.jar" ;;
  *) echo "Unknown BRUSHCUI_E2E_EDITOR: ${EDITOR}" >&2; exit 1 ;;
esac
wed_jar="${BRUSHCUI_E2E_WED_JAR:-}"
if [[ -z "${wed_jar}" ]]; then
  wed_jar="${CACHE_DIR}/WorldEditDisplay-2.8.1-paper.jar"
  [[ -f "${wed_jar}" ]] || curl --fail --silent --show-error --location "${WED_URL}" --output "${wed_jar}"
fi

cp "${CACHE_DIR}/paper-1.21.11.jar" "${SERVER_DIR}/paper.jar"
cp "${CACHE_DIR}/packetevents.jar" "${SERVER_DIR}/plugins/packetevents.jar"
cp "${editor_jar}" "${SERVER_DIR}/plugins/editor.jar"
cp "${wed_jar}" "${SERVER_DIR}/plugins/WorldEditDisplay.jar"
cp "${PROJECT_DIR}/target/BrushCUI-"*.jar "${SERVER_DIR}/plugins/BrushCUI.jar"

printf 'eula=true\n' >"${SERVER_DIR}/eula.txt"
printf '%s\n' \
  "server-port=${PORT}" \
  'online-mode=false' \
  'spawn-protection=0' \
  'view-distance=4' \
  'simulation-distance=4' \
  'level-type=minecraft:flat' \
  'generate-structures=false' \
  'motd=BrushCUI integration test' \
  >"${SERVER_DIR}/server.properties"
# Offline-mode UUIDs of the test bots, so they are operators from the start.
python3 - "${SERVER_DIR}/ops.json" BrushNative BrushWed <<'PY'
import hashlib, json, sys, uuid
ops = []
for name in sys.argv[2:]:
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    ops.append({"uuid": str(uuid.UUID(bytes=bytes(digest))), "name": name, "level": 4, "bypassesPlayerLimit": False})
json.dump(ops, open(sys.argv[1], "w"))
PY

pushd "${SERVER_DIR}" >/dev/null
java -Xms1G -Xmx2G -jar paper.jar --nogui >server.log 2>&1 &
SERVER_PID="$!"
popd >/dev/null

for _ in $(seq 1 240); do
  grep -q 'Done (' "${SERVER_DIR}/server.log" 2>/dev/null && break
  if ! kill -0 "${SERVER_PID}" 2>/dev/null; then tail -120 "${SERVER_DIR}/server.log" >&2; exit 1; fi
  sleep 1
done
for plugin in BrushCUI WorldEditDisplay; do
  if ! grep -Eq "Enabling ${plugin} v" "${SERVER_DIR}/server.log"; then
    tail -160 "${SERVER_DIR}/server.log" >&2
    echo "${plugin} did not enable" >&2
    exit 1
  fi
done

npm ci --prefix "${PROJECT_DIR}/integration/mineflayer" --silent 2>/dev/null \
  || npm install --prefix "${PROJECT_DIR}/integration/mineflayer" --silent
if ! BRUSHCUI_E2E_PORT="${PORT}" BRUSHCUI_E2E_EDITOR="${EDITOR}" \
    BRUSHCUI_E2E_EXPECT_OUTLINE="${BRUSHCUI_E2E_EXPECT_OUTLINE:-0}" node "${PROJECT_DIR}/integration/mineflayer/test.mjs"; then
  tail -200 "${SERVER_DIR}/server.log" >&2
  exit 1
fi
if grep -E 'BrushCUI\].*(Exception|failed)|Brush preview failed' "${SERVER_DIR}/server.log"; then
  echo "BrushCUI logged errors" >&2
  exit 1
fi
