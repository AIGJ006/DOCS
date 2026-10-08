#!/bin/sh
# 사진 저장소 준비 (003 T004, contracts/storage.md §1). docker compose의 일회성 minio-init 서비스가 실행한다.
#   - 버킷 blog
#   - 익명 읽기: s3:GetObject, images/* 만 (익명 목록 금지)
#   - 앱 전용 사용자: s3:PutObject·GetObject·DeleteObject, images/* 만
# 필요한 환경 변수: S3_ENDPOINT, STORAGE_ROOT_USER, STORAGE_ROOT_PASSWORD,
#                   BLOG_IMAGE_STORAGE_ACCESS_KEY, BLOG_IMAGE_STORAGE_SECRET_KEY, (선택) BUCKET
# 비밀값은 출력하지 않는다. 여러 번 실행해도 결과가 같다.
set -eu

: "${S3_ENDPOINT:?S3_ENDPOINT가 필요합니다}"
: "${STORAGE_ROOT_USER:?STORAGE_ROOT_USER가 필요합니다}"
: "${STORAGE_ROOT_PASSWORD:?STORAGE_ROOT_PASSWORD가 필요합니다}"
: "${BLOG_IMAGE_STORAGE_ACCESS_KEY:?BLOG_IMAGE_STORAGE_ACCESS_KEY를 .env에 넣어 주세요}"
: "${BLOG_IMAGE_STORAGE_SECRET_KEY:?BLOG_IMAGE_STORAGE_SECRET_KEY를 .env에 넣어 주세요}"
BUCKET="${BUCKET:-blog}"

mc alias set local "$S3_ENDPOINT" "$STORAGE_ROOT_USER" "$STORAGE_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing "local/$BUCKET" >/dev/null

cat > /tmp/anonymous.json <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {"AWS": ["*"]},
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::$BUCKET/images/*"]
    }
  ]
}
JSON
mc anonymous set-json /tmp/anonymous.json "local/$BUCKET" >/dev/null

cat > /tmp/app.json <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::$BUCKET/images/*"]
    }
  ]
}
JSON
mc admin policy create local blog-app /tmp/app.json >/dev/null
mc admin user add local "$BLOG_IMAGE_STORAGE_ACCESS_KEY" "$BLOG_IMAGE_STORAGE_SECRET_KEY" >/dev/null
mc admin policy attach local blog-app --user "$BLOG_IMAGE_STORAGE_ACCESS_KEY" >/dev/null 2>&1 || true
echo "minio-init: 버킷 $BUCKET, images/* 익명 읽기, 앱 전용 사용자 준비 완료"
