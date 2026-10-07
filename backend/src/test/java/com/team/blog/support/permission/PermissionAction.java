package com.team.blog.support.permission;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 권한 매트릭스 CSV의 {@code action} 열 하나를 실행하는 실행기 (004 T022, research R-28).
 *
 * <p>각 기능은 자기 행동의 실행기를 테스트 소스에 {@code @Component}로 두어 등록한다(통합 테스트 컨텍스트의 컴포넌트 스캔이 찾는다). 실행기가 없는 행은
 * {@link PermissionActionRegistry}가 {@code pending: <owner>}로 건너뛴다.
 *
 * <pre>{@code
 * @Component
 * class TrashPostAction implements PermissionAction {
 *     public String name() { return "post.trash"; }
 *     public String owner() { return "006"; }
 *     public ActionResult perform(MockMvc mvc, Cookie session, Long postId) throws Exception {
 *         return ActionResult.of(mvc.perform(TestLogin.withCsrf(delete("/api/posts/{id}", postId), session)).andReturn());
 *     }
 * }
 * }</pre>
 */
public interface PermissionAction {

    /**
     * CSV {@code action} 열 값 (예: {@code post.read}, {@code post.visibility}, {@code post.trash}).
     */
    String name();

    /** 소유 스펙 번호 (예: {@code 004}). CSV {@code owner} 열과 같아야 한다. */
    String owner();

    /**
     * 쓰기 행동인가. 쓰기 행동이 거부되면(4xx) 하네스가 요청 전후 {@link PostSnapshot}이 같은지 확인한다(42 §12 #1). 읽기·목록 행동은
     * {@code false}.
     */
    default boolean isWrite() {
        return true;
    }

    /**
     * 행동을 실행한다.
     *
     * @param session 행위자의 {@code SESSION} 쿠키 (비회원이면 {@code null}). 상태를 바꾸는 요청은 {@code
     *     TestLogin.withCsrf}로 CSRF 쿠키·헤더를 붙인다
     * @param postId 대상 글 번호 (대상 없음이면 {@code null})
     */
    ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception;
}
