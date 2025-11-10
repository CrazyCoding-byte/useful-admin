package com.yzx.filestorage.controller;

import com.yzx.filestorage.service.IFileStorageService;
import com.yzx.model.Result;
import com.yzx.model.filestorage.FileDetailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * <p>
 * 文件存储表 前端控制器
 * </p>
 *
 * @author yzx
 * @since 2025-03-11
 */
@RestController
@RequestMapping("/files")
public class FileStorageController {
    @Autowired
    private IFileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<String> uploadFile(@RequestParam("fileSystemType") String fileSystemType,
            @RequestParam("file") MultipartFile file) throws IOException {
        String fileHash = fileStorageService.storeFile(fileSystemType, file);
        return Result.success(fileHash);
    }

    @PostMapping("/upload-url-file")
    public Result<FileDetailResponse> uploadFile(@RequestParam("fileSystemType") String fileSystemType,
            @RequestParam("downloadUrl") String downloadUrl,
            @RequestParam(value = "mimeType", required = false) String mimeType,
            @RequestParam(value = "fileName", required = false) String fileName) throws IOException {
        FileDetailResponse fileDetailResponse = fileStorageService.storeUrlFile(fileSystemType, downloadUrl, mimeType,
                fileName);
        return Result.success(fileDetailResponse);
    }

    @GetMapping("/getFileDetail")
    public Result<FileDetailResponse> getFileDetail(@RequestParam("fileHash") String fileHash,
            @RequestParam("fileSystemType") String fileSystemType) {
        return Result.success(fileStorageService.getFileDetail(fileHash, fileSystemType));
    }

    @PostMapping("/upload-to-minio")
    public Result<String> uploadFileToMinio(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(fileStorageService.storeFile(file));
    }

}
