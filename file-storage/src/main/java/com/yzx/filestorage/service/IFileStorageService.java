package com.yzx.filestorage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yzx.filestorage.service.serviceimpl.FileStorageServiceImpl;
import com.yzx.model.filestorage.FileDetailResponse;
import com.yzx.model.filestorage.FileStorage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * <p>
 * 文件存储表 服务类
 * </p>
 *
 * @author yzx
 * @since 2025-03-11
 */
public interface IFileStorageService extends IService<FileStorage> {

    /**
     * 上传文件
     *
     * @param fileSystemType
     * @param file
     * @return String 返回文件哈希值
     * @author aoxiang
     * @create 2025/3/11
     **/
    String storeFile(String fileSystemType, MultipartFile file) throws IOException;

    String storeFile(MultipartFile multipartFile);

    /**
     * 查询文件详情
     *
     * @param fileHash
     * @param fileSystemType
     * @return FileDetailResponse
     * @author aoxiang
     * @create 2025/3/12
     **/
    FileDetailResponse getFileDetail(String fileHash, String fileSystemType);


    /**
     * 查询过期文件
     *
     * @param
     * @return List<FileStorage> 仅包含文件id与文件路径
     * @author aoxiang
     * @create 2025/3/25
     **/
    List<FileStorage> selectExpiredFile();

    /**
     * 删除过期文件
     *
     * @param fileStorageList
     * @return void
     * @author aoxiang
     * @create 2025/3/25
     **/
    void deleteExpiredFiles(List<FileStorage> fileStorageList);

    /**
     * 下载链接中的文件到本地
     *
     * @param fileSystemType
     * @param downloadUrl
     * @param mimeType 可以为空，为空则通过downloadUrl判断
     * @param fileName 可以为空，为空则通过downloadUrl判断
     * @return FileDetailResponse
     * @author aoxiang
     * @create 2025/4/10
     **/
    FileDetailResponse storeUrlFile(String fileSystemType, String downloadUrl, String mimeType, String fileName) throws IOException;

    // 修改分片上传方法，不需要数据库记录
    FileStorageServiceImpl.ChunkMetadata storeFileChunk(String uploadId, int chunkIndex,
                                                        int totalChunks, byte[] chunkData, String fileName);

    boolean isChunkComplete(String uploadId);

    String completeChunkUpload(String uploadId, String fileSystemType, String fileName, String mimeType) throws IOException;

    void cleanupChunkSession(String uploadId);
}
