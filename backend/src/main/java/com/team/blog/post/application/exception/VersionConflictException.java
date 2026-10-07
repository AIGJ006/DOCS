package com.team.blog.post.application.exception;

import com.team.blog.post.domain.PostReasonCode;
import com.team.blog.post.domain.ServerCopy;
import com.team.blog.shared.error.BusinessRuleException;
import java.util.Map;

/** 기준 버전 ≠ 현재 버전 → 409 {@code VERSION_CONFLICT}, {@code details.server = ServerCopy} (FR-012). */
public class VersionConflictException extends BusinessRuleException {

    private final ServerCopy server;

    public VersionConflictException(ServerCopy server) {
        super(PostReasonCode.VERSION_CONFLICT, Map.of("server", server));
        this.server = server;
    }

    public ServerCopy server() {
        return server;
    }
}
