package com.team.blog.media.domain;

import java.util.Optional;

/**
 * 머리말 검사 결과.
 *
 * @param format 매직 바이트 형식 (모르는 형식이면 {@code null})
 * @param width 가로 (읽지 못했으면 0)
 * @param height 세로 (읽지 못했으면 0)
 * @param frames GIF가 읽은 범위 안에서 센 프레임 수 (GIF가 아니면 1, 읽지 못했으면 0). 전체 수는 {@link
 *     ImageHeaderReader#countGifFrames}
 * @param rejectReason 머리말 단계의 거부 사유 ({@link ImageRejectReason#CORRUPT}만 나온다)
 */
public record ImageInspection(
        ImageFormat format,
        int width,
        int height,
        int frames,
        Optional<ImageRejectReason> rejectReason) {

    public ImageInspection {
        rejectReason = rejectReason == null ? Optional.empty() : rejectReason;
    }

    static ImageInspection ok(ImageFormat format, int width, int height, int frames) {
        if (width <= 0 || height <= 0) {
            return corrupt(format);
        }
        return new ImageInspection(format, width, height, frames, Optional.empty());
    }

    static ImageInspection corrupt(ImageFormat format) {
        return new ImageInspection(format, 0, 0, 0, Optional.of(ImageRejectReason.CORRUPT));
    }

    public boolean ok() {
        return rejectReason.isEmpty();
    }

    public long pixels() {
        return (long) width * height;
    }
}
