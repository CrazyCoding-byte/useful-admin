import { request } from '@/utils/request';

// ==================== 教学视频模块 API ====================
// 后端服务：video（Go，端口 8890），路径前缀 /api/video
// 前端通过 vite 代理 /video -> http://127.0.0.1:8890，并去掉 /video 前缀

/** 课程 */
export interface Course {
  id?: number;
  title: string;
  description?: string;
  coverUrl?: string;
  price?: number; // 分
  isFree?: boolean;
  status?: number; // 0下架 1上架
}

/** 章节 */
export interface Chapter {
  id?: number;
  courseId: number;
  title: string;
  sortOrder?: number;
}

/** 视频 */
export interface Video {
  id?: number;
  courseId: number;
  chapterId?: number;
  title: string;
  sortOrder?: number;
  duration?: number; // 秒
  trialSeconds?: number; // 试看秒数
  originalObject?: string;
  hlsPath?: string;
  fullM3u8?: string;
  trialM3u8?: string;
  status?: number; // 0待转码 1已转码 2失败
  createdAt?: string;
}

export const courseApi = {
  list: (params: { page?: number; pageSize?: number; keyword?: string }) =>
    request.get({ url: '/video/api/video/course/list', params }),

  detail: (id: number) => request.get({ url: `/video/api/video/course/${id}/detail` }),

  create: (data: Course) => request.post({ url: '/video/api/video/course', data }),

  update: (data: Course) => request.put({ url: '/video/api/video/course', data }),

  remove: (id: number) => request.delete({ url: `/video/api/video/course/${id}` }),
};

export const chapterApi = {
  listByCourse: (courseId: number) =>
    request.get({ url: `/video/api/video/chapter/course/${courseId}` }),

  create: (data: Chapter) => request.post({ url: '/video/api/video/chapter', data }),

  update: (data: Chapter) => request.put({ url: '/video/api/video/chapter', data }),

  remove: (id: number) => request.delete({ url: `/video/api/video/chapter/${id}` }),
};

export const videoApi = {
  // 注意：video 服务在 main.go 里用 api.Group("/video") 单独建了视频子组，
  // 因此视频相关接口的完整路径是 /api/video/video/...，比课程/章节多一层 /video。
  // 只有 play/key 是直接挂在 /api/video/ 上的（公开/播放器直连）。
  listByCourse: (courseId: number) =>
    request.get({ url: `/video/api/video/video/course/${courseId}` }),

  get: (id: number) => request.get({ url: `/video/api/video/video/${id}` }),

  update: (data: Video) => request.put({ url: '/video/api/video/video', data }),

  remove: (id: number) => request.delete({ url: `/video/api/video/video/${id}` }),

  // 播放接口在 main.go 里是 api.GET("/play/:id")，路径不带额外 /video/
  playInfo: (id: number) => request.get({ url: `/video/api/video/play/${id}` }),

  // ---------- 分片上传（都在 video 子组下） ----------
  /** 初始化分片上传 */
  initChunk: (data: {
    courseId: number;
    chapterId?: number;
    title: string;
    trialSeconds?: number;
    fileName: string;
    mimeType: string;
    fileSize: number;
    fileHash: string;
  }) => request.post({ url: '/video/api/video/video/chunk/init', data }),

  /** 上传单个分片（multipart/form-data）。
   * 关键点：
   * 1) Content-Type 必须置空 —— request 实例默认 application/json 会覆盖 axios 自动生成的 boundary。
   * 2) 关闭 retry + 大 timeout —— 全局默认 retry=3，但响应拦截器 catch 路径里
   *    会把重试请求的 Content-Type 强制改回 application/json，导致重试 100% 失败；
   *    分片上传又长又不能容忍这种重试，所以这里直接关掉。 */
  uploadChunk: (formData: FormData) =>
    request.post(
      {
        url: '/video/api/video/video/chunk/upload',
        data: formData,
        headers: { 'Content-Type': undefined },
        timeout: 5 * 60 * 1000,
      } as any,
      { retry: { count: 0, delay: 0 } } as any,
    ),

  /** 查询上传进度（断点续传） */
  chunkProgress: (uploadID: string) =>
    request.get({ url: '/video/api/video/video/chunk/progress', params: { upload: uploadID } }),

  /** 完成分片上传（合并 + 触发转码） */
  completeChunk: (uploadId: string) =>
    request.post({ url: '/video/api/video/video/chunk/complete', data: { uploadId } }),

  /** 取消分片上传 */
  abortChunk: (uploadID: string) =>
    request.post({ url: '/video/api/video/video/chunk/abort', data: { uploadID } }),
};
