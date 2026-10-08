package com.team.blog.interaction.infra;

/** 조회 반영 중 Redis를 쓸 수 없음 — 그 회차를 끝내고 다음 회차를 기다린다 (009 research R8). */
public class ViewStoreUnavailableException extends RuntimeException {

    public ViewStoreUnavailableException() {
        super("조회수 Redis를 쓸 수 없습니다");
    }
}
