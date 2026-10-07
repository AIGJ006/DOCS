package com.team.blog.shared.security;

import com.team.blog.account.application.MemberAccessInfo;
import com.team.blog.account.application.MemberQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러의 {@link Viewer} 파라미터를 채운다 (004 T011, research R-07·R-23).
 *
 * <ul>
 *   <li>입력은 세션의 {@link MemberPrincipal}(회원 번호)뿐이다. 요청 본문·쿼리의 {@code authorId}·{@code memberId}는 읽지
 *       않는다(FR-026).
 *   <li>역할·상태·이메일 인증 여부는 001 {@link MemberQueryService#findAccessInfo(long)}(PK 1번)로 읽는다. 001
 *       {@code WithdrawnAccountGateFilter}가 같은 요청에서 이미 읽어 {@link
 *       MemberAccessInfo#REQUEST_ATTRIBUTE}에 두었으면 다시 조회하지 않는다.
 *   <li>세션이 없거나(세션 저장소 장애 포함 — 001 {@code ResilientSessionRepository}) 회원이 없거나 익명 처리됐으면 {@link
 *       Viewer#anonymous()}.
 *   <li>같은 요청에서는 request attribute {@link #ATTRIBUTE}에 두고 한 번만 만든다.
 * </ul>
 *
 * Service 등 컨트롤러 밖에서는 {@link #current()}를 쓴다. 등록은 {@link ViewerWebMvcConfig}.
 */
@Component
public class CurrentViewerResolver implements HandlerMethodArgumentResolver {

    /** request attribute 이름 — 값은 {@link Viewer}. */
    public static final String ATTRIBUTE = Viewer.class.getName();

    /** 지연 조회: {@code @WebMvcTest} 슬라이스처럼 account Service가 없는 컨텍스트에서도 이 Bean은 만들어진다. */
    private final ObjectProvider<MemberQueryService> memberQueryService;

    public CurrentViewerResolver(ObjectProvider<MemberQueryService> memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Viewer.class;
    }

    @Override
    public Viewer resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return request == null ? build(null) : resolve(request);
    }

    /** 지금 요청의 {@link Viewer}. 요청 밖(배치 등)이면 비회원. */
    public Viewer current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return resolve(attrs.getRequest());
        }
        return Viewer.anonymous();
    }

    /** 그 요청의 {@link Viewer} (같은 요청에서는 한 번만 만든다). */
    public Viewer resolve(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof Viewer cached) {
            return cached;
        }
        Viewer viewer = build(request);
        request.setAttribute(ATTRIBUTE, viewer);
        return viewer;
    }

    private Viewer build(HttpServletRequest request) {
        Optional<MemberPrincipal> principal = MemberPrincipal.current();
        if (principal.isEmpty()) {
            return Viewer.anonymous();
        }
        long memberId = principal.get().memberId();
        Object attribute =
                request == null ? null : request.getAttribute(MemberAccessInfo.REQUEST_ATTRIBUTE);
        Optional<MemberAccessInfo> info =
                attribute instanceof MemberAccessInfo cached
                        ? Optional.of(cached)
                        : memberQueryService.getObject().findAccessInfo(memberId);
        return info.map(a -> new Viewer(memberId, a.role(), a.status(), a.emailVerified()))
                .orElseGet(Viewer::anonymous);
    }
}
