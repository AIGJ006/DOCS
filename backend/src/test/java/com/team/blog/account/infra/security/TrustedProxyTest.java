package com.team.blog.account.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.catalina.filters.RemoteIpFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 신뢰 프록시·클라이언트 IP (US5 #7, SC-010, FR-037, R-13). {@code application.yml}의 {@code
 * server.tomcat.remoteip.*} 값으로 Tomcat {@code RemoteIpFilter}(운영의 {@code RemoteIpValve}와 같은 알고리즘·같은
 * 설정 이름)를 만들어 확인한다. 실제 서버(RANDOM_PORT)를 띄우는 테스트 컨텍스트를 하나 더 만들지 않기 위해서다. 같은 IP 요청 제한이 위조 {@code
 * X-Forwarded-For}에 흔들리지 않는 것은 {@code LoginSecurityIntegrationTest}가 확인한다.
 */
class TrustedProxyTest {

    /**
     * {@code RemoteIpFilter}가 HTTPS 판정을 남기는 요청 속성. Tomcat의 실제 요청은 이 값으로 {@code isSecure()}를 답하고, 모의
     * 요청은 속성만 남는다.
     */
    private static final String SECURE_ATTRIBUTE =
            "org.apache.catalina.filters.RemoteIpFilter.secure";

    private RemoteIpFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        for (PropertySource<?> source :
                new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"))) {
            env.getPropertySources().addLast(source);
        }
        MockFilterConfig config = new MockFilterConfig();
        config.addInitParameter(
                "internalProxies",
                env.getRequiredProperty("server.tomcat.remoteip.internal-proxies"));
        config.addInitParameter(
                "remoteIpHeader",
                env.getRequiredProperty("server.tomcat.remoteip.remote-ip-header"));
        config.addInitParameter(
                "protocolHeader",
                env.getRequiredProperty("server.tomcat.remoteip.protocol-header"));
        filter = new RemoteIpFilter();
        filter.init(config);
    }

    private HttpServletRequest pass(MockHttpServletRequest request) throws Exception {
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> seen.set((HttpServletRequest) req));
        return seen.get();
    }

    @Test
    @DisplayName("신뢰 대역 밖에서 온 X-Forwarded-For는 무시한다")
    void untrustedRemoteIgnoresForwardedFor() throws Exception {
        for (int i = 0; i < 21; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("203.0.113.9");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            assertThat(ClientIp.of(pass(request))).isEqualTo("203.0.113.9");
        }
    }

    @Test
    @DisplayName("신뢰 대역(사설) 프록시 + X-Forwarded-For: 203.0.113.5, 10.0.0.2 → 클라이언트 203.0.113.5")
    void trustedProxyChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2");
        request.addHeader("X-Forwarded-Proto", "https");
        HttpServletRequest seen = pass(request);
        assertThat(ClientIp.of(seen)).isEqualTo("203.0.113.5");
        assertThat(seen.getScheme()).isEqualTo("https");
        assertThat(seen.getAttribute(SECURE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("신뢰 대역 밖에서 X-Forwarded-Proto: https를 위조해도 HTTPS로 보지 않는다")
    void forgedProtoIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-Proto", "https");
        HttpServletRequest seen = pass(request);
        assertThat(seen.getScheme()).isEqualTo("http");
        assertThat(seen.getAttribute(SECURE_ATTRIBUTE)).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("X-Forwarded-For 왼쪽에 위조 주소를 끼워도 가장 오른쪽 신뢰 밖 주소를 쓴다")
    void spoofedLeftmostIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setRemoteAddr("192.168.0.10");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.44");
        assertThat(ClientIp.of(pass(request))).isEqualTo("203.0.113.44");
    }
}
