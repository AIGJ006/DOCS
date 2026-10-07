package com.team.blog.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 베이스: 실제 PostgreSQL + Redis(Testcontainers, H2 금지 — constitution VIII, R-35).
 *
 * <p>컨테이너는 static 싱글턴으로 한 번만 띄우고 모든 테스트 클래스가 재사용한다(같은 설정이면 Spring 컨텍스트도 재사용). 이미지 태그는
 * docker-compose.yml과 같게 둔다. 각 테스트 전에 {@code flyway_schema_history}·{@code shedlock}을 뺀 모든 테이블을 비우고
 * Redis를 FLUSHALL 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfiguration.class)
public abstract class IntegrationTestBase {

    public static final String POSTGRES_IMAGE = "postgres:18-alpine";
    public static final String REDIS_IMAGE = "redis:7-alpine";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                            DockerImageName.parse(POSTGRES_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("blog")
                    .withUsername("blog")
                    .withPassword("blog");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    private static final List<String> KEPT_TABLES = List.of("flyway_schema_history", "shedlock");

    @Autowired protected MockMvc mockMvc;

    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected StringRedisTemplate redis;

    @Autowired protected CapturingMailSender mailSender;

    public static PostgreSQLContainer postgres() {
        return POSTGRES;
    }

    public static GenericContainer<?> redisContainer() {
        return REDIS;
    }

    @BeforeEach
    void resetDatabaseAndRedis() {
        RedisOutage.ensureRunning();
        List<String> tables =
                jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                        String.class);
        String targets =
                tables.stream()
                        .filter(t -> !KEPT_TABLES.contains(t))
                        .map(t -> "\"" + t + "\"")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(null);
        if (targets != null) {
            jdbc.execute("TRUNCATE TABLE " + targets + " RESTART IDENTITY CASCADE");
        }
        redis.execute(
                (org.springframework.data.redis.core.RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushAll();
                            return null;
                        });
        mailSender.clear();
    }

    /** 이 테스트에서 쓸 회원 픽스처. */
    protected MemberFixtures members() {
        return new MemberFixtures(jdbc);
    }
}
