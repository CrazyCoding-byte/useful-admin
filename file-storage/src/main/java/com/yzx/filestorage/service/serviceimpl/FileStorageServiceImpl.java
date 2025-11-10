package com.yzx.filestorage.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzx.filestorage.config.FileLoadProperties;
import com.yzx.filestorage.config.FileStorageTransform;
import com.yzx.filestorage.config.OkHttpUtils;
import com.yzx.filestorage.mapper.FileStorageMapper;
import com.yzx.filestorage.service.IFileStorageService;
import com.yzx.model.BaseException;
import com.yzx.model.ErrorCodeEnum;
import com.yzx.model.filestorage.FileDetailResponse;
import com.yzx.model.filestorage.FileStorage;
import com.yzx.model.filestorage.FileSystemType;
import kotlin.io.FilesKt;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Response;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 文件存储表 服务实现类
 * </p>
 *
 * @author yzx
 * @since 2025-03-11
 */
@Service
public class FileStorageServiceImpl extends ServiceImpl<FileStorageMapper, FileStorage> implements IFileStorageService {
    @Autowired
    private FileStorageMapper fileStorageMapper;
    @Autowired
    private FileLoadProperties fileLoadProperties;
    @Autowired
    private FileStorageTransform fileStorageTransform;
    @Autowired
    private OkHttpUtils okHttpUtils;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    @Override
    public FileDetailResponse storeUrlFile(String fileSystemType, String downloadUrl, String mimeType, String fileName) throws IOException {
        if (!FileSystemType.isAvailableType(fileSystemType)) {
            throw new BaseException(ErrorCodeEnum.NOT_ALLOWED_SYSTEM);
        }

        // 发送 GET 请求获取文件响应
        Response response = okHttpUtils.get(downloadUrl);
        if (!response.isSuccessful() || response.body() == null) {
            LOGGER.error("文件下载失败:{}", response);
            throw new BaseException(ErrorCodeEnum.EXECUTION_FAIL.getCode(), "文件下载失败");
        }

        if (StringUtils.isBlank(fileName)) {
            try {
                URL url = new URL(downloadUrl);
                String path = url.getPath();
                fileName = Paths.get(path).getFileName().toString();
            } catch (MalformedURLException e) {
                LOGGER.warn("解析文件名失败", e);
                fileName = "";
            }
        }

        String extension = "";
        if (fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf("."));
        }

        String fileType;
        if (StringUtils.isBlank(mimeType)) {
            String contentType = response.header("Content-Type");
            fileType = getFileType(contentType);
        } else {
            fileType = getFileType(mimeType);
        }

        byte[] fileBytes = response.body().bytes();
        String fileHash = DigestUtils.sha256Hex(fileBytes);
        long fileSize = fileBytes.length;

        // 查库判断是否已存在
        FileStorage fileStorage = getFileStorageSimpleByFileHash(fileSystemType, fileHash);
        if (fileStorage != null && fileStorage.getFilePath() != null) {
            if (!Objects.equals(fileStorage.getFileName(), fileName)) {
                updateFileName(fileName, fileHash);
            }
            return fileStorageTransform.toFileDetailResponse(fileStorage);
        }

        // 构建路径并写入文件
        Path typeDir = Paths.get(fileLoadProperties.getUploadBaseDir(), fileSystemType, fileType);
        if (!Files.exists(typeDir)) {
            Files.createDirectories(typeDir);
        }

        String hashedFileName = fileHash + extension;
        Path targetPath = typeDir.resolve(hashedFileName);
        Files.write(targetPath, fileBytes);

        Path downloadDir = Paths.get(fileSystemType, fileType, hashedFileName);
        String normalizedPath = downloadDir.toString().replace(File.separator, "/");
        //保存元数据
        FileStorage fileStorageToSave = saveMetaDate(fileSystemType, fileName, fileType, fileHash, normalizedPath, fileSize);
        FileDetailResponse fileDetailResponse = fileStorageTransform.toFileDetailResponse(fileStorageToSave);
        System.out.println("=== storeUrlFile 返回结果 ===");
        System.out.println("结果对象: " + fileDetailResponse);
        if (fileDetailResponse != null) {
            System.out.println("结果ID: " + fileDetailResponse.getId());
            System.out.println("结果文件名: " + fileDetailResponse.getFileName());
            System.out.println("结果文件哈希: " + fileDetailResponse.getFileHash());
        } else {
            System.err.println("错误: storeUrlFile 返回了 null 结果");
        }

        return fileDetailResponse;
    }

    @Override
    public ChunkMetadata storeFileChunk(String uploadId, int chunkIndex,
                                        int totalChunks, byte[] chunkData, String fileName) {
        try {
            //创建临时文件夹 假设配置的 upload.base-dir 为 /data/server/upload，某次上传的 uploadId 是 f8921e5d-3c7b-458a-9f3d-75555abcdef，则：
            //chunkDir 最终路径为：/data/server/upload/chunks/f8921e5d-3c7b-458a-9f3d-75555abcdef
            //该目录下会存储分片文件（如 chunk_00000、chunk_00001）和对应元数据文件（如 metadata_00000.json）。
            Path chunkDir = Paths.get(fileLoadProperties.getUploadBaseDir(), "chunks", uploadId);
            Path chunkFile = chunkDir.resolve(String.format("chunk_%05d", chunkIndex));
            String originHash = calculateChunkHash(chunkData);
            Files.createDirectories(chunkDir);
            saveChunkFile(chunkDir, chunkIndex, chunkData);
            byte[] bytes = Files.readAllBytes(chunkFile);
            String chunkHash = calculateChunkHash(bytes);
            boolean verified = originHash.equals(chunkHash);
            ChunkMetadata chunkMetadata = saveChunkMetadata(uploadId, chunkDir, chunkData, fileName, chunkIndex, chunkHash, verified, totalChunks);
            if (!verified) {
                // 如果验证失败，删除损坏的分片文件
                Files.deleteIfExists(chunkFile);
                throw new BaseException(ErrorCodeEnum.CHUNK_HASH_ERROR.getCode(),
                        String.format("分片%d校验失败，需要重新上传", chunkIndex));
            }
            return chunkMetadata;
        } catch (Exception e) {
            log.error("分片上传失败:{}", e);
            throw new BaseException(ErrorCodeEnum.EXECUTION_FAIL.getCode(), "创建目录失败");
        }
    }

    /**
     * 保存分片元数据到JSON文件
     */
    private ChunkMetadata saveChunkMetadata(String uploadId, Path chunkDir, byte[] chunkData, String fileName, int chunIndex,
                                            String chunkHash, boolean verified, int totalChunks) throws IOException {
        ChunkMetadata metadata = new ChunkMetadata();
        metadata.setUploadId(uploadId);
        metadata.setChunkHash(chunkHash);
        metadata.setChunkIndex(chunIndex);
        metadata.setTotalChunks(totalChunks);
        metadata.setFileName(fileName);
        // 计算chunkSize（字节数 -> MB/KB，带单位）
        int byteLength = (chunkData != null) ? chunkData.length : 0; // 避免空指针
        String chunkSize;
        if (byteLength >= 1024 * 1024) { // 1MB = 1024*1024字节
            double mbSize = (double) byteLength / (1024 * 1024);
            chunkSize = String.format("%.2fMB", mbSize); // 保留2位小数
        } else { // 不足1MB，用KB
            double kbSize = (double) byteLength / 1024; // 1KB = 1024字节
            chunkSize = String.format("%.2fKB", kbSize); // 保留2位小数
        }
        metadata.setChunkSize(chunkSize);
        metadata.setVerified(verified);
        metadata.setFilePath(chunkDir.resolve(String.format("chunk_%05d", chunIndex)).toString());
        ObjectMapper objectMapper = new ObjectMapper();
        String metadataJson = objectMapper.writeValueAsString(metadata);
        // 为每个分片保存单独的元数据文件
        Path metadataFile = chunkDir.resolve(String.format("metadata_%05d.json", chunIndex));
        Files.write(metadataFile, metadataJson.getBytes(StandardCharsets.UTF_8));
        return metadata;
    }

    /**
     * 计算分片哈希
     */
    private String calculateChunkHash(byte[] chunkData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(chunkData);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不支持", e);
        }
    }

    /**
     * 计算文件哈希
     */
    private String calculateFileHash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hashBytes = digest.digest(fileBytes);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不支持", e);
        }
    }

    // 辅助方法：字节数组转十六进制
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 读取分片元数据
     * @param chunkDir   chunDir.Resolve("metadata.json")相当于 chunDir路径+"/metadata.json"
     * @return
     */
    private ChunkMetadata readChunkMetadata(Path chunkDir, int chunkIndex) {
        Path metadataFile = chunkDir.resolve(String.format("metadata_%05d.json", chunkIndex));
        if (!Files.exists(metadataFile)) return null;
        try {
            String s = new String(Files.readAllBytes(metadataFile), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(s, ChunkMetadata.class);
        } catch (IOException e) {
            log.error("读取分片元数据失败:{}", e);
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param chunkDir  分片文件
     * @param chunIndex 分片索引
     * @param chunkData  分片数据
     */
    private void saveChunkFile(Path chunkDir, int chunIndex, byte[] chunkData) throws IOException {
        String format = String.format("chunk_%05d", chunIndex);
        Path chunkFile = chunkDir.resolve(format);
        // 使用临时文件确保原子性写入
        Path tempFile = chunkDir.resolve(chunkFile + ".tmp");
        try {
            //写入临时文件 意思是先写入临时文件，然后移动到目标文件，如果目标文件已存在，则替换掉 因为写入临时的可能会出现问题 所以为了避免错误的文件
            //就需要写入临时文件 移动文件是原子操作
            Files.write(tempFile, chunkData);
            Files.move(tempFile, chunkFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 如果不支持原子移动，使用普通移动
            Files.move(tempFile, chunkFile, StandardCopyOption.REPLACE_EXISTING);
            log.error("保存分片文件失败:{}", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isChunkComplete(String uploadId) {
        try {
            Path chunkDir = Paths.get(fileLoadProperties.getUploadBaseDir(), "chunks", uploadId);
            if (!Files.exists(chunkDir)) {
                return false;
            }
            //读取元数据
            int totalChunks = getTotalChunksFromMetadata(chunkDir);
            if (totalChunks == 0) return false;
            // 检查所有分片是否都存在且验证通过
            for (int i = 0; i < totalChunks; i++) {
                ChunkMetadata metadata = readChunkMetadata(chunkDir, i);
                if (metadata == null || !metadata.isVerified()) {
                    return false;
                }
                // 检查分片文件是否存在
                Path chunkFile = chunkDir.resolve(String.format("chunk_%05d", i));
                if (!Files.exists(chunkFile)) {
                    return false;
                }
            }
            return true;
            //检查分片数量是否一致
        } catch (Exception e) {
            log.error("检查分片是否完成失败:{}", e);
            return false;
        }
    }

    private int getTotalChunksFromMetadata(Path chunkDir) {
        ChunkMetadata chunkMetadata = readChunkMetadata(chunkDir, 0);
        return chunkMetadata != null ? chunkMetadata.getTotalChunks() : 0;
    }

    @Override
    public String completeChunkUpload(String uploadId, String fileType, String fileName, String mimeType) throws IOException {
        Path chunks = Paths.get(fileLoadProperties.getUploadBaseDir(), "chunks", uploadId);
        if (!Files.exists(chunks)) {
            return null;
        }
        if (!isChunkComplete(uploadId)) {
            return null;
        }
        Path megerFile = megerChunk(chunks, fileName);
        // 使用现有的 storeFile 方法保存文件
        MultipartFile multipartFile = new FileSystemMultipartFile(mergedFile.toFile());
        String fileHash = storeFile(fileSystemType, multipartFile);

        // 清理临时文件
        Files.deleteIfExists(mergedFile);
        cleanupChunkSession(uploadId);
    }

    private Path megerChunk(Path chunkDir, String fileName) throws IOException {
        //
        Path megerFile = Files.createTempFile("meger_", "_" + fileName);
        try (OutputStream outputStream = Files.newOutputStream(megerFile)) {
            List<Path> chunkFiles = Files.list(chunkDir).sorted(Comparator.comparing(path -> {
                String filename = path.getFileName().toString();
                return Integer.parseInt(filename.replace("chunk_", ""));
            })).collect(Collectors.toList());
            for (Path chunkFile : chunkFiles) {
                Files.copy(chunkFile, outputStream);
            }
        }
        return megerFile;
    }

    private void cleanupChunkDirectory(Path chunkDir) throws IOException {
        Files.walk(chunkDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Override
    public void cleanupChunkSession(String uploadId) {
        try {
            Path chunkDir = Paths.get(fileLoadProperties.getUploadBaseDir(), "chunks", uploadId);
            cleanupChunkDirectory(chunkDir);
        } catch (IOException e) {
            LOGGER.warn("清理分片目录失败: {}", uploadId, e);
        }
    }

    // 保存元数据到数据库
    private FileStorage saveMetaDate(String fileSystemType, String fileName, String fileType, String fileHash, String normalizedPath, Long fileSize) {
        FileStorage metadata = new FileStorage();
        metadata.setFileName(fileName);
        metadata.setFileType(fileType);
        metadata.setFileHash(fileHash);
        metadata.setFilePath(normalizedPath);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setFileSystemType(fileSystemType);
        metadata.setFileSize(fileSize);
        save(metadata);
        return metadata;
    }

    private void updateFileName(String fileName, String fileHash) {
        LambdaUpdateWrapper<FileStorage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(FileStorage::getFileName, fileName);
        updateWrapper.set(FileStorage::getUpdatedAt, LocalDateTime.now());
        updateWrapper.eq(FileStorage::getFileHash, fileHash);
        fileStorageMapper.update(null, updateWrapper);
    }

    private FileStorage getFileStorageSimpleByFileHash(String fileSystemType, String fileHash) {
        LambdaQueryWrapper<FileStorage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileStorage::getFileHash, fileHash);
        queryWrapper.eq(FileStorage::getFileSystemType, fileSystemType);
        return fileStorageMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional
    public void deleteExpiredFiles(List<FileStorage> fileStorageList) {
        if (fileStorageList == null || fileStorageList.isEmpty()) {
            return;
        }

        // 删除文件系统中的文件
        fileStorageList.forEach(fileStorage -> {
            String filePath = fileStorage.getFilePath();
            if (filePath != null) {
                Path fullPath = Paths.get(fileLoadProperties.getUploadBaseDir(), filePath);
                try {
                    Files.deleteIfExists(fullPath);
                } catch (IOException e) {
                    LOGGER.warn("删除文件失败: {}", fullPath, e);
                }
            }
        });

        // 删除数据库中的记录
        List<Long> fileIds = fileStorageList.stream().map(FileStorage::getId).collect(Collectors.toList());
        fileStorageMapper.deleteBatchIds(fileIds);
        LOGGER.info("成功删除 {} 条过期文件记录", fileIds.size());
    }

    @Override
    public List<FileStorage> selectExpiredFile() {
        LambdaQueryWrapper<FileStorage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(FileStorage::getUpdatedAt, LocalDateTime.now().minusDays(7));
        queryWrapper.eq(FileStorage::getFileSystemType, FileSystemType.WAMediaMessage.getType());
        queryWrapper.select(FileStorage::getId, FileStorage::getFilePath);
        return fileStorageMapper.selectList(queryWrapper);
    }

    @Override
    public FileDetailResponse getFileDetail(String fileHash, String fileSystemType) {
        LambdaQueryWrapper<FileStorage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileStorage::getFileHash, fileHash);
        queryWrapper.eq(FileStorage::getFileSystemType, fileSystemType);
        FileStorage fileStorage = fileStorageMapper.selectOne(queryWrapper);
        if (fileStorage == null) {
            return null;
        }
        return fileStorageTransform.toFileDetailResponse(fileStorage);
    }

    /**
     * 上传文件并存储
     */
    @Override
    public String storeFile(String fileSystemType, MultipartFile file) throws IOException {
        if (!FileSystemType.isAvailableType(fileSystemType)) {
            throw new BaseException(ErrorCodeEnum.NOT_ALLOWED_SYSTEM);
        }
        String fileType = getFileType(file.getContentType());
        String originalFileName = file.getOriginalFilename();
        String extension = ""; // 获取文件扩展名
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        // 计算文件SHA-256哈希
        String fileHash = DigestUtils.sha256Hex(file.getInputStream());
        // 查询数据库，检查是否已存在相同哈希文件
        FileStorage fileStorage = getFileStorageSimpleByFileHash(fileSystemType, fileHash);
        if (fileStorage != null && fileStorage.getFilePath() != null) {
            if (!Objects.equals(fileStorage.getFileName(), originalFileName)) {
                updateFileName(originalFileName, fileHash);
            }
            return fileStorage.getFileHash();
        }

        // 计算存储路径
        Path typeDir = Paths.get(fileLoadProperties.getUploadBaseDir(), fileSystemType, fileType);
        if (!Files.exists(typeDir)) {
            Files.createDirectories(typeDir);
        }

        // 用哈希值作为文件名，避免文件名冲突
        String hashedFileName = fileHash + extension;
        Path targetPath = typeDir.resolve(hashedFileName);
        file.transferTo(targetPath.toFile());
        Path downloadDir = Paths.get(fileSystemType, fileType, hashedFileName);
        String normalizedPath = downloadDir.toString().replace(File.separator, "/");
        long fileSize = file.getSize();
        saveMetaDate(fileSystemType, originalFileName, fileType, fileHash, normalizedPath, fileSize);
        return fileHash;
    }

    @Override
    public String storeFile(MultipartFile multipartFile) {
        return "";
    }

    /**
     * 根据 MIME 类型分类文件夹
     */
    private String getFileType(String contentType) {
        if (contentType == null) {
            return "others";
        } else if (contentType.startsWith("image/")) {
            return "images";
        } else if (contentType.startsWith("video/")) {
            return "videos";
        } else if (contentType.startsWith("application/pdf") || contentType.startsWith("text/")) {
            return "documents";
        } else {
            return "others";
        }
    }

    /**
     * 分片元数据类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class ChunkMetadata {
        private String uploadId;          // 唯一上传会话ID
        private String fileName;          //文件名
        private String chunkHash;        // 该分片的哈希值
        private int chunkIndex;          // 分片索引
        private String chunkSize;          // 分片大小
        private String filePath;        // 文件路径
        private boolean verified;        // 是否已验证
        private int totalChunks;      // 总分片数
    }
}
