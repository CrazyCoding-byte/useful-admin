package com.yzx.filestorage.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 文件存储表 服务实现类
 * </p>
 *
 * @author 翱翔
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

}
