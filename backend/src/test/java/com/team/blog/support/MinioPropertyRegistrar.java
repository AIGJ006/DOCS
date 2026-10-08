package com.team.blog.support;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 테스트 저장소 접속값을 모든 test 프로필 컨텍스트에 넣는다 (003 T014). {@code @DynamicPropertySource}를 테스트 클래스에 두면 컨텍스트
 * 캐시 키가 달라져 시험 전용 컨텍스트(= DB 연결 풀)가 늘어나므로, 기존 컨텍스트에 Bean으로 붙인다.
 *
 * <p>컨테이너는 값을 처음 읽을 때 띄운다({@link MinioContainerSupport#container()}). 공개 주소({@code
 * blog.image.public-base-url})는 바꾸지 않는다 — 002·005 테스트가 기본값 {@code http://localhost:9000/blog}를 기준으로
 * 쓴다. 저장소 경로를 시험할 때는 {@link StorageIntegrationTestBase}가 실제 주소를 쓴다.
 */
@Profile("test")
@Component
public class MinioPropertyRegistrar implements DynamicPropertyRegistrar {

    @Override
    public void accept(DynamicPropertyRegistry registry) {
        registry.add("blog.image.storage.endpoint", MinioContainerSupport::endpoint);
        registry.add("blog.image.storage.presign-endpoint", MinioContainerSupport::endpoint);
        registry.add("blog.image.storage.bucket", () -> MinioContainerSupport.BUCKET);
        registry.add("blog.image.storage.access-key", () -> MinioContainerSupport.APP_KEY);
        registry.add("blog.image.storage.secret-key", () -> MinioContainerSupport.APP_SECRET);
    }
}
