package com.team.blog.support.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import com.team.blog.support.fixture.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 권한 매트릭스 CSV 하네스 (004 T022, research R-28, FR-047). 하위 클래스가 CSV를 골라 행마다 {@link #verify}를 부른다.
 *
 * <pre>{@code
 * class PermissionMatrixIT extends AbstractPermissionMatrixIT {
 *     @ParameterizedTest(name = "{0} × {1} × {2} → {3} {4}")
 *     @CsvFileSource(resources = "/permission/post-write.csv", numLinesToSkip = 1)
 *     void postWrite(String actor, String target, String action, String status, String code, String owner)
 *             throws Exception {
 *         verify(actor, target, action, status, code, owner);
 *     }
 * }
 * }</pre>
 *
 * <p>CSV 열: {@code actor,targetState,action,expectedStatus,expectedCode,owner}. {@code
 * expectedStatus}는 HTTP 상태 숫자, 목록 행동이면 {@code INCLUDED}/{@code EXCLUDED}. {@code expectedCode}는 오류
 * 본문 {@code code}(성공이면 비움). 기능별 CSV(예: 006 {@code post-trashed.csv})를 같은 형식으로 더할 수 있다.
 *
 * <p>행마다 DB가 비워진 상태({@link IntegrationTestBase})에서 작성자·대상 글·행위자 세션을 새로 만든다. 실행기가 없는 행동은 {@code
 * pending: <owner>}로 건너뛴다. 쓰기 행동이 거부되면 요청 전후 {@link PostSnapshot}이 같아야 한다(42 §12 #1).
 */
public abstract class AbstractPermissionMatrixIT extends IntegrationTestBase {

    @Autowired private ObjectProvider<PermissionAction> actionBeans;

    /** 행 하나를 준비·실행·비교한다. */
    protected void verify(
            String actor,
            String targetState,
            String action,
            String expectedStatus,
            String expectedCode,
            String owner)
            throws Exception {
        PermissionAction executor = registry().find(action).orElse(null);
        if (executor == null) {
            Assumptions.abort("pending: " + owner);
            return;
        }
        assertThat(executor.owner()).as("CSV owner 열과 실행기 owner").isEqualTo(owner);

        Scenario scenario = arrange(Actor.valueOf(actor), TargetState.valueOf(targetState));
        Optional<PostSnapshot> before =
                scenario.postId() == null
                        ? Optional.empty()
                        : PostSnapshot.take(jdbc, scenario.postId());

        ActionResult result = executor.perform(mockMvc, scenario.session(), scenario.postId());

        String row = actor + " × " + targetState + " × " + action;
        if ("INCLUDED".equals(expectedStatus) || "EXCLUDED".equals(expectedStatus)) {
            assertThat(result.included())
                    .as(row + " 목록 포함 여부")
                    .isEqualTo("INCLUDED".equals(expectedStatus));
        } else {
            assertThat(result.status())
                    .as(row + " 상태 코드")
                    .isEqualTo(Integer.parseInt(expectedStatus));
            if (expectedCode != null && !expectedCode.isBlank()) {
                assertThat(result.code()).as(row + " 오류 code").isEqualTo(expectedCode);
            }
        }
        if (executor.isWrite() && result.status() >= 400 && before.isPresent()) {
            assertThat(PostSnapshot.take(jdbc, scenario.postId()))
                    .as(row + " 거부된 요청 전후 DB 값")
                    .contains(before.get());
        }
    }

    /** 등록된 실행기 (테스트 컨텍스트의 {@link PermissionAction} Bean). */
    protected PermissionActionRegistry registry() {
        return new PermissionActionRegistry(actionBeans.orderedStream().toList());
    }

    /** 행위자·대상을 만든다. 작성자는 매번 새 회원이고, 작성자 본인 행위자({@link Actor#isAuthor()})는 그 계정 상태로 만든다. */
    protected Scenario arrange(Actor actor, TargetState target) {
        long author = createAuthor(actor);
        PostFixtures posts = new PostFixtures(jdbc);
        Long postId =
                switch (target) {
                    case NONE -> null;
                    case NONEXISTENT -> posts.nonexistentId();
                    default -> posts.create(author, target.fixture());
                };
        Long actorId =
                switch (actor) {
                    case ANONYMOUS -> null;
                    case UNVERIFIED -> members().member().emailVerified(false).create();
                    case MEMBER -> members().member().create();
                    case ADMIN -> members().member().role("ADMIN").create();
                    case AUTHOR, UNVERIFIED_AUTHOR, SUSPENDED, WITHDRAWN -> author;
                };
        Cookie session = actorId == null ? null : TestLogin.loginAs(mockMvc, actorId);
        return new Scenario(author, postId, actorId, session);
    }

    private long createAuthor(Actor actor) {
        return switch (actor) {
            case UNVERIFIED_AUTHOR -> members().member().emailVerified(false).create();
            case WITHDRAWN -> members().member().status("WITHDRAWN").create();
            case SUSPENDED -> {
                long id = members().member().create();
                members().suspend(id, Instant.now().plus(7, ChronoUnit.DAYS), "권한 매트릭스 테스트");
                yield id;
            }
            default -> members().member().create();
        };
    }

    /**
     * 준비된 행.
     *
     * @param authorId 대상 글의 작성자
     * @param postId 대상 글 번호 (대상 없음이면 {@code null})
     * @param actorId 행위자 회원 번호 (비회원이면 {@code null})
     * @param session 행위자 세션 쿠키 (비회원이면 {@code null})
     */
    public record Scenario(long authorId, Long postId, Long actorId, Cookie session) {}
}
