import { ref } from 'vue';

import { videoApi } from '@/api/video';

/**
 * 分片上传 Hook（切片上传 + 断点续传 + 秒传）
 *
 * 流程：
 *  1. 计算文件整体 SHA-256（用于秒传）；
 *  2. initChunk 创建上传任务（秒传命中则直接返回）；
 *  3. 查询已上传分片（断点续传），跳过；
 *  4. 并发 3 片上传剩余分片；
 *  5. completeChunk 合并并触发后端转码。
 *
 * uploadID 会持久化到 localStorage，页面刷新后可通过 restore 恢复进度。
 */

interface UploadOptions {
  courseId: number;
  chapterId?: number;
  title: string;
  trialSeconds?: number;
}

const STORAGE_KEY = 'video_chunk_upload';

/** 计算文件 SHA-256（hex）。200MB 以内直接读入内存，够用且无需额外依赖。 */
export async function calcFileSha256(file: File): Promise<string> {
  const buf = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buf);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export function useChunkUpload() {
  const uploading = ref(false);
  const percent = ref(0); // 0-100
  const currentChunk = ref(0);
  const totalChunks = ref(0);
  const errorMsg = ref('');
  const uploadID = ref('');

  /** 从 localStorage 读取上次未完成的 uploadID */
  function restoreUploadID(): string {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}').uploadID || '';
    } catch {
      return '';
    }
  }

  function saveUploadID(id: string) {
    uploadID.value = id;
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ uploadID: id, ts: Date.now() }));
  }

  function clearUploadID() {
    uploadID.value = '';
    localStorage.removeItem(STORAGE_KEY);
  }

  /**
   * 分片上传一个文件。
   * @param file 待上传文件
   * @param options 上传参数
   * @param onSuccess 上传完成回调（参数为秒传标志）
   */
  async function upload(file: File, options: UploadOptions, onSuccess?: (instant: boolean) => void) {
    uploading.value = true;
    errorMsg.value = '';
    percent.value = 0;

    try {
      // 1. 计算文件整体哈希（秒传依据）
      const fileHash = await calcFileSha256(file);
      const mimeType = file.type || 'video/mp4';

      // 2. 初始化上传任务
      const initRes: any = await videoApi.initChunk({
        courseId: options.courseId,
        chapterId: options.chapterId || 0,
        title: options.title,
        trialSeconds: options.trialSeconds || 0,
        fileName: file.name,
        mimeType,
        fileSize: file.size,
        fileHash,
      });

      // 秒传命中：后端已直接触发转码
      if (initRes.instant) {
        percent.value = 100;
        uploading.value = false;
        onSuccess?.(true);
        return;
      }

      const uid: string = initRes.uploadID;
      saveUploadID(uid);
      totalChunks.value = initRes.totalChunks;

      // 3. 断点续传：查询已上传分片，跳过
      const uploadedSet = new Set<number>();
      try {
        const prog: any = await videoApi.chunkProgress(uid);
        (prog.uploadedChunks || []).forEach((i: number) => uploadedSet.add(i));
        percent.value = Math.round(((uploadedSet.size / totalChunks.value) * 10000)) / 100;
      } catch {
        // 进度查询失败不阻塞，重新传所有分片
      }

      const chunkSize: number = initRes.chunkSize || 10 * 1024 * 1024;

      // 4. 并发上传剩余分片（最多 3 个）
      const pending: number[] = [];
      for (let i = 0; i < totalChunks.value; i += 1) {
        if (!uploadedSet.has(i)) pending.push(i);
      }

      let done = uploadedSet.size;
      const CONCURRENCY = 2;
      const failedChunks: number[] = [];
      const worker = async (idx: number) => {
        for (let i = idx; i < pending.length; i += CONCURRENCY) {
          const chunkIndex = pending[i];
          const start = chunkIndex * chunkSize;
          const blob = file.slice(start, Math.min(start + chunkSize, file.size));
          const fd = new FormData();
          fd.append('uploadId', uid);
          fd.append('chunkIndex', String(chunkIndex));
          fd.append('file', blob, `chunk_${chunkIndex}`);
          try {
            await videoApi.uploadChunk(fd);
            done += 1;
            currentChunk.value = done;
            percent.value = Math.round((done / totalChunks.value) * 10000) / 100;
          } catch (e) {
            // 单个分片失败不退出 worker，记录下来交给重试
            console.warn(`分片 ${chunkIndex} 上传失败:`, e);
            failedChunks.push(chunkIndex);
          }
        }
      };
      await Promise.all(Array.from({ length: Math.min(CONCURRENCY, pending.length) }, (_, i) => worker(i)));

      // 有分片失败：再重试一轮（断点续传）
      if (failedChunks.length > 0) {
        console.warn(`第一轮有 ${failedChunks.length} 个分片失败，准备重试`, failedChunks);
        const retriedFailed: number[] = [];
        for (const chunkIndex of failedChunks) {
          const start = chunkIndex * chunkSize;
          const blob = file.slice(start, Math.min(start + chunkSize, file.size));
          const fd = new FormData();
          fd.append('uploadId', uid);
          fd.append('chunkIndex', String(chunkIndex));
          fd.append('file', blob, `chunk_${chunkIndex}`);
          try {
            await videoApi.uploadChunk(fd);
            done += 1;
            currentChunk.value = done;
            percent.value = Math.round((done / totalChunks.value) * 10000) / 100;
          } catch (e) {
            retriedFailed.push(chunkIndex);
            console.error(`分片 ${chunkIndex} 重试仍失败:`, e);
          }
        }
        if (retriedFailed.length > 0) {
          throw new Error(`仍有 ${retriedFailed.length} 个分片上传失败: ${retriedFailed.join(', ')}`);
        }
      }

      // 5. 完成（合并分片 + 触发转码）
      await videoApi.completeChunk(uid);
      clearUploadID();
      percent.value = 100;
      uploading.value = false;
      onSuccess?.(false);
    } catch (e: any) {
      errorMsg.value = e?.message || '上传失败';
      uploading.value = false;
      throw e;
    }
  }

  /** 取消上传（清理 Multipart 任务与视频记录） */
  async function cancel() {
    if (uploadID.value) {
      try {
        await videoApi.abortChunk(uploadID.value);
      } catch {
        // 忽略取消失败
      }
      clearUploadID();
    }
    uploading.value = false;
  }

  return { uploading, percent, currentChunk, totalChunks, errorMsg, uploadID, upload, cancel, restoreUploadID };
}
