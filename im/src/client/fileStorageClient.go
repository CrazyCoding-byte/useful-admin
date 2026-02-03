package client

import (
	"context"
	"fmt"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"io"
	"local/im/proto/filestorage"
	"log/slog"
	"time"
)

// FileStorageClient 文件存储 gRPC 客户端
type FileStorageClient struct {
	conn   *grpc.ClientConn
	client filestorage.FileStorageServiceClient
}

// NewFileStorageClient 创建文件存储客户端
func NewFileStorageClient(serverAddr string) (*FileStorageClient, error) {
	conn, err := grpc.NewClient(
		serverAddr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithBlock(),
		grpc.WithTimeout(10*time.Second),
	)
	if err != nil {
		return nil, fmt.Errorf("连接 gRPC 服务器失败: %w", err)
	}

	client := filestorage.NewFileStorageServiceClient(conn)

	return &FileStorageClient{
		conn:   conn,
		client: client,
	}, nil
}

// Close 关闭连接
func (c *FileStorageClient) Close() error {
	return c.conn.Close()
}

// UploadVideo 上传视频文件（兼容旧接口，小文件使用）
func (c *FileStorageClient) UploadVideo(ctx context.Context, fileSystemType string, fileName string, mimeType string, fileReader io.Reader) (*filestorage.FileDetailResponse, error) {
	// 先读取整个文件到内存，检查大小
	fileData, err := io.ReadAll(fileReader)
	if err != nil {
		return nil, fmt.Errorf("读取文件失败: %w", err)
	}

	// 如果文件大于50MB，建议使用分片上传
	if len(fileData) > 50*1024*1024 {
		slog.Warn("文件较大，建议使用分片上传接口", "size", len(fileData))
	}

	stream, err := c.client.UploadFile(ctx)
	if err != nil {
		return nil, fmt.Errorf("创建上传流失败: %w", err)
	}

	// 发送元数据
	metadata := &filestorage.UploadFileRequest{
		Data: &filestorage.UploadFileRequest_Metadata{
			Metadata: &filestorage.FileMetadata{
				FileSystemType: fileSystemType,
				FileName:       fileName,
				MimeType:       mimeType,
				FileSize:       int64(len(fileData)),
			},
		},
	}

	if err := stream.Send(metadata); err != nil {
		return nil, fmt.Errorf("发送元数据失败: %w", err)
	}

	// 分块发送文件数据
	const chunkSize = 4 * 1024 * 1024 // 4MB
	for offset := 0; offset < len(fileData); offset += chunkSize {
		end := offset + chunkSize
		if end > len(fileData) {
			end = len(fileData)
		}

		chunk := &filestorage.UploadFileRequest{
			Data: &filestorage.UploadFileRequest_Chunk{
				Chunk: fileData[offset:end],
			},
		}

		if err := stream.Send(chunk); err != nil {
			return nil, fmt.Errorf("发送文件块失败: %w", err)
		}
	}

	// 接收响应
	response, err := stream.CloseAndRecv()
	if err != nil {
		return nil, fmt.Errorf("接收响应失败: %w", err)
	}

	if !response.Success {
		return nil, fmt.Errorf("上传失败: %s", response.Message)
	}

	slog.Info("文件上传成功", "file_hash", response.FileDetail.FileHash)
	return response.FileDetail, nil
}

func (c *FileStorageClient) UploadChunk(ctx context.Context, req *filestorage.FileChunk) (*filestorage.UploadFileResponse, error) {
	//1.创建grpc流式客户端
	stream, err := c.client.UploadFileChunked(ctx)
	if err != nil {
		return nil, fmt.Errorf("创建分片上传流失败: %w", err)
	}
	//2.直接发送分片
	if err := stream.Send(req); err != nil {
		return nil, fmt.Errorf("发送分片失败: %w", err)
	}
	//3.关闭并接受java服务
	resp, err := stream.CloseAndRecv()
	if err != nil {
		return nil, fmt.Errorf("接收响应失败: %w", err)
	}
	if !resp.Success {
		return nil, fmt.Errorf("分片上传失败: %s", resp.Message)
	}
	slog.Info("分片上传成功", "upload_id", req.UploadId, "chunk_index", req.ChunkIndex, "progress", resp.Message)
	return resp, nil
}

// CompleteChunkedUpload 完成分片上传
func (c *FileStorageClient) CompleteChunkedUpload(ctx context.Context, req *filestorage.CompleteUploadRequest) (*filestorage.UploadFileResponse, error) {
	resp, err := c.client.CompleteChunkedUpload(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("完成分片上传失败: %w", err)
	}
	if !resp.Success {
		return nil, fmt.Errorf("分片上传失败: %s", resp.Message)
	}
	slog.Info("文件合并成功", "upload_id", req.UploadId, "file_hash", resp.FileDetail.FileHash)
	return resp, nil
}

// UploadVideoFromUrl 从URL上传视频
func (c *FileStorageClient) UploadVideoFromUrl(ctx context.Context, fileSystemType string, downloadUrl string, mimeType string, fileName string) (*filestorage.FileDetailResponse, error) {
	req := &filestorage.UploadFileFromUrlRequest{
		FileSystemType: fileSystemType,
		DownloadUrl:    downloadUrl,
		MimeType:       mimeType,
		FileName:       fileName,
	}

	resp, err := c.client.UploadFileFromUrl(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("从URL上传文件失败: %w", err)
	}

	if !resp.Success {
		return nil, fmt.Errorf("上传失败: %s", resp.Message)
	}

	return resp.FileDetail, nil
}

// GetFileDetail 获取文件详情
func (c *FileStorageClient) GetFileDetail(ctx context.Context, fileHash string, fileSystemType string) (*filestorage.FileDetailResponse, error) {
	req := &filestorage.GetFileDetailRequest{
		FileHash:       fileHash,
		FileSystemType: fileSystemType,
	}

	resp, err := c.client.GetFileDetail(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("获取文件详情失败: %w", err)
	}

	return resp, nil
}

// generateUploadID 生成唯一的上传ID
func generateUploadID() string {
	return fmt.Sprintf("upload_%d", time.Now().UnixNano())
}
