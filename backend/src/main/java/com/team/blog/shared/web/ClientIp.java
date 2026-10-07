package com.team.blog.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP (FR-037, R-13, H4). Tomcat {@code RemoteIpValve}({@code
 * server.forward-headers-strategy=native}, {@code server.tomcat.remoteip.*})가 신뢰 프록시에서 온 요청일 때만
 * {@code X-Forwarded-For}의 가장 오른쪽 신뢰 밖 주소로 정한 {@code getRemoteAddr()}를 그대로 쓴다. 헤더를 직접 읽지 않는다 — 위조
 * 헤더로 요청 제한을 우회할 수 없게 하기 위해서다. 모든 "같은 IP" 제한 키는 이 값을 쓴다.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
