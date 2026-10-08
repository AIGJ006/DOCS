package com.team.blog.interaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.team.blog.interaction.application.ViewFlushJob;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 조회수 기록에 원래 IP·방문자 쿠키 값이 남지 않는다 (009 T028, SC-010, FR-034, research R7). 조회수 기능이 만드는 기록(Redis 키·값,
 * {@code post_view_daily}, 조회수 로그)만 본다 — 서버 접속 로그·로그인 제한 키는 범위 밖(Clarifications Q2).
 *
 * <p>(구현 메모) MockMvc는 Tomcat {@code RemoteIpValve}를 거치지 않으므로 신뢰 프록시의 {@code X-Forwarded-For}를 푼
 * 결과({@code getRemoteAddr()})를 요청에 직접 넣는다 — {@code ClientIp.of}가 읽는 값과 같다.
 */
@ExtendWith(OutputCaptureExtension.class)
class ViewPrivacyIT extends IntegrationTestBase {

    private static final String IP = "203.0.113.77";
    private static final String VID = "5b7e2a91-0c3d-4f6e-9a8b-7c6d5e4f3a21";
    private static final String UA = "Mozilla/5.0 PrivacyTest";
    private static final List<String> LOGGERS = List.of("com.team.blog", "org.springframework.web");

    @Autowired LoggingSystem loggingSystem;
    @Autowired ViewFlushJob flushJob;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            request.addHeader("X-Forwarded-For", ip);
            return request;
        };
    }

    @Test
    void IP와_vid가_Redis_DB_로그_어디에도_없다(CapturedOutput output) throws Exception {
        long author = members().member().create();
        PostFixtures posts = new PostFixtures(jdbc);
        long postId = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        long second = posts.create(author, PostFixtures.State.PUBLISHED_PUBLIC);
        Map<String, LogLevel> previous = new HashMap<>();
        for (String logger : LOGGERS) {
            var config = loggingSystem.getLoggerConfiguration(logger);
            previous.put(logger, config == null ? null : config.getConfiguredLevel());
            loggingSystem.setLogLevel(logger, LogLevel.DEBUG);
        }
        try {
            mockMvc.perform(get("/api/posts/{id}", postId).with(from(IP)).header("User-Agent", UA))
                    .andReturn();
            for (long id : new long[] {postId, postId, second}) {
                mockMvc.perform(
                                TestLogin.withCsrf(post("/api/posts/{id}/views", id), null)
                                        .with(from(IP))
                                        .header("User-Agent", UA)
                                        .cookie(new Cookie("vid", VID)))
                        .andReturn();
                // 쿠키를 막은 방문자(IP·UA 해시 경로)
                mockMvc.perform(
                                TestLogin.withCsrf(post("/api/posts/{id}/views", id), null)
                                        .with(from(IP))
                                        .header("User-Agent", UA))
                        .andReturn();
            }
            // 반영 전 Redis 덤프
            List<String> beforeFlush = dumpRedis();
            flushJob.flush();
            List<String> afterFlush = dumpRedis();

            assertThat(beforeFlush)
                    .as("반영 전 Redis에 조회 기록이 있다")
                    .anyMatch(s -> s.startsWith("view:"));
            for (String secret : List.of(IP, VID)) {
                assertThat(beforeFlush).as("반영 전 Redis 키·값").noneMatch(s -> s.contains(secret));
                assertThat(afterFlush).as("반영 후 Redis 키·값").noneMatch(s -> s.contains(secret));
                assertThat(
                                jdbc.queryForList("SELECT * FROM post_view_daily").stream()
                                        .map(Object::toString)
                                        .toList())
                        .as("post_view_daily")
                        .noneMatch(s -> s.contains(secret));
                assertThat(viewLogLines(output.getAll()))
                        .as("조회수 로그")
                        .noneMatch(l -> l.contains(secret));
            }
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT view_count FROM post WHERE id = ?", Long.class, postId))
                    .isEqualTo(2);
        } finally {
            previous.forEach(loggingSystem::setLogLevel);
        }
    }

    /** 조회수 기능(interaction)이 남긴 로그 줄. */
    private static List<String> viewLogLines(String all) {
        return all.lines().filter(l -> l.contains("interaction") || l.contains("조회")).toList();
    }

    private List<String> dumpRedis() {
        List<String> out = new ArrayList<>();
        try (Cursor<String> cursor =
                redis.scan(ScanOptions.scanOptions().match("*").count(1000).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                out.add(key);
                DataType type = redis.type(key);
                if (type == DataType.STRING) {
                    out.add(key + "=" + redis.opsForValue().get(key));
                } else if (type == DataType.HASH) {
                    redis.opsForHash()
                            .entries(key)
                            .forEach((k, v) -> out.add(key + "." + k + "=" + v));
                }
            }
        }
        return out;
    }
}
