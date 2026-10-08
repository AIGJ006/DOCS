package com.team.blog.support;

import org.junit.jupiter.api.BeforeEach;

/**
 * 사진 저장소가 필요한 통합 테스트 베이스 (003 T014). {@link IntegrationTestBase}와 같은 Spring 컨텍스트를 쓰고(저장소 접속값은
 * {@link MinioPropertyRegistrar}가 이미 넣음), 테스트마다 저장소의 {@code images/} 객체를 비운다.
 */
public abstract class StorageIntegrationTestBase extends IntegrationTestBase {

    @BeforeEach
    void clearStorage() {
        MinioContainerSupport.clearImages();
    }
}
