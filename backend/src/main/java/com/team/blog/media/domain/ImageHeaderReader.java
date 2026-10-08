package com.team.blog.media.domain;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * 사진 머리말만 읽는 검사기 (research R6, FR-007·FR-037). 파일 전체를 해독하지 않으므로 머리말에 거대 해상도를 적은 파일로도 메모리를 쓰지 않고,
 * JDK {@code ImageIO}에 없는 WebP도 읽는다.
 *
 * <ul>
 *   <li>형식: 매직 바이트 ({@link ImageFormat#detect})
 *   <li>JPEG: SOF0~SOF15 마커(DHT {@code C4}·JPG {@code C8}·DAC {@code CC} 제외). 읽은 범위 안에 SOF가 없거나
 *       SOS가 먼저 나오면 손상으로 본다 — 브라우저가 다시 그린 파일은 EXIF가 없어 SOF가 앞에 있다
 *   <li>PNG: 첫 청크 IHDR, GIF: 논리 화면 크기, WebP: {@code VP8 }(키 프레임)·{@code VP8L}(14비트)·{@code
 *       VP8X}(24비트)
 *   <li>GIF 프레임: 블록 구조를 따라가며 이미지 기술자({@code 0x2C})를 센다. 한도 + 1장째에서 바로 멈춘다
 * </ul>
 *
 * 상태가 없는 순수 함수다(스레드 안전).
 */
public final class ImageHeaderReader {

    private ImageHeaderReader() {}

    /**
     * 스트림 앞부분 {@code maxBytes}까지만 읽어 검사한다. 스트림은 닫지 않는다.
     *
     * @param maxBytes 최대 읽기 바이트 ({@code blog.image.inspect-head-bytes}, 기본 64KB)
     */
    public static ImageInspection inspect(InputStream in, int maxBytes) {
        try {
            return inspect(readUpTo(in, maxBytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 이미 읽은 앞부분을 검사한다. */
    public static ImageInspection inspect(byte[] head) {
        ImageFormat format = ImageFormat.detect(head).orElse(null);
        if (format == null) {
            return ImageInspection.corrupt(null);
        }
        try {
            return switch (format) {
                case JPEG -> jpeg(head);
                case PNG -> png(head);
                case GIF -> gif(head);
                case WEBP -> webp(head);
            };
        } catch (ArrayIndexOutOfBoundsException e) {
            return ImageInspection.corrupt(format);
        }
    }

    /**
     * GIF 전체를 스트림으로 한 번 읽으며 프레임을 센다. 파일 전체를 메모리에 올리지 않는다(블록 단위로 건너뜀).
     *
     * @param limit 허용 프레임 수 ({@code blog.image.gif-max-frames})
     * @return 프레임 수. {@code limit}를 넘으면 {@code limit + 1}을 돌려주고 더 읽지 않는다. GIF가 아니거나 구조가 깨졌으면 {@code
     *     -1}
     */
    public static int countGifFrames(InputStream in, int limit) throws IOException {
        byte[] header = in.readNBytes(13);
        if (header.length < 13 || ImageFormat.detect(header).orElse(null) != ImageFormat.GIF) {
            return -1;
        }
        int packed = header[10] & 0xFF;
        if ((packed & 0x80) != 0) {
            if (!skip(in, 3L * (1 << ((packed & 0x07) + 1)))) {
                return -1;
            }
        }
        int frames = 0;
        while (true) {
            int block = in.read();
            switch (block) {
                case 0x2C -> {
                    frames++;
                    if (frames > limit) {
                        return frames;
                    }
                    byte[] descriptor = in.readNBytes(9);
                    if (descriptor.length < 9) {
                        return -1;
                    }
                    int flags = descriptor[8] & 0xFF;
                    if ((flags & 0x80) != 0 && !skip(in, 3L * (1 << ((flags & 0x07) + 1)))) {
                        return -1;
                    }
                    if (in.read() < 0 || !skipSubBlocks(in)) { // LZW 최소 코드 크기 + 데이터
                        return -1;
                    }
                }
                case 0x21 -> {
                    if (in.read() < 0 || !skipSubBlocks(in)) { // 확장 종류 + 데이터
                        return -1;
                    }
                }
                case 0x3B -> {
                    return frames;
                }
                default -> {
                    return -1;
                }
            }
        }
    }

    // ---- 형식별 머리말 ----

    private static ImageInspection jpeg(byte[] d) {
        int i = 2;
        while (i + 3 < d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                return ImageInspection.corrupt(ImageFormat.JPEG);
            }
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xFF) { // 채움 바이트
                i++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // 길이 없는 마커
                i += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) { // SOS·EOI가 SOF보다 먼저
                return ImageInspection.corrupt(ImageFormat.JPEG);
            }
            int length = u16be(d, i + 2);
            if (length < 2) {
                return ImageInspection.corrupt(ImageFormat.JPEG);
            }
            if (isSof(marker)) {
                if (i + 9 > d.length) {
                    break;
                }
                int height = u16be(d, i + 5);
                int width = u16be(d, i + 7);
                return ImageInspection.ok(ImageFormat.JPEG, width, height, 1);
            }
            i += 2 + length;
        }
        return ImageInspection.corrupt(ImageFormat.JPEG);
    }

    private static boolean isSof(int marker) {
        return marker >= 0xC0
                && marker <= 0xCF
                && marker != 0xC4
                && marker != 0xC8
                && marker != 0xCC;
    }

    private static ImageInspection png(byte[] d) {
        if (d.length < 24 || d[12] != 'I' || d[13] != 'H' || d[14] != 'D' || d[15] != 'R') {
            return ImageInspection.corrupt(ImageFormat.PNG);
        }
        long width = u32be(d, 16);
        long height = u32be(d, 20);
        if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            return new ImageInspection(
                    ImageFormat.PNG,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    1,
                    java.util.Optional.empty());
        }
        return ImageInspection.ok(ImageFormat.PNG, (int) width, (int) height, 1);
    }

    private static ImageInspection gif(byte[] d) {
        if (d.length < 13) {
            return ImageInspection.corrupt(ImageFormat.GIF);
        }
        int width = u16le(d, 6);
        int height = u16le(d, 8);
        int frames;
        try {
            frames = countGifFrames(new java.io.ByteArrayInputStream(d), Integer.MAX_VALUE - 1);
        } catch (IOException e) {
            frames = -1;
        }
        if (frames < 0) {
            // 앞부분만 읽었으면 끝까지 가지 못한 것이 정상일 수 있다 — 최소 한 장이 보이면 통과
            frames = framesSeen(d);
            if (frames == 0) {
                return ImageInspection.corrupt(ImageFormat.GIF);
            }
        }
        if (frames == 0) {
            return ImageInspection.corrupt(ImageFormat.GIF);
        }
        return ImageInspection.ok(ImageFormat.GIF, width, height, frames);
    }

    /** 잘린 GIF에서 트레일러 전까지 센 프레임 수 (구조가 깨진 지점 앞까지). */
    private static int framesSeen(byte[] d) {
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(d);
        int[] count = {0};
        try {
            countGifFramesInto(in, count);
        } catch (IOException ignored) {
            // 메모리 스트림
        }
        return count[0];
    }

    private static void countGifFramesInto(InputStream in, int[] count) throws IOException {
        byte[] header = in.readNBytes(13);
        int packed = header[10] & 0xFF;
        if ((packed & 0x80) != 0 && !skip(in, 3L * (1 << ((packed & 0x07) + 1)))) {
            return;
        }
        while (true) {
            int block = in.read();
            if (block == 0x2C) {
                byte[] descriptor = in.readNBytes(9);
                if (descriptor.length < 9) {
                    return;
                }
                int flags = descriptor[8] & 0xFF;
                if ((flags & 0x80) != 0 && !skip(in, 3L * (1 << ((flags & 0x07) + 1)))) {
                    return;
                }
                if (in.read() < 0 || !skipSubBlocks(in)) {
                    return;
                }
                count[0]++;
            } else if (block == 0x21) {
                if (in.read() < 0 || !skipSubBlocks(in)) {
                    return;
                }
            } else {
                return;
            }
        }
    }

    private static ImageInspection webp(byte[] d) {
        if (d.length < 30) {
            return ImageInspection.corrupt(ImageFormat.WEBP);
        }
        String chunk = new String(d, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8 " -> {
                // 프레임 태그 3바이트 뒤 시작 코드 9D 01 2A, 이어서 14비트 가로·세로
                int p = 20 + 3;
                if ((d[p] & 0xFF) != 0x9D
                        || (d[p + 1] & 0xFF) != 0x01
                        || (d[p + 2] & 0xFF) != 0x2A) {
                    return ImageInspection.corrupt(ImageFormat.WEBP);
                }
                int width = u16le(d, p + 3) & 0x3FFF;
                int height = u16le(d, p + 5) & 0x3FFF;
                return ImageInspection.ok(ImageFormat.WEBP, width, height, 1);
            }
            case "VP8L" -> {
                if ((d[20] & 0xFF) != 0x2F) {
                    return ImageInspection.corrupt(ImageFormat.WEBP);
                }
                long bits = u32le(d, 21);
                int width = (int) (bits & 0x3FFF) + 1;
                int height = (int) ((bits >> 14) & 0x3FFF) + 1;
                return ImageInspection.ok(ImageFormat.WEBP, width, height, 1);
            }
            case "VP8X" -> {
                int width = u24le(d, 24) + 1;
                int height = u24le(d, 27) + 1;
                return ImageInspection.ok(ImageFormat.WEBP, width, height, 1);
            }
            default -> {
                return ImageInspection.corrupt(ImageFormat.WEBP);
            }
        }
    }

    // ---- 바이트 도우미 ----

    private static byte[] readUpTo(InputStream in, int maxBytes) throws IOException {
        byte[] buffer = new byte[Math.max(0, maxBytes)];
        int total = 0;
        while (total < buffer.length) {
            int n = in.read(buffer, total, buffer.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total == buffer.length ? buffer : Arrays.copyOf(buffer, total);
    }

    private static boolean skipSubBlocks(InputStream in) throws IOException {
        while (true) {
            int size = in.read();
            if (size < 0) {
                return false;
            }
            if (size == 0) {
                return true;
            }
            if (!skip(in, size)) {
                return false;
            }
        }
    }

    private static boolean skip(InputStream in, long n) throws IOException {
        long left = n;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    return false;
                }
                skipped = 1;
            }
            left -= skipped;
        }
        return true;
    }

    private static int u16be(byte[] d, int i) {
        return ((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF);
    }

    private static int u16le(byte[] d, int i) {
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8);
    }

    private static int u24le(byte[] d, int i) {
        return (d[i] & 0xFF) | ((d[i + 1] & 0xFF) << 8) | ((d[i + 2] & 0xFF) << 16);
    }

    private static long u32be(byte[] d, int i) {
        return ((long) (d[i] & 0xFF) << 24)
                | ((d[i + 1] & 0xFF) << 16)
                | ((d[i + 2] & 0xFF) << 8)
                | (d[i + 3] & 0xFF);
    }

    private static long u32le(byte[] d, int i) {
        return (d[i] & 0xFF)
                | ((d[i + 1] & 0xFF) << 8)
                | ((d[i + 2] & 0xFF) << 16)
                | ((long) (d[i + 3] & 0xFF) << 24);
    }
}
