package com.team.blog.media.integration;

import com.team.blog.media.infra.storage.ImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 브라우저처럼 저장소에 직접 요청하는 도우미 (Presigned PUT·익명 GET). 쿠키·자격 증명을 보내지 않는다. */
final class StorageHttp {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .proxy(HttpClient.Builder.NO_PROXY)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

    private StorageHttp() {}

    /** 업로드 주소로 PUT (헤더 그대로). 상태 코드. */
    static int put(ImageStorage.UploadTarget target, byte[] body) {
        return put(target.url(), target.headers(), body);
    }

    static int put(String url, Map<String, String> headers, byte[] body) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        return send(request.build()).statusCode();
    }

    static HttpResponse<byte[]> get(String url) {
        return send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build());
    }

    private static HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
