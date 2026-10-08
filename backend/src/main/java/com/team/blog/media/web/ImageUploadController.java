package com.team.blog.media.web;

import com.team.blog.media.application.ImageUploadService;
import com.team.blog.media.web.dto.ImageUploadTicketResponse;
import com.team.blog.media.web.dto.PresignRequest;
import com.team.blog.media.web.dto.UploadedImageResponse;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사진 업로드 API (003 T030, contracts/openapi.yaml {@code presignImageUpload}·{@code
 * completeImageUpload}). 로그인은 보안 설정이 확인한다(비회원 401). 업로드 주소(서명 쿼리)는 응답에만 싣고 로그에 남기지 않는다.
 */
@RestController
public class ImageUploadController {

    private final ImageUploadService uploads;

    public ImageUploadController(ImageUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/api/images/presign")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadTicketResponse presign(
            @CurrentUser Long memberId, @RequestBody PresignRequest request) {
        return ImageUploadTicketResponse.of(uploads.presign(memberId, request.toCommand()));
    }

    @PostMapping("/api/images/{imageId}/complete")
    public UploadedImageResponse complete(@CurrentUser Long memberId, @PathVariable long imageId) {
        return UploadedImageResponse.of(uploads.complete(memberId, imageId));
    }
}
