package com.team.blog.support;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 테스트용 사진 저장소 (003 T014, research R18, 23 §2-3 "앱 통합 테스트는 Testcontainers로 같은 고정 이미지").
 *
 * <p>로컬 compose와 같은 {@code pgsty/silo} 고정 이미지를 임의 포트로 한 번만 띄우고(JVM 싱글턴, 이름 고정 없음), compose의 {@code
 * minio-init}과 같은 {@code scripts/minio-init.sh}를 컨테이너 안의 {@code mc}로 실행해 버킷 {@code blog}·{@code
 * images/*} 익명 읽기 정책·앱 전용 사용자를 만든다. 앱은 앱 전용 키만 쓴다(점검 8번과 같은 권한).
 *
 * <p>Spring 설정 주입은 {@link MinioPropertyRegistrar}(test 프로필 {@code DynamicPropertyRegistrar} Bean)가
 * 한다 — 시험 전용 컨텍스트를 늘리지 않으려고 {@code @DynamicPropertySource}를 쓰지 않는다.
 */
public final class MinioContainerSupport {

    public static final String IMAGE = "pgsty/silo:RELEASE.2026-09-16T00-00-00Z";
    public static final String BUCKET = "blog";
    public static final String ROOT_USER = "test-root";
    public static final String ROOT_PASSWORD = "test-root-password";
    public static final String APP_KEY = "test-app-key";
    public static final String APP_SECRET = "test-app-secret-1234";

    /** 브라우저 출처 (CORS 허용). */
    public static final String SITE_ORIGIN = "http://localhost:8080";

    private static volatile GenericContainer<?> container;
    private static volatile S3Client admin;

    private MinioContainerSupport() {}

    /** 컨테이너를 (처음 한 번) 띄우고 돌려준다. */
    public static synchronized GenericContainer<?> container() {
        if (container == null) {
            @SuppressWarnings("resource")
            GenericContainer<?> c =
                    new GenericContainer<>(DockerImageName.parse(IMAGE))
                            .withCommand("server", "/data")
                            .withEnv("MINIO_ROOT_USER", ROOT_USER)
                            .withEnv("MINIO_ROOT_PASSWORD", ROOT_PASSWORD)
                            .withEnv(
                                    "MINIO_API_CORS_ALLOW_ORIGIN",
                                    SITE_ORIGIN + ",http://localhost:5173")
                            .withExposedPorts(9000)
                            .withCopyFileToContainer(
                                    MountableFile.forHostPath(initScript()), "/minio-init.sh")
                            .waitingFor(
                                    Wait.forHttp("/minio/health/live")
                                            .forPort(9000)
                                            .withStartupTimeout(Duration.ofSeconds(60)));
            c.start();
            init(c);
            container = c;
        }
        return container;
    }

    /** 브라우저·서버가 접속하는 주소 ({@code http://localhost:{임의 포트}}). */
    public static String endpoint() {
        GenericContainer<?> c = container();
        return "http://" + c.getHost() + ":" + c.getMappedPort(9000);
    }

    /** 공개 주소 ({@code {endpoint}/blog}). */
    public static String publicBaseUrl() {
        return endpoint() + "/" + BUCKET;
    }

    /** 관리 키 클라이언트 (테스트 정리·확인용 — 앱 코드는 쓰지 않는다). */
    public static synchronized S3Client admin() {
        if (admin == null) {
            admin =
                    S3Client.builder()
                            .endpointOverride(URI.create(endpoint()))
                            .region(Region.US_EAST_1)
                            .forcePathStyle(true)
                            .httpClient(UrlConnectionHttpClient.create())
                            .credentialsProvider(
                                    StaticCredentialsProvider.create(
                                            AwsBasicCredentials.create(ROOT_USER, ROOT_PASSWORD)))
                            .build();
        }
        return admin;
    }

    /** {@code images/} 아래 객체를 모두 지운다. */
    public static void clearImages() {
        S3Client s3 = admin();
        List<ObjectIdentifier> ids = new ArrayList<>();
        for (S3Object object :
                s3.listObjectsV2Paginator(b -> b.bucket(BUCKET).prefix("images/")).contents()) {
            ids.add(ObjectIdentifier.builder().key(object.key()).build());
            if (ids.size() == 1000) {
                deleteBatch(s3, ids);
            }
        }
        deleteBatch(s3, ids);
    }

    /** 저장소에 그 키가 있는가 (관리 키로 확인). */
    public static boolean exists(String key) {
        try {
            admin().headObject(b -> b.bucket(BUCKET).key(key));
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** 관리 키로 객체를 바로 넣는다 (브라우저 PUT을 흉내 내지 않아도 되는 준비 단계용). */
    public static void putDirect(String key, String contentType, byte[] body) {
        admin().putObject(
                        b -> b.bucket(BUCKET).key(key).contentType(contentType),
                        software.amazon.awssdk.core.sync.RequestBody.fromBytes(body));
    }

    /** 관리 키로 객체 바이트를 읽는다. */
    public static byte[] read(String key) {
        return admin().getObjectAsBytes(b -> b.bucket(BUCKET).key(key)).asByteArray();
    }

    /** 컨테이너를 일시 정지한다 (저장소 장애 재현). {@link #resume()}으로 되살린다. */
    public static void pause() {
        GenericContainer<?> c = container();
        c.getDockerClient().pauseContainerCmd(c.getContainerId()).exec();
    }

    public static void resume() {
        GenericContainer<?> c = container();
        c.getDockerClient().unpauseContainerCmd(c.getContainerId()).exec();
    }

    private static void deleteBatch(S3Client s3, List<ObjectIdentifier> ids) {
        if (ids.isEmpty()) {
            return;
        }
        s3.deleteObjects(
                b -> b.bucket(BUCKET).delete(Delete.builder().objects(List.copyOf(ids)).build()));
        ids.clear();
    }

    private static void init(GenericContainer<?> c) {
        try {
            ExecResult result =
                    c.execInContainer(
                            "env",
                            "S3_ENDPOINT=http://127.0.0.1:9000",
                            "STORAGE_ROOT_USER=" + ROOT_USER,
                            "STORAGE_ROOT_PASSWORD=" + ROOT_PASSWORD,
                            "BLOG_IMAGE_STORAGE_ACCESS_KEY=" + APP_KEY,
                            "BLOG_IMAGE_STORAGE_SECRET_KEY=" + APP_SECRET,
                            "BUCKET=" + BUCKET,
                            "/bin/sh",
                            "/minio-init.sh");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(
                        "minio-init 실패: " + result.getStdout() + result.getStderr());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** 저장소 루트의 {@code scripts/minio-init.sh} (테스트 작업 디렉터리는 {@code backend/}). */
    private static Path initScript() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("scripts/minio-init.sh");
            if (candidate.toFile().isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("scripts/minio-init.sh를 찾지 못했습니다");
    }
}
