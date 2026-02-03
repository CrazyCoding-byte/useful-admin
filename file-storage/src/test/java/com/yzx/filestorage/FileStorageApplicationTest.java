package com.yzx.filestorage;

import static org.mockito.Mockito.when;

import com.yzx.filestorage.service.IFileStorageService;
import com.yzx.filestorage.service.serviceimpl.FileStorageGrpcService;
import filestorage.FileStorage;
import filestorage.FileStorage.*;
import filestorage.FileStorageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileStorageApplicationTest {

    private static ManagedChannel channel;
    private static FileStorageServiceGrpc.FileStorageServiceBlockingStub blockingStub;
    private static FileStorageServiceGrpc.FileStorageServiceStub asyncStub;

    @Autowired
    private IFileStorageService fileStorageService;

    private static final String TEST_FILE_SYSTEM_TYPE = "local";
    private static String uploadedFileHash="39bc421da4bbf6907ec8cf7b81f95be3db3814b5e3e98ea55965fd0654baee66";

    @BeforeAll
    static void setUp() {
        // 创建gRPC客户端连接
        channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        blockingStub = FileStorageServiceGrpc.newBlockingStub(channel);
        asyncStub = FileStorageServiceGrpc.newStub(channel);
    }

    @AfterAll
    static void tearDown() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Test
    @Order(1)
    @DisplayName("测试从URL上传文件 - 完整流程")
    void testUploadFileFromUrl_EndToEnd() throws Exception {
        System.out.println("=== 开始端到端测试：URL文件上传 ===");

        // 使用一个真实可访问的小图片URL
        String testUrl = "https://pixnio.com/free-images/2025/08/14/2025-08-14-00-53-03-768x578.jpg";

        UploadFileFromUrlRequest request = UploadFileFromUrlRequest.newBuilder()
                .setFileSystemType(TEST_FILE_SYSTEM_TYPE)
                .setDownloadUrl(testUrl)
                .setFileName("end-to-end-test.jpg")
                .setMimeType("image/jpeg")
                .build();

        System.out.println("发送gRPC请求...");
        UploadFileResponse response = blockingStub.uploadFileFromUrl(request);

        // 验证gRPC响应
        assertNotNull(response, "gRPC响应不能为空");
        assertTrue(response.getSuccess(), "上传应该成功");
        assertNotNull(response.getFileDetail(), "文件详情不能为空");

        uploadedFileHash = response.getFileDetail().getFileHash();
        assertNotNull(uploadedFileHash, "文件哈希不能为空");

        System.out.println("✅ gRPC响应验证通过");
        System.out.println("📁 文件哈希: " + uploadedFileHash);
        System.out.println("📁 文件路径: " + response.getFileDetail().getFilePath());
        System.out.println("📁 文件大小: " + response.getFileDetail().getFileSize() + " bytes");
    }

    @Test
    @Order(2)
    @DisplayName("验证数据库记录")
    void testVerifyDatabaseRecord() {
        System.out.println("=== 验证数据库记录 ===");
        assertNotNull(uploadedFileHash, "需要先运行上传测试");
        // 直接调用Service层验证数据库
        com.yzx.model.filestorage.FileDetailResponse dbRecord =
                fileStorageService.getFileDetail(uploadedFileHash, TEST_FILE_SYSTEM_TYPE);

        assertNotNull(dbRecord, "数据库中没有找到对应的文件记录");
        assertEquals("end-to-end-test.jpg", dbRecord.getFileName());
        assertEquals(TEST_FILE_SYSTEM_TYPE, dbRecord.getFileSystemType());
        assertTrue(dbRecord.getFileSize() > 0, "文件大小应该大于0");

        System.out.println("✅ 数据库记录验证通过");
        System.out.println("🗃️ 数据库记录ID: " + dbRecord.getId());
        System.out.println("🗃️ 文件名: " + dbRecord.getFileName());
        System.out.println("🗃️ 文件类型: " + dbRecord.getFileType());
        System.out.println("🗃️ 文件大小: " + dbRecord.getFileSize());
    }

    @Test
    @Order(3)
    @DisplayName("验证文件系统存储")
    void testVerifyFileSystemStorage() throws Exception {
        System.out.println("=== 验证文件系统存储 ===");

        assertNotNull(uploadedFileHash, "需要先运行上传测试");

        // 获取文件详情
        com.yzx.model.filestorage.FileDetailResponse fileDetail =
                fileStorageService.getFileDetail(uploadedFileHash, TEST_FILE_SYSTEM_TYPE);

        // 验证文件是否真的存储在文件系统中
        String filePath = fileDetail.getFilePath();
        assertNotNull(filePath, "文件路径不能为空");

        Path fullPath = Paths.get("./test-uploads", filePath);
        File file = fullPath.toFile();

        assertTrue(file.exists(), "文件应该存在于文件系统中: " + fullPath);
        assertTrue(file.length() > 0, "文件大小应该大于0");

        System.out.println("✅ 文件系统验证通过");
        System.out.println("💾 文件路径: " + fullPath);
        System.out.println("💾 实际文件大小: " + file.length() + " bytes");

        // 验证文件内容（可选）
        byte[] fileBytes = Files.readAllBytes(fullPath);
        assertTrue(fileBytes.length > 0, "文件内容不能为空");
    }

    @Test
    @Order(4)
    @DisplayName("测试通过gRPC获取文件详情")
    void testGetFileDetailViaGrpc() {
        System.out.println("=== 测试gRPC文件详情查询 ===");

        assertNotNull(uploadedFileHash, "需要先运行上传测试");

        GetFileDetailRequest request = GetFileDetailRequest.newBuilder()
                .setFileHash(uploadedFileHash)
                .setFileSystemType(TEST_FILE_SYSTEM_TYPE)
                .build();

        FileDetailResponse response = blockingStub.getFileDetail(request);

        assertNotNull(response, "gRPC响应不能为空");
        assertEquals(uploadedFileHash, response.getFileHash());
        assertEquals("end-to-end-test.jpg", response.getFileName());

        System.out.println("✅ gRPC文件详情查询通过");
        System.out.println("🔍 查询到的文件名: " + response.getFileName());
        System.out.println("🔍 查询到的文件类型: " + response.getFileType());
    }

    @Test
    @Order(5)
    @DisplayName("测试流式文件上传")
    void testStreamFileUpload() throws Exception {
        System.out.println("=== 测试流式文件上传 ===");

        // 创建一个测试文件
        Path testFile = Paths.get("E:\\1.jpg");
        String testContent = "这是一个流式上传测试文件内容 " + System.currentTimeMillis();
        Files.write(testFile, testContent.getBytes());

        CountDownLatch finishLatch = new CountDownLatch(1);
        TestResultHolder resultHolder = new TestResultHolder();

        StreamObserver<UploadFileResponse> responseObserver = new StreamObserver<UploadFileResponse>() {
            @Override
            public void onNext(UploadFileResponse response) {
                resultHolder.response = response;
            }

            @Override
            public void onError(Throwable t) {
                resultHolder.error = t;
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };

        StreamObserver<UploadFileRequest> requestObserver = asyncStub.uploadFile(responseObserver);

        try {
            // 发送元数据
            FileMetadata metadata = FileMetadata.newBuilder()
                    .setFileSystemType(TEST_FILE_SYSTEM_TYPE)
                    .setFileName("stream-test.txt")
                    .setMimeType("text/plain")
                    .setFileSize(Files.size(testFile))
                    .build();

            requestObserver.onNext(UploadFileRequest.newBuilder()
                    .setMetadata(metadata)
                    .build());

            // 发送文件数据（分块）
            byte[] fileData = Files.readAllBytes(testFile);
            int chunkSize = 1024; // 1KB chunks for testing
            for (int i = 0; i < fileData.length; i += chunkSize) {
                int end = Math.min(fileData.length, i + chunkSize);
                byte[] chunk = new byte[end - i];
                System.arraycopy(fileData, i, chunk, 0, chunk.length);

                requestObserver.onNext(UploadFileRequest.newBuilder()
                        .setChunk(com.google.protobuf.ByteString.copyFrom(chunk))
                        .build());
            }

            requestObserver.onCompleted();

            // 等待完成
            boolean completed = finishLatch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "流式上传超时");

            // 验证结果
            assertNull(resultHolder.error, "流式上传失败: " +
                    (resultHolder.error != null ? resultHolder.error.getMessage() : ""));
            assertNotNull(resultHolder.response);
            assertTrue(resultHolder.response.getSuccess());

            String streamFileHash = resultHolder.response.getFileDetail().getFileHash();
            assertNotNull(streamFileHash);

            System.out.println("✅ 流式文件上传测试通过");
            System.out.println("📤 流式上传文件哈希: " + streamFileHash);

        } finally {
            // 清理测试文件
            Files.deleteIfExists(testFile);
        }
    }

    @Test
    @Order(6)
    @DisplayName("清理测试数据")
    void testCleanup() {
        System.out.println("=== 清理测试数据 ===");

        // 这里可以添加清理逻辑，删除测试文件等
        System.out.println("🧹 测试完成，请手动清理测试文件或添加自动清理逻辑");
    }

    private static class TestResultHolder {
        UploadFileResponse response;
        Throwable error;
    }
}