package com.yzx.filestorage.service.serviceimpl;

import com.yzx.filestorage.service.IFileStorageService;
import com.yzx.filestorage.util.ByteArrayMultipartFile;
import com.yzx.model.exception.BaseException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 导入生成的gRPC类
import filestorage.FileStorageServiceGrpc;
import filestorage.FileStorage.*;

@GrpcService
public class FileStorageGrpcService extends FileStorageServiceGrpc.FileStorageServiceImplBase {

    @Autowired
    private IFileStorageService fileStorageService;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 内存限制：100MB
    private static final long MAX_MEMORY_SIZE = 100 * 1024 * 1024;

    // 分片上传会话存储
    private final Map<String, ChunkUploadSession> uploadSessions = new ConcurrentHashMap<>();


    @Override
    public StreamObserver<UploadFileRequest> uploadFile(
            StreamObserver<UploadFileResponse> responseObserver) {

        return new StreamObserver<UploadFileRequest>() {
            private FileMetadata metadata;
            private ByteArrayOutputStream fileData = new ByteArrayOutputStream();
            private long totalReceivedBytes = 0;

            @Override
            public void onNext(UploadFileRequest request) {
                try {
                    if (request.hasMetadata()) {
                        metadata = request.getMetadata();

                        // 检查文件大小是否超过内存限制
                        if (metadata.getFileSize() > MAX_MEMORY_SIZE) {
                            String errorMsg = String.format("文件过大(%dMB)，请使用分片上传接口",
                                    metadata.getFileSize() / (1024 * 1024));
                            responseObserver.onError(Status.INVALID_ARGUMENT
                                    .withDescription(errorMsg).asException());
                            return;
                        }

                        fileData = new ByteArrayOutputStream((int) metadata.getFileSize());
                    } else if (request.hasChunk()) {
                        byte[] chunkData = request.getChunk().toByteArray();
                        totalReceivedBytes += chunkData.length;

                        // 检查内存使用
                        if (totalReceivedBytes > MAX_MEMORY_SIZE) {
                            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                                    .withDescription("内存超限，请使用分片上传").asException());
                            return;
                        }

                        fileData.write(chunkData);
                    }
                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("处理数据失败: " + e.getMessage()).asException());
                }
            }

            @Override
            public void onError(Throwable t) {
                // 清理资源
                fileData = null;
                metadata = null;
            }

            @Override
            public void onCompleted() {
                try {
                    if (metadata == null) {
                        responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("未收到文件元数据").asException());
                        return;
                    }

                    // 验证文件大小
                    if (metadata.getFileSize() > 0 && fileData.size() != metadata.getFileSize()) {
                        responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("文件大小不匹配").asException());
                        return;
                    }

                    // 将字节数组转换为 MultipartFile
                    MultipartFile multipartFile = new ByteArrayMultipartFile(
                            metadata.getFileName(),
                            metadata.getMimeType(),
                            fileData.toByteArray()
                    );

                    // 调用现有服务
                    String fileHash = fileStorageService.storeFile(
                            metadata.getFileSystemType(),
                            multipartFile
                    );

                    // 获取文件详情
                    com.yzx.model.filestorage.FileDetailResponse fileDetail =
                            fileStorageService.getFileDetail(fileHash, metadata.getFileSystemType());

                    // 构建响应
                    UploadFileResponse response = buildSuccessResponse(fileDetail);
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();

                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("上传失败: " + e.getMessage()).asException());
                } finally {
                    // 清理资源
                    fileData = null;
                    metadata = null;
                }
            }
        };
    }

    // 新的分片上传接口
    @Override
    public StreamObserver<FileChunk> uploadFileChunked(StreamObserver<UploadFileResponse> responseObserver) {
        return new StreamObserver<FileChunk>() {
            private ChunkUploadSession session = null;

            @Override
            public void onNext(FileChunk chunk) {
                try {
                    if (session == null) {
                        // 第一个分片，创建上传会话
                        String uploadId = chunk.getUploadId();
                        if (uploadId.isEmpty()) {
                            uploadId = UUID.randomUUID().toString();
                        }
                        //如果存在就用原来的value，不存在就创建新的value
                        session = uploadSessions.computeIfAbsent(uploadId,
                                id -> new ChunkUploadSession(
                                        id,
                                        chunk.getFileName(),
                                        chunk.getMimeType(),
                                        chunk.getFileSystemType(),
                                        chunk.getTotalChunks()
                                ));
                    }
                    fileStorageService.storeFileChunk(
                            session.getUploadId(),    // uploadId
                            chunk.getChunkIndex(),   // 分片索引
                            session.getTotalChunks(),// 总分片数
                            chunk.getData().toByteArray(), // 分片数据
                            session.getFileName()    // 文件名
                    );
                    session.markChunkReceived(chunk.getChunkIndex());

                    // 可选：发送分片确认
                    if (chunk.getChunkIndex() % 10 == 0) { // 每10个分片确认一次进度
                        UploadFileResponse progressResponse = UploadFileResponse.newBuilder()
                                .setSuccess(true)
                                .setMessage("上传进度: " + session.getProgress() + "%")
                                .setUploadId(session.getUploadId())
                                .build();
                        responseObserver.onNext(progressResponse);
                    }
                } catch (BaseException e) {
                    // 捕获你service抛出的异常（如分片校验失败），返回给客户端重试
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage()).asException());
                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("分片上传失败: " + e.getMessage()).asException());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (session != null) {
                    cleanupSession(session.getUploadId());
                }
            }

            @Override
            public void onCompleted() {
                if (session != null) {
                    // 返回上传ID，让客户端调用完成接口
                    UploadFileResponse response = UploadFileResponse.newBuilder()
                            .setSuccess(true)
                            .setMessage("分片上传完成，请调用CompleteChunkedUpload")
                            .setUploadId(session.getUploadId())
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } else {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("未收到任何分片数据").asException());
                }
            }
        };
    }

    @Override
    public void completeChunkedUpload(CompleteUploadRequest request,
                                      StreamObserver<UploadFileResponse> responseObserver) {
        try {
            String uploadId = request.getUploadId();
            ChunkUploadSession session = uploadSessions.get(uploadId);

            if (session == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("上传会话不存在或已过期").asException());
                return;
            }

            boolean chunkComplete = fileStorageService.isChunkComplete(uploadId);
            if (!chunkComplete) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("所有分片未上传完成").asException());
            }
            String filehash = fileStorageService.completeChunkUpload(uploadId, session.getFileSystemType(), session.getFileName(), session.getMimeType());
            com.yzx.model.filestorage.FileDetailResponse fileDetail1 = fileStorageService.getFileDetail(filehash, session.getFileSystemType());
            // 验证文件哈希（可选）
            if (!request.getFileHash().isEmpty() && !request.getFileHash().equals(filehash)) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("文件哈希校验失败").asException());
                return;
            }


            UploadFileResponse response = buildSuccessResponse(fileDetail1);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            // 清理会话
            cleanupSession(uploadId);
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("合并文件失败: " + e.getMessage()).asException());
        }
    }

    // 原有的其他方法保持不变
    @Override
    public void uploadFileFromUrl(UploadFileFromUrlRequest request,
                                  StreamObserver<UploadFileResponse> responseObserver) {
        try {
            System.out.println("=== gRPC uploadFileFromUrl 开始 ===");
            System.out.println("请求参数: fileSystemType=" + request.getFileSystemType() +
                    ", downloadUrl=" + request.getDownloadUrl() +
                    ", fileName=" + request.getFileName() +
                    ", mimeType=" + request.getMimeType());

            com.yzx.model.filestorage.FileDetailResponse fileDetail = fileStorageService.storeUrlFile(
                    request.getFileSystemType(),
                    request.getDownloadUrl(),
                    request.getMimeType(),
                    request.getFileName()
            );
            System.out.println("=== gRPC uploadFileFromUrl 成功 ===");
            UploadFileResponse response = buildSuccessResponse(fileDetail);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.err.println("=== gRPC uploadFileFromUrl 异常 ===");
            System.err.println("异常类型: " + e.getClass().getName());
            System.err.println("异常信息: " + e.getMessage());
            e.printStackTrace(); // 打印完整堆栈
            responseObserver.onError(Status.INTERNAL
                    .withDescription("URL上传失败: " + e.getMessage()).asException());
        }
    }

    @Override
    public void getFileDetail(GetFileDetailRequest request,
                              StreamObserver<FileDetailResponse> responseObserver) {
        try {
            com.yzx.model.filestorage.FileDetailResponse fileDetail = fileStorageService.getFileDetail(
                    request.getFileHash(),
                    request.getFileSystemType()
            );

            if (fileDetail == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("文件不存在").asException());
                return;
            }

            responseObserver.onNext(convertToProto(fileDetail));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("获取文件详情失败: " + e.getMessage()).asException());
        }
    }

    private UploadFileResponse buildSuccessResponse(com.yzx.model.filestorage.FileDetailResponse fileDetail) {
        return UploadFileResponse.newBuilder()
                .setSuccess(true)
                .setMessage("上传成功")
                .setFileDetail(convertToProto(fileDetail))
                .build();
    }

    private FileDetailResponse convertToProto(com.yzx.model.filestorage.FileDetailResponse javaObj) {
        if (javaObj == null) {
            System.err.println("警告: convertToProto 接收到 null 对象");
            return FileDetailResponse.newBuilder().build();
        }

        // 添加详细的调试信息
        System.out.println("=== convertToProto 开始转换 ===");
        System.out.println("javaObj: " + javaObj);
        System.out.println("id: " + javaObj.getId());
        System.out.println("fileName: " + javaObj.getFileName());
        System.out.println("fileType: " + javaObj.getFileType());
        System.out.println("fileHash: " + javaObj.getFileHash());
        System.out.println("filePath: " + javaObj.getFilePath());
        System.out.println("fileSize: " + javaObj.getFileSize());
        System.out.println("uploader: " + javaObj.getUploader());
        System.out.println("fileSystemType: " + javaObj.getFileSystemType());
        System.out.println("createdAt: " + javaObj.getCreatedAt());
        System.out.println("updatedAt: " + javaObj.getUpdatedAt());

        FileDetailResponse.Builder builder = FileDetailResponse.newBuilder()
                .setId(javaObj.getId())
                .setFileName(javaObj.getFileName() != null ? javaObj.getFileName() : "")
                .setFileType(javaObj.getFileType() != null ? javaObj.getFileType() : "")
                .setFileHash(javaObj.getFileHash() != null ? javaObj.getFileHash() : "")
                .setFilePath(javaObj.getFilePath() != null ? javaObj.getFilePath() : "")
                .setFileSize(javaObj.getFileSize() != null ? javaObj.getFileSize() : 0)
                .setUploader(javaObj.getUploader() != null ? javaObj.getUploader() : 0)
                .setFileSystemType(javaObj.getFileSystemType() != null ? javaObj.getFileSystemType() : "");

        // 安全处理日期字段
        if (javaObj.getCreatedAt() != null) {
            builder.setCreatedAt(javaObj.getCreatedAt().format(DATE_FORMATTER));
        } else {
            builder.setCreatedAt("");
            System.err.println("警告: createdAt 为 null");
        }

        if (javaObj.getUpdatedAt() != null) {
            builder.setUpdatedAt(javaObj.getUpdatedAt().format(DATE_FORMATTER));
        } else {
            builder.setUpdatedAt("");
            System.err.println("警告: updatedAt 为 null");
        }

        FileDetailResponse result = builder.build();
        System.out.println("=== convertToProto 转换完成 ===");
        return result;
    }

    private void cleanupSession(String uploadId) {
        ChunkUploadSession session = uploadSessions.remove(uploadId);
        if (session != null) {
            session.cleanup();
        }
    }

    /**
     * 分片上传会话管理类
     */
    @Data
    private static class ChunkUploadSession {
        private final String uploadId;
        private final String fileName;
        private final String mimeType;
        private final String fileSystemType;
        private final int totalChunks;
        private final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        private final Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();
        private final long createTime = System.currentTimeMillis();

        public ChunkUploadSession(String uploadId, String fileName, String mimeType,
                                  String fileSystemType, int totalChunks) {
            this.uploadId = uploadId;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.fileSystemType = fileSystemType;
            this.totalChunks = totalChunks;
        }



        public void markChunkReceived(int chunkIndex) {
            if (chunkIndex >= 0 && chunkIndex < totalChunks) {
                receivedChunks.add(chunkIndex);
            }
        }

        public boolean isComplete() {
            return receivedChunks.size() == totalChunks;
        }

        public int getProgress() {
            return (int) ((receivedChunks.size() * 100.0) / totalChunks);
        }

        public byte[] mergeChunks() throws IOException {
            if (!isComplete()) {
                throw new IllegalStateException("分片未全部上传完成");
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                for (int i = 0; i < totalChunks; i++) {
                    byte[] chunk = chunks.get(i);
                    if (chunk == null) {
                        throw new IOException("分片 " + i + " 缺失");
                    }
                    outputStream.write(chunk);
                }
                return outputStream.toByteArray();
            }
        }

        public void cleanup() {
            chunks.clear();
            receivedChunks.clear();
        }
    }

}