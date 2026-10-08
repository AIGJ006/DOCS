#!/usr/bin/env python3
"""사진 저장소 점검 11가지 (003 contracts/storage.md §1-1, docs/23 §2-3, docs/04 §6-1).

환경 변수만 받는다. 비밀값(APP_KEY·APP_SECRET)과 서명된 주소는 출력하지 않는다.

  S3_ENDPOINT   저장소 주소 (예: http://localhost:9000) — 브라우저가 접속하는 주소
  APP_KEY       앱 전용 키 (PutObject·GetObject·DeleteObject만)
  APP_SECRET    앱 전용 비밀 키
  BUCKET        버킷 이름 (기본 blog)
  SITE_ORIGIN   우리 서비스 출처 (예: http://localhost:8080) — CORS 허용 확인용
  S3_REGION     서명 리전 (기본 us-east-1)

결과: 번호별 PASS/FAIL 한 줄씩, 마지막에 "PASS n / FAIL m". 하나라도 실패하면 종료 코드 1.
표준 라이브러리만 쓴다(boto3 없이 SigV4 Presigned PUT을 직접 만든다).
"""

import datetime
import hashlib
import hmac
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

CACHE_CONTROL = "public, max-age=31536000, immutable"
OTHER_ORIGIN = "https://evil.example"

# 1x1 WebP (VP8L) — 내용은 점검에 중요하지 않다
WEBP = bytes.fromhex(
    "524946461a000000574542505650384c0d0000002f0000001007101111888808"
)


def env(name, default=None):
    value = os.environ.get(name, default)
    if value is None or value == "":
        print(f"환경 변수 {name}가 필요합니다", file=sys.stderr)
        sys.exit(2)
    return value


ENDPOINT = env("S3_ENDPOINT").rstrip("/")
APP_KEY = env("APP_KEY")
APP_SECRET = env("APP_SECRET")
BUCKET = env("BUCKET", "blog")
SITE_ORIGIN = env("SITE_ORIGIN")
REGION = os.environ.get("S3_REGION") or "us-east-1"

_parsed = urllib.parse.urlsplit(ENDPOINT)
HOST = _parsed.netloc
BASE_PATH = _parsed.path.rstrip("/")


def _sign(key, msg):
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def presign(method, key, expires, signed_headers, now=None):
    """SigV4 Presigned 주소 (path-style). signed_headers: {이름(소문자): 값} — host는 자동으로 더한다."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date = now.strftime("%Y%m%d")
    scope = f"{date}/{REGION}/s3/aws4_request"
    headers = {"host": HOST, **{k.lower(): v for k, v in signed_headers.items()}}
    names = sorted(headers)
    path = f"{BASE_PATH}/{BUCKET}/{urllib.parse.quote(key, safe='/-_.~')}"
    query = {
        "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
        "X-Amz-Credential": f"{APP_KEY}/{scope}",
        "X-Amz-Date": amz_date,
        "X-Amz-Expires": str(expires),
        "X-Amz-SignedHeaders": ";".join(names),
    }
    canonical_query = "&".join(
        f"{urllib.parse.quote(k, safe='-_.~')}={urllib.parse.quote(v, safe='-_.~')}"
        for k, v in sorted(query.items())
    )
    canonical_headers = "".join(f"{n}:{headers[n].strip()}\n" for n in names)
    canonical = "\n".join(
        [method, path, canonical_query, canonical_headers, ";".join(names), "UNSIGNED-PAYLOAD"]
    )
    to_sign = "\n".join(
        ["AWS4-HMAC-SHA256", amz_date, scope, hashlib.sha256(canonical.encode()).hexdigest()]
    )
    k = _sign(("AWS4" + APP_SECRET).encode(), date)
    k = _sign(k, REGION)
    k = _sign(k, "s3")
    k = _sign(k, "aws4_request")
    signature = hmac.new(k, to_sign.encode(), hashlib.sha256).hexdigest()
    return f"{ENDPOINT}{path[len(BASE_PATH):]}?{canonical_query}&X-Amz-Signature={signature}"


def request(method, url, body=None, headers=None):
    """(상태 코드, 응답 헤더). 네트워크 오류면 (0, {})."""
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            res.read()
            return res.status, {k.lower(): v for k, v in res.headers.items()}
    except urllib.error.HTTPError as e:
        e.read()
        return e.code, {k.lower(): v for k, v in e.headers.items()}
    except (urllib.error.URLError, OSError):
        return 0, {}


def put_headers(content_type="image/webp", length=len(WEBP)):
    return {
        "content-type": content_type,
        "content-length": str(length),
        "cache-control": CACHE_CONTROL,
    }


def main():
    key = f"images/check/{uuid.uuid4()}.webp"
    results = []

    def check(number, title, ok):
        results.append(ok)
        print(f"{number:>2}. {'PASS' if ok else 'FAIL'}  {title}")

    # 1 정상 Presigned PUT
    url = presign("PUT", key, 300, put_headers())
    status, _ = request("PUT", url, WEBP, put_headers())
    check(1, "정상 Presigned PUT → 200", status == 200)

    # 2 서명 위조
    forged = url[:-4] + ("0000" if not url.endswith("0000") else "1111")
    status, _ = request("PUT", forged, WEBP, put_headers())
    check(2, "서명 위조 → 403", status == 403)

    # 3 서명 후 경로 변경
    moved = url.replace(key, f"images/check/{uuid.uuid4()}.webp")
    status, _ = request("PUT", moved, WEBP, put_headers())
    check(3, "서명 후 경로 변경 → 403", status == 403)

    # 4 서명과 다른 Content-Type
    status, _ = request("PUT", url, WEBP, put_headers(content_type="image/png"))
    check(4, "서명과 다른 Content-Type → 403", status == 403)

    # 5 만료된 주소 (1초)
    short = presign("PUT", f"images/check/{uuid.uuid4()}.webp", 1, put_headers())
    time.sleep(2.5)
    status, _ = request("PUT", short, WEBP, put_headers())
    check(5, "만료된 주소 → 403", status == 403)

    # 6 익명 목록
    status, _ = request("GET", f"{ENDPOINT}/{BUCKET}?list-type=2")
    check(6, "익명 목록 → 403", status == 403)

    # 7 익명 GET images/*
    status, _ = request("GET", f"{ENDPOINT}/{BUCKET}/{key}")
    check(7, "익명 GET images/* → 200", status == 200)

    # 8 서명 없는 익명 PUT
    status, _ = request(
        "PUT", f"{ENDPOINT}/{BUCKET}/images/check/{uuid.uuid4()}.webp", WEBP, put_headers()
    )
    check(8, "서명 없는 익명 PUT → 403", status == 403)

    # 9 images/* 밖 익명 읽기
    status, _ = request("GET", f"{ENDPOINT}/{BUCKET}/private/{uuid.uuid4()}.webp")
    check(9, "images/* 밖 익명 읽기 → 403", status == 403)

    preflight = {
        "Access-Control-Request-Method": "PUT",
        "Access-Control-Request-Headers": "content-type,cache-control",
    }
    # 10 CORS 사전 요청, 우리 출처
    status, headers = request("OPTIONS", url, headers={"Origin": SITE_ORIGIN, **preflight})
    allowed = headers.get("access-control-allow-origin") in (SITE_ORIGIN, "*")
    check(10, "CORS 사전 요청(우리 출처) → 허용", 200 <= status < 300 and allowed)

    # 11 CORS 사전 요청, 다른 출처
    status, headers = request("OPTIONS", url, headers={"Origin": OTHER_ORIGIN, **preflight})
    leaked = headers.get("access-control-allow-origin") in (OTHER_ORIGIN, "*")
    check(11, "CORS 사전 요청(다른 출처) → 거부", not leaked)

    # 정리 (앱 키의 DeleteObject)
    request("DELETE", presign("DELETE", key, 60, {}))

    passed = sum(results)
    failed = len(results) - passed
    print(f"PASS {passed} / FAIL {failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
