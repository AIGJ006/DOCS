package com.team.blog.media.web;

import com.team.blog.media.application.ImageProperties;
import com.team.blog.shared.config.CoreProperties;
import com.team.blog.shared.web.CspContributor;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 브라우저가 사진을 직접 올리는 저장소 출처를 모든 응답의 CSP {@code connect-src}에 더한다 (003 T019, research R20). 001 {@code
 * SecurityHeadersFilter}는 공개 주소({@code blog.image.public-base-url}) 출처만 넣는다 — 업로드 주소({@code
 * blog.image.storage.presign-endpoint})가 다르면(예: 공개 주소는 CDN) 그 출처가 필요하다. 같으면 아무것도 더하지 않는다.
 */
@Component
public class StorageCspContributor implements CspContributor {

    private final List<String> connectSrc;

    @Autowired
    public StorageCspContributor(CoreProperties core, ImageProperties images) {
        this(core.image().publicOrigin(), images.storage().presignEndpoint());
    }

    StorageCspContributor(String publicOrigin, String presignEndpoint) {
        String uploadOrigin = originOf(presignEndpoint);
        this.connectSrc = uploadOrigin.equals(publicOrigin) ? List.of() : List.of(uploadOrigin);
    }

    /** 설정 없이 쓰는 생성 (테스트용). */
    public static StorageCspContributor of(String publicOrigin, String presignEndpoint) {
        return new StorageCspContributor(publicOrigin, presignEndpoint);
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        return !connectSrc.isEmpty();
    }

    @Override
    public List<String> extraImgSrc() {
        return List.of();
    }

    @Override
    public List<String> extraConnectSrc() {
        return connectSrc;
    }

    private static String originOf(String url) {
        URI uri = URI.create(url.strip());
        String origin = uri.getScheme() + "://" + uri.getHost();
        return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
    }
}
