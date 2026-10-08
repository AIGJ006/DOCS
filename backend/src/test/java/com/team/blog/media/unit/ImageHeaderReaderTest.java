package com.team.blog.media.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.domain.ImageFormat;
import com.team.blog.media.domain.ImageHeaderReader;
import com.team.blog.media.domain.ImageInspection;
import com.team.blog.media.domain.ImageRejectReason;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 머리말만 읽는 사진 검사 (003 T006, research R6, FR-007·FR-037). 자원은 {@code src/test/resources/images/}.
 *
 * <p>① 매직 바이트 형식 ② 가로·세로 ③ GIF 프레임 수(한도 + 1에서 멈춤) ④ 잘린 파일·SOF 없음 → CORRUPT ⑤ 거대 해상도는 해독 없이 크기만.
 */
class ImageHeaderReaderTest {

    private static final int HEAD = 65536;

    @ParameterizedTest(name = "{0} → {1} {2}x{3}")
    @CsvSource({
        "tiny.jpg, JPEG, 40, 30",
        "tiny.png, PNG, 40, 30",
        "tiny.gif, GIF, 40, 30",
        "tiny-vp8.webp, WEBP, 40, 30",
        "tiny-vp8l.webp, WEBP, 40, 30",
        "tiny-vp8x.webp, WEBP, 40, 30",
        "photo-1920.jpg, JPEG, 1920, 1440",
        "photo-1920.webp, WEBP, 1920, 1440",
        "profile-256.webp, WEBP, 256, 256",
        "wide-4097.jpg, JPEG, 4097, 30",
        "wide-1921.gif, GIF, 1921, 10",
    })
    void 매직_바이트로_형식과_가로_세로를_읽는다(String file, ImageFormat format, int width, int height) {
        ImageInspection result = inspect(file);

        assertThat(result.rejectReason()).isEmpty();
        assertThat(result.format()).isEqualTo(format);
        assertThat(result.width()).isEqualTo(width);
        assertThat(result.height()).isEqualTo(height);
    }

    @Test
    void 확장자가_아니라_내용으로_판별한다() {
        // PNG 바이트를 .jpg 이름으로 저장한 파일
        assertThat(inspect("png-named.jpg").format()).isEqualTo(ImageFormat.PNG);
    }

    @Test
    void 알_수_없는_형식은_형식_없음으로_거부한다() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        ImageInspection result = ImageHeaderReader.inspect(svg);
        assertThat(result.format()).isNull();
        assertThat(result.rejectReason()).contains(ImageRejectReason.CORRUPT);
    }

    @ParameterizedTest
    @CsvSource({"truncated.jpg", "truncated.png", "truncated.gif", "sof-after-64k.jpg"})
    void 잘린_파일과_앞부분에_SOF가_없는_JPEG는_CORRUPT(String file) {
        assertThat(inspect(file).rejectReason()).contains(ImageRejectReason.CORRUPT);
    }

    @Test
    void GIF_프레임_수를_세고_한도_다음_장에서_멈춘다() throws IOException {
        assertThat(ImageHeaderReader.countGifFrames(open("tiny.gif"), 300)).isEqualTo(3);
        assertThat(ImageHeaderReader.countGifFrames(open("frames-300.gif"), 300)).isEqualTo(300);

        CountingStream counting = new CountingStream(open("frames-301.gif"));
        assertThat(ImageHeaderReader.countGifFrames(counting, 300)).isEqualTo(301);
        // 301번째 프레임 기술자에서 멈춘다 — 끝(트레일러)까지 읽지 않는다
        assertThat(counting.read.get()).isLessThan(resource("frames-301.gif").length);

        assertThat(ImageHeaderReader.countGifFrames(open("truncated.gif"), 300)).isEqualTo(-1);
        assertThat(ImageHeaderReader.countGifFrames(open("tiny.png"), 300)).isEqualTo(-1);
    }

    @Test
    void 거대_해상도는_해독하지_않고_머리말만_읽는다() {
        byte[] bytes = resource("huge-header.png");
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();

        ImageInspection result = ImageHeaderReader.inspect(new ByteArrayInputStream(bytes), HEAD);

        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertThat(result.width()).isEqualTo(30000);
        assertThat(result.height()).isEqualTo(30000);
        // 30000×30000×3바이트(약 2.7GB)를 해독했다면 여기까지 오지 못한다. 읽기 버퍼(64KB)와 잡비만 쓴다
        assertThat(allocated).isLessThan(1_048_576L);
    }

    @Test
    void 최대_읽기_바이트까지만_읽는다() {
        CountingStream counting = new CountingStream(open("sof-after-64k.jpg"));
        ImageHeaderReader.inspect(counting, HEAD);
        assertThat(counting.read.get()).isLessThanOrEqualTo(HEAD);
    }

    private static ImageInspection inspect(String file) {
        return ImageHeaderReader.inspect(open(file), HEAD);
    }

    static InputStream open(String file) {
        return new ByteArrayInputStream(resource(file));
    }

    static byte[] resource(String file) {
        try (InputStream in = ImageHeaderReaderTest.class.getResourceAsStream("/images/" + file)) {
            if (in == null) {
                throw new IllegalArgumentException("자원 없음: " + file);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 읽은 바이트 수를 센다. */
    static final class CountingStream extends java.io.FilterInputStream {
        final AtomicLong read = new AtomicLong();

        CountingStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                read.incrementAndGet();
            }
            return b;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            read.addAndGet(Math.max(0, skipped));
            return skipped;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                read.addAndGet(n);
            }
            return n;
        }
    }
}
