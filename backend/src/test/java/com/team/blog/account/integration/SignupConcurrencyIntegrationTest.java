package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 동시 가입 (SC-001, US3 #4·#10, R-09·R-35). 20개 요청을 한꺼번에 보내도 DB UNIQUE가 한 명만 통과시키고, 진 쪽은 선조회와 같은 형식의 칸
 * 오류를 받는다. 500은 0건.
 */
class SignupConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int THREADS = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private List<MockHttpServletResponse> race(IntFunction<SignupRequests.Body> bodyOf)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                SignupRequests.Body body = bodyOf.apply(i);
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return mockMvc.perform(body.request())
                                            .andReturn()
                                            .getResponse();
                                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> f : futures) {
                responses.add(f.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<MockHttpServletResponse> responses, int status) {
        return responses.stream().filter(r -> r.getStatus() == status).count();
    }

    private static JsonNode json(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    @Test
    @DisplayName("같은 이메일 20개 동시 → 계정 1개")
    void sameEmail() throws Exception {
        List<MockHttpServletResponse> responses =
                race(i -> SignupRequests.body("race@example.com", "race_h" + i, "레이스" + i));
        assertThat(count(responses, 201)).isEqualTo(1);
        assertThat(count(responses, 500)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
        for (MockHttpServletResponse r : responses) {
            if (r.getStatus() != 201) {
                assertThat(r.getStatus()).isEqualTo(400);
                assertThat(json(r).at("/errors/0/code").asText())
                        .isEqualTo("EMAIL_ALREADY_REGISTERED");
            }
        }
    }

    @Test
    @DisplayName("같은 주소·다른 이메일 20개 동시 → 201 1건, 나머지는 HANDLE_DUPLICATE + 대안 주소")
    void sameHandle() throws Exception {
        List<MockHttpServletResponse> responses =
                race(i -> SignupRequests.body("h" + i + "@example.com", "samehandle", "주소" + i));
        assertThat(count(responses, 201)).isEqualTo(1);
        assertThat(count(responses, 400)).isEqualTo(THREADS - 1);
        assertThat(count(responses, 500)).isZero();
        for (MockHttpServletResponse r : responses) {
            if (r.getStatus() == 400) {
                JsonNode body = json(r);
                assertThat(body.at("/errors/0/field").asText()).isEqualTo("handle");
                assertThat(body.at("/errors/0/code").asText()).isEqualTo("HANDLE_DUPLICATE");
                String suggestion = body.at("/details/handleSuggestion").asText();
                assertThat(suggestion).isEqualTo("samehandle_2");
                assertThat(body.at("/errors/0/message").asText())
                        .isIn(
                                "방금 다른 분이 이 주소를 사용했어요. `samehandle_2`는 어떠세요?",
                                "이미 사용 중인 주소예요. `samehandle_2`는 어떠세요?");
            }
        }
        assertThat(
                        responses.stream()
                                .filter(r -> r.getStatus() == 400)
                                .anyMatch(
                                        r -> {
                                            try {
                                                return json(r).at("/errors/0/message")
                                                        .asText()
                                                        .startsWith("방금 다른 분이");
                                            } catch (Exception e) {
                                                return false;
                                            }
                                        }))
                .as("UNIQUE 위반으로 진 요청이 하나 이상 있다")
                .isTrue();
    }

    @Test
    @DisplayName("같은 닉네임(대소문자 다름 포함) 20개 동시 → 1건만, 진 쪽은 NICKNAME_DUPLICATE")
    void sameNickname() throws Exception {
        List<MockHttpServletResponse> responses =
                race(
                        i ->
                                SignupRequests.body(
                                        "n" + i + "@example.com",
                                        "nick_h" + i,
                                        i % 2 == 0 ? "RaceNick" : "racenick"));
        assertThat(count(responses, 201)).isEqualTo(1);
        assertThat(count(responses, 500)).isZero();
        for (MockHttpServletResponse r : responses) {
            if (r.getStatus() != 201) {
                JsonNode body = json(r);
                assertThat(body.at("/errors/0/field").asText()).isEqualTo("nickname");
                assertThat(body.at("/errors/0/code").asText()).isEqualTo("NICKNAME_DUPLICATE");
                assertThat(body.at("/errors/0/message").asText())
                        .isIn("방금 다른 분이 이 닉네임을 사용했어요", "이미 사용 중인 닉네임이에요");
            }
        }
    }
}
