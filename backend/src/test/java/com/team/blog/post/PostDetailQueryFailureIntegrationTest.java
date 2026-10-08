package com.team.blog.post;

import static com.team.blog.discovery.support.ReadingApi.read;
import static com.team.blog.discovery.support.ReadingApi.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;

import com.team.blog.discovery.support.ReadingApi;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/** 글+작성자 조회가 실패하면 상세도 실패한다 (005 T026 뒷부분, 원칙 V — 부가 기능만 기본값으로 대체한다). */
class PostDetailQueryFailureIntegrationTest extends IntegrationTestBase {

    @MockitoBean PostQueryRepository posts;

    @Test
    void 글_작성자_조회가_실패하면_500이다() throws Exception {
        willThrow(new IllegalStateException("DB 장애")).given(posts).findDetailRow(anyLong());

        MvcResult result = new ReadingApi(mockMvc).detail(null, 1);

        assertThat(status(result)).isEqualTo(500);
        assertThat((String) read(result, "$.code")).isEqualTo("INTERNAL_ERROR");
    }
}
