package com.team.blog.media.permission;

import com.team.blog.media.support.ImageApi;
import com.team.blog.support.permission.ActionResult;
import com.team.blog.support.permission.PermissionAction;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** 003 권한 매트릭스 실행기 {@code image.presign}: POST /api/images/presign — 글 사진 업로드 준비 (T023·T031). */
@Profile("test")
@Component
public class PresignImageAction implements PermissionAction {

    @Override
    public String name() {
        return "image.presign";
    }

    @Override
    public String owner() {
        return "003";
    }

    @Override
    public ActionResult perform(MockMvc mockMvc, Cookie session, Long postId) throws Exception {
        return ActionResult.of(
                new ImageApi(mockMvc)
                        .presign(
                                session,
                                ImageApi.postBody("image/webp", 412_345, "image/webp", 38_211)));
    }
}
