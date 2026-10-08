#!/usr/bin/env bash
# 로컬 사진 저장소 점검 11가지 (003 T003, contracts/storage.md §1-1, docs/23 §2-3).
#   1) docker compose의 minio에 minio-init(pgsty/mc)으로 버킷·정책·앱 키를 준비하고
#   2) scripts/lib/presign_check.py로 11가지를 차례로 시험한다.
# 결과: PASS/FAIL 개수. 하나라도 실패하면 종료 코드 1. 비밀값은 출력하지 않는다.
#
# 다른 저장소(임의 포트로 띄운 컨테이너 등)를 점검할 때:
#   SKIP_INIT=1 S3_ENDPOINT=http://localhost:19000 ./scripts/check-storage.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

if [[ "${SKIP_INIT:-0}" != "1" ]]; then
  docker compose -f "$ROOT/docker-compose.yml" up -d minio >/dev/null
  docker compose -f "$ROOT/docker-compose.yml" run --rm minio-init
fi

S3_ENDPOINT="${S3_ENDPOINT:-http://localhost:9000}" \
APP_KEY="${BLOG_IMAGE_STORAGE_ACCESS_KEY:?BLOG_IMAGE_STORAGE_ACCESS_KEY를 .env에 넣어 주세요}" \
APP_SECRET="${BLOG_IMAGE_STORAGE_SECRET_KEY:?BLOG_IMAGE_STORAGE_SECRET_KEY를 .env에 넣어 주세요}" \
BUCKET="${BLOG_IMAGE_STORAGE_BUCKET:-blog}" \
SITE_ORIGIN="${SITE_ORIGIN:-http://localhost:8080}" \
  python3 "$ROOT/scripts/lib/presign_check.py"
