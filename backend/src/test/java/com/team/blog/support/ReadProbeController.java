package com.team.blog.support;

import com.team.blog.post.application.PostReadService;
import com.team.blog.post.infra.PostView;
import com.team.blog.shared.security.Viewer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 004 읽기 판정 확인용 테스트 컨트롤러 (test 프로필 전용): {@code GET /api/__test/posts/{postId}} → {@link
 * PostReadService#requireReadable}. 005 상세 API가 생기기 전에 권한 매트릭스의 읽기 행({@code post.read})을 실행한다.
 *
 * <p>{@code /api/**} 아래에 두어 001 {@code WithdrawnAccountGateFilter}(탈퇴 유예 403)가 실제 API처럼 적용된다.
 */
@Profile("test")
@RestController
public class ReadProbeController {

    private final PostReadService postReadService;

    public ReadProbeController(PostReadService postReadService) {
        this.postReadService = postReadService;
    }

    /** 컨트롤러가 받은 {@link Viewer} (004 {@code CurrentViewerResolver} 확인용). */
    @GetMapping("/api/__test/viewer")
    public Map<String, Object> viewer(Viewer viewer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", viewer.isAuthenticated());
        body.put("id", viewer.id());
        body.put("role", viewer.role());
        body.put("status", viewer.status());
        body.put("emailVerified", viewer.emailVerified());
        return body;
    }

    @GetMapping("/api/__test/posts/{postId}")
    public Map<String, Object> read(@PathVariable long postId, Viewer viewer) {
        PostView post = postReadService.requireReadable(postId, viewer);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", post.id());
        body.put("status", post.status());
        body.put("visibility", post.visibility());
        return body;
    }

    /** 받은 본문과 매개변수를 그대로 돌려준다 (004 T045 {@code OwnerFieldInjector}가 실제 요청에 작성자 번호를 끼워 넣는지 확인용). */
    @PostMapping("/api/__test/echo")
    public Map<String, Object> echo(
            @RequestBody(required = false) String body, @RequestParam Map<String, String> params) {
        Map<String, Object> echoed = new LinkedHashMap<>();
        echoed.put("body", body);
        echoed.put("params", params);
        return echoed;
    }
}
