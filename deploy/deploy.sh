#!/bin/sh
# 서버에서 실행하는 배포 스크립트 (GitHub Actions deploy.yml이 ~/buildlog/ 로 복사한 뒤 부른다).
# 손으로 다시 실행해도 된다:  cd ~/buildlog && sh deploy.sh
#   1) buildlog-app.tar.gz 가 있으면 docker load (태그: 커밋 sha, latest)
#   2) .env 확인(권한 600), SITE_URL에서 SITE_HOST·SITE_SCHEME 계산
#   3) minio-init(버킷·정책·앱 키, 여러 번 실행해도 같음) → 전체 up -d
#   4) edge(127.0.0.1:APP_PORT)로 /api/agreements/current 200 을 기다린다
# 비밀값을 출력하지 않는다 (.env를 cat 하거나 set -x 하지 말 것).
set -eu

cd "$(dirname "$0")"

COMPOSE_FILE=docker-compose.prod.yml
IMAGE_ARCHIVE=buildlog-app.tar.gz

if [ ! -f .env ]; then
  echo "deploy: .env가 없습니다 (GitHub Actions가 만들어 넣습니다 — deploy/README.md)" >&2
  exit 1
fi
chmod 600 .env

# docker compose v2 플러그인이 없으면 단독 실행 파일(docker-compose, v2 이상)로 대신한다
if docker compose version >/dev/null 2>&1; then
  dc() { docker compose -p buildlog --env-file .env -f "$COMPOSE_FILE" "$@"; }
elif command -v docker-compose >/dev/null 2>&1; then
  dc() { docker-compose -p buildlog --env-file .env -f "$COMPOSE_FILE" "$@"; }
else
  echo "deploy: docker compose(또는 docker-compose)가 없습니다" >&2
  exit 1
fi

# .env에서 비밀이 아닌 값 몇 개만 읽는다 (값 전체를 셸에 source 하지 않는다 — 특수 문자 안전)
env_value() {
  sed -n "s/^$1=//p" .env | tail -n 1 | sed "s/^'\(.*\)'\$/\1/; s/^\"\(.*\)\"\$/\1/"
}
SITE_URL=$(env_value SITE_URL)
APP_PORT=$(env_value APP_PORT)
if [ -z "$SITE_URL" ] || [ -z "$APP_PORT" ]; then
  echo "deploy: .env에 SITE_URL·APP_PORT가 필요합니다" >&2
  exit 1
fi
SITE_SCHEME=${SITE_URL%%://*}
SITE_HOST=${SITE_URL#*://}
SITE_HOST=${SITE_HOST%%/*}
export SITE_SCHEME SITE_HOST

if [ -f "$IMAGE_ARCHIVE" ]; then
  echo "deploy: 이미지 불러오는 중"
  gunzip -c "$IMAGE_ARCHIVE" | docker load
  rm -f "$IMAGE_ARCHIVE"
fi
APP_IMAGE_TAG=${APP_IMAGE_TAG:-latest}
export APP_IMAGE_TAG
docker image inspect "buildlog-app:$APP_IMAGE_TAG" >/dev/null 2>&1 || {
  echo "deploy: 이미지 buildlog-app:$APP_IMAGE_TAG 가 없습니다" >&2
  exit 1
}

echo "deploy: 설정 확인"
dc config --quiet

case "$(env_value COMPOSE_PROFILES)" in
  *localdb*)
    echo "deploy: 서버 안 PostgreSQL(profile localdb) 준비"
    dc up -d --wait postgres
    ;;
esac

echo "deploy: 저장소 준비 (minio-init)"
dc up -d redis minio
dc run --rm minio-init

echo "deploy: 컨테이너 올리는 중 (이미지 buildlog-app:$APP_IMAGE_TAG)"
dc up -d --remove-orphans

echo "deploy: 앱 기동 확인 (최대 180초)"
i=0
while [ $i -lt 90 ]; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $SITE_HOST" -H "X-Forwarded-Proto: $SITE_SCHEME" \
    "http://127.0.0.1:$APP_PORT/api/agreements/current" || true)
  if [ "$code" = "200" ]; then
    echo "deploy: 완료 — 앱이 응답합니다 ($SITE_URL)"
    dc ps
    # 옛 앱 이미지 정리: 지금 것과 latest만 남긴다 (볼륨·다른 서비스 이미지는 건드리지 않음)
    docker image ls buildlog-app --format '{{.Tag}}' | while read -r tag; do
      [ "$tag" = latest ] || [ "$tag" = "$APP_IMAGE_TAG" ] || docker image rm "buildlog-app:$tag" >/dev/null 2>&1 || true
    done
    docker image prune -f >/dev/null 2>&1 || true
    exit 0
  fi
  i=$((i + 1))
  sleep 2
done

echo "deploy: 앱이 180초 안에 응답하지 않았습니다. 최근 로그:" >&2
dc ps >&2 || true
dc logs --tail 80 app >&2 || true
exit 1
