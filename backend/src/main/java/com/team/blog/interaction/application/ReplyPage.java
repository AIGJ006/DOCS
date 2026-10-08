package com.team.blog.interaction.application;

import java.util.List;

/** 답글 펼치기 한 번 (contracts {@code ReplyPage}). */
public record ReplyPage(List<CommentView> items, String nextCursor) {}
