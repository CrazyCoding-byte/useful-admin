package com.yzx.model.filestorage;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Description:
 *
 * @author: aoxiang
 * @create: 2025-03-12 13:10
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDetailResponse {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 原文件名称
     */
    private String fileName;

    /**
     * 文件类型 (images, videos, documents 等)
     */
    private String fileType;

    /**
     * 文件哈希 (SHA-256 用于去重)
     */
    private String fileHash;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 上传用户ID (可选, 关联用户表)
     */
    private Long uploader;

    private String fileSystemType;

    /**
     * 上传时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
