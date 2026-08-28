<template>
  <div class="video-manage">
    <!-- 顶部操作栏 -->
    <div class="op-bar">
      <t-button theme="primary" @click="openCourseDialog()">新建课程</t-button>
      <t-button variant="outline" @click="refreshCourses">刷新课程</t-button>
      <t-select
        v-model="filterStatus"
        class="status-filter"
        placeholder="视频状态"
        clearable
        :options="[
          { label: '待转码', value: 0 },
          { label: '已转码', value: 1 },
          { label: '转码失败', value: 2 },
        ]"
        @change="applyStatusFilter"
      />
    </div>

    <div class="main">
      <!-- 左侧：课程列表 -->
      <div class="course-panel">
        <div
          v-for="course in courses"
          :key="course.id"
          class="course-card"
          :class="{ active: currentCourse?.id === course.id }"
          @click="selectCourse(course)"
        >
          <div class="course-title">{{ course.title }}</div>
          <div class="course-meta">
            <t-tag :theme="course.isFree ? 'success' : 'warning'" size="small">
              {{ course.isFree ? '免费' : `¥${((course.price || 0) / 100).toFixed(2)}` }}
            </t-tag>
            <t-tag v-if="course.status === 0" theme="default" size="small">已下架</t-tag>
            <t-button variant="text" size="small" @click.stop="openCourseDialog(course)">编辑</t-button>
            <t-popconfirm content="确定删除该课程？" @confirm="deleteCourse(course)">
              <t-button variant="text" size="small" theme="danger">删除</t-button>
            </t-popconfirm>
          </div>
        </div>
        <t-empty v-if="!courses.length" description="暂无课程，先新建一个" />
      </div>

      <!-- 右侧：视频列表 -->
      <div class="video-panel">
        <div class="video-head">
          <span class="panel-title">
            {{ currentCourse ? `「${currentCourse.title}」的视频` : '请先选择课程' }}
          </span>
          <t-button theme="primary" :disabled="!currentCourse" @click="() => openUploadDialog()">
            上传视频
          </t-button>
        </div>

        <t-table
          :data="filteredVideos"
          :columns="videoColumns"
          row-key="id"
          :loading="videoLoading"
          :pagination="false"
        >
          <template #status="{ row }">
            <t-tag :theme="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">
              {{ row.status === 1 ? '已转码' : row.status === 2 ? '转码失败' : '转码中' }}
            </t-tag>
          </template>
          <template #duration="{ row }">
            {{ formatDuration(row.duration) }}
          </template>
          <template #op="{ row }">
            <t-button
              variant="text"
              size="small"
              :disabled="row.status !== 1"
              @click="() => playVideo(row, 'trial')"
            >
              播放
            </t-button>
            <t-button
              variant="text"
              theme="primary"
              size="small"
              :disabled="row.status !== 1"
              @click="() => playVideo(row, 'full')"
            >
              VIP 播放
            </t-button>
            <t-button variant="text" size="small" @click="() => openUploadDialog(row)">重新上传</t-button>
            <t-popconfirm content="确定删除该视频？" @confirm="() => deleteVideo(row)">
              <t-button variant="text" size="small" theme="danger">删除</t-button>
            </t-popconfirm>
          </template>
        </t-table>
        <t-empty v-if="!filteredVideos.length && !videoLoading" description="该课程下暂无视频" />
      </div>
    </div>

    <!-- 课程编辑弹窗 -->
    <t-dialog
      v-model:visible="courseDialogVisible"
      :header="courseForm.id ? '编辑课程' : '新建课程'"
      width="520px"
      :confirm-btn="{ content: '保存', loading: courseSaving }"
      :cancel-btn="{ content: '取消' }"
      @confirm="saveCourse"
    >
      <t-form :data="courseForm" label-width="90px">
        <t-form-item label="课程标题" name="title" :rules="[{ required: true, message: '请输入课程标题' }]">
          <t-input v-model="courseForm.title" placeholder="例如：Java 进阶实战" />
        </t-form-item>
        <t-form-item label="价格（元）">
          <t-input-number v-model="coursePrice" :min="0" :step="1" />
        </t-form-item>
        <t-form-item label="是否免费">
          <t-switch v-model="courseForm.isFree" />
        </t-form-item>
        <t-form-item label="课程描述">
          <t-textarea v-model="courseForm.description" :autosize="{ minRows: 2, maxRows: 5 }" />
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 上传视频弹窗 -->
    <t-dialog
      v-model:visible="uploadDialogVisible"
      header="上传视频"
      width="620px"
      :footer="false"
      :close-on-overlay-click="!uploading"
    >
      <t-form :data="uploadForm" label-width="90px">
        <t-form-item label="视频标题" name="title" :rules="[{ required: true, message: '请输入视频标题' }]">
          <t-input v-model="uploadForm.title" placeholder="例如：第3章 集合框架" />
        </t-form-item>
        <t-form-item label="所属章节">
          <t-select
            v-model="uploadForm.chapterId"
            placeholder="不选则直接挂在课程下"
            clearable
            :options="chapterOptions"
            :disabled="uploading"
          />
        </t-form-item>
        <t-form-item label="试看秒数">
          <t-input-number v-model="uploadForm.trialSeconds" :min="0" :max="600" :step="5" :disabled="uploading" />
        </t-form-item>
        <t-form-item label="选择文件">
          <input
            ref="fileInput"
            type="file"
            accept="video/*"
            class="file-input"
            :disabled="uploading"
            @change="onFileChange"
          />
          <div v-if="selectedFile" class="file-info">{{ selectedFile.name }}（{{ formatSize(selectedFile.size) }}）</div>
        </t-form-item>
        <t-form-item v-if="uploading || percent > 0" label="上传进度">
          <t-progress :percentage="percent" theme="plump" :label="`${percent}%`" />
        </t-form-item>
        <t-form-item v-if="errorMsg" label=" ">
          <t-alert theme="error" :message="errorMsg" />
        </t-form-item>
        <t-form-item label=" ">
          <t-button theme="primary" :loading="uploading" :disabled="!selectedFile || !uploadForm.title" @click="startUpload">
            {{ uploading ? '上传中...' : '开始上传' }}
          </t-button>
          <t-button v-if="uploading" theme="danger" variant="outline" @click="cancelUpload">取消</t-button>
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 视频播放弹窗（hls.js 加载 m3u8） -->
    <t-dialog
      v-model:visible="playDialogVisible"
      :header="playingTitle || '视频播放'"
      width="860px"
      :footer="false"
      :close-on-overlay-click="true"
      @close="closePlayer"
    >
      <div class="player-wrap">
        <video ref="playerRef" class="player-video" controls autoplay playsinline />
        <div v-if="playLoading" class="player-loading">正在加载视频…</div>
        <div v-if="qualityLevels.length > 0" class="player-quality">
          <span class="player-quality-label">清晰度：</span>
          <button
            class="quality-btn"
            :class="{ active: currentQuality === -1 }"
            @click="switchQuality(-1)"
          >自动</button>
          <button
            v-for="lv in qualityLevels"
            :key="lv.value"
            class="quality-btn"
            :class="{ active: currentQuality === lv.value }"
            @click="switchQuality(lv.value)"
          >{{ lv.label }}</button>
        </div>
        <t-alert
          v-if="!playingCanWatchFull && playingTitle"
          theme="warning"
          :message="`试看模式：前 ${playingTrialSeconds || 30} 秒可观看，VIP/购买后解锁完整版`"
          class="player-tip"
        />
        <t-alert
          v-else-if="playingCanWatchFull && playingMode === 'trial'"
          theme="info"
          :message="`管理员视角（试看路径）：仍能播完整版，仅用于验证试看链路`"
          class="player-tip"
        />
        <t-alert
          v-else-if="playingCanWatchFull && playingMode === 'full'"
          theme="success"
          :message="`管理员视角（完整版）：已开启 master.m3u8 多码率，可在清晰度按钮切换`"
          class="player-tip"
        />
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { MessagePlugin } from 'tdesign-vue-next';

import { chapterApi, courseApi, videoApi, type Course, type Video } from '@/api/video';
import { useChunkUpload } from '@/hooks/useChunkUpload';

// ---------- 课程 ----------
const courses = ref<Course[]>([]);
const currentCourse = ref<Course | null>(null);
const courseDialogVisible = ref(false);
const courseSaving = ref(false);
const courseForm = reactive<Course>({ title: '', description: '', price: 0, isFree: false, status: 1 });
const coursePrice = ref(0);

const { uploading, percent, errorMsg, upload, cancel } = useChunkUpload();

async function refreshCourses() {
  const res: any = await courseApi.list({ page: 1, pageSize: 100 });
  courses.value = res.list || [];
  // 若当前选中的课程还在，保持选中
  if (currentCourse.value) {
    const alive = courses.value.find((c) => c.id === currentCourse.value!.id);
    currentCourse.value = alive || null;
    if (alive) loadVideos();
  }
}

function selectCourse(course: Course) {
  currentCourse.value = course;
  loadVideos();
}

function openCourseDialog(course?: Course) {
  if (course) {
    Object.assign(courseForm, course);
    coursePrice.value = (course.price || 0) / 100;
  } else {
    Object.assign(courseForm, { id: undefined, title: '', description: '', price: 0, isFree: false, status: 1 });
    coursePrice.value = 0;
  }
  courseDialogVisible.value = true;
}

async function saveCourse() {
  if (!courseForm.title) {
    MessagePlugin.warning('请输入课程标题');
    return;
  }
  courseSaving.value = true;
  try {
    courseForm.price = Math.round(coursePrice.value * 100);
    if (courseForm.id) {
      await courseApi.update({ ...courseForm });
    } else {
      await courseApi.create({ ...courseForm });
    }
    MessagePlugin.success('保存成功');
    courseDialogVisible.value = false;
    await refreshCourses();
  } catch (e: any) {
    MessagePlugin.error(e?.message || '保存失败');
  } finally {
    courseSaving.value = false;
  }
}

async function deleteCourse(course: Course) {
  try {
    await courseApi.remove(course.id!);
    MessagePlugin.success('删除成功');
    if (currentCourse.value?.id === course.id) {
      currentCourse.value = null;
      videos.value = [];
    }
    await refreshCourses();
  } catch (e: any) {
    MessagePlugin.error(e?.message || '删除失败');
  }
}

// ---------- 章节 ----------
const chapterOptions = ref<{ label: string; value: number }[]>([]);

async function loadChapters(courseId: number) {
  try {
    const list: any = await chapterApi.listByCourse(courseId);
    chapterOptions.value = (list || []).map((c: any) => ({ label: c.title, value: c.id }));
  } catch {
    chapterOptions.value = [];
  }
}

// ---------- 视频 ----------
const videos = ref<Video[]>([]);
const videoLoading = ref(false);
const filterStatus = ref<number | undefined>(undefined);

const videoColumns = [
  { colKey: 'id', title: 'ID', width: 70 },
  { colKey: 'title', title: '标题', ellipsis: true },
  { colKey: 'chapterId', title: '章节ID', width: 90 },
  { colKey: 'duration', title: '时长', width: 90, slot: 'duration' },
  { colKey: 'trialSeconds', title: '试看(秒)', width: 90 },
  { colKey: 'status', title: '状态', width: 100, slot: 'status' },
  { colKey: 'op', title: '操作', width: 220, slot: 'op' },
];

const filteredVideos = computed(() =>
  filterStatus.value === undefined ? videos.value : videos.value.filter((v) => v.status === filterStatus.value),
);

function applyStatusFilter() {
  // 由 computed 自动过滤
}

async function loadVideos() {
  if (!currentCourse.value) return;
  videoLoading.value = true;
  try {
    videos.value = (await videoApi.listByCourse(currentCourse.value.id)) || [];
  } catch (e: any) {
    MessagePlugin.error(e?.message || '加载视频失败');
  } finally {
    videoLoading.value = false;
  }
}

async function deleteVideo(video: Video) {
  try {
    await videoApi.remove(video.id!);
    MessagePlugin.success('删除成功');
    await loadVideos();
  } catch (e: any) {
    MessagePlugin.error(e?.message || '删除失败');
  }
}

/**
 * 打开播放弹窗。
 * @param mode 'trial' 试看；'full' VIP 完整版（仅 admin 实际能拿到 master.m3u8，普通用户走后端 GetPlayInfo 仍返回 trial）
 */
function playVideo(video: Video, mode: 'trial' | 'full' = 'trial') {
  videoApi
    .playInfo(video.id!)
    .then((info: any) => {
      if (info?.m3u8Url) {
        playingTitle.value = info.title || video.title || '';
        playingTrialSeconds.value = info.trialSeconds || 0;
        playingCanWatchFull.value = !!info.canWatchFull;
        playingMode.value = mode;
        playDialogVisible.value = true;
        nextTick(() => initPlayer(info.m3u8Url));
      } else {
        MessagePlugin.warning('暂无播放地址');
      }
    })
    .catch((e: any) => MessagePlugin.error(e?.message || '获取播放地址失败'));
}

// ---------- 播放器（hls.js 加载 m3u8，Safari 原生支持则直接用 video.src） ----------
import Hls from 'hls.js';

const playDialogVisible = ref(false);
const playingTitle = ref('');
const playingTrialSeconds = ref(0);
const playingCanWatchFull = ref(true);
const playingMode = ref<'trial' | 'full'>('trial');
// 清晰度档位列表（hls.js 解析 master.m3u8 后填入）
const qualityLevels = ref<Array<{ label: string; value: number; height: number }>>([]);
const currentQuality = ref<number>(-1); // -1 = 自动
const playLoading = ref(false);
const playerRef = ref<HTMLVideoElement | null>(null);
let hls: Hls | null = null;

/** 手动切换清晰度。value = -1 表示恢复自动（hls.js 按带宽自适应）。 */
function switchQuality(value: number) {
  if (!hls) return;
  currentQuality.value = value;
  hls.currentLevel = value; // -1 自动；>=0 强制该档
}

function initPlayer(url: string) {
  destroyPlayer();
  const videoEl = playerRef.value;
  if (!videoEl) return;
  playLoading.value = true;
  qualityLevels.value = [];
  currentQuality.value = -1;

  if (Hls.isSupported()) {
    hls = new Hls();
    hls.loadSource(url);
    hls.attachMedia(videoEl);
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      playLoading.value = false;
      // master.m3u8 解析后填入档位列表
      qualityLevels.value = (hls?.levels || []).map((lvl, idx) => ({
        label: `${lvl.height || '?'}p`,
        value: idx,
        height: lvl.height || 0,
      }));
      videoEl.play().catch(() => {});
    });
    hls.on(Hls.Events.ERROR, (_e, data) => {
      if (data.fatal) {
        playLoading.value = false;
        MessagePlugin.error(`播放失败：${data.type} - ${data.details}`);
        hls?.destroy();
        hls = null;
      }
    });
  } else if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari / iOS 原生支持 m3u8
    videoEl.src = url;
    videoEl.addEventListener('loadedmetadata', () => {
      playLoading.value = false;
      videoEl.play().catch(() => {});
    });
  } else {
    playLoading.value = false;
    MessagePlugin.error('当前浏览器不支持 HLS 播放');
  }
}

function destroyPlayer() {
  if (hls) {
    hls.destroy();
    hls = null;
  }
  const videoEl = playerRef.value;
  if (videoEl) {
    videoEl.pause();
    videoEl.removeAttribute('src');
    videoEl.load();
  }
  playLoading.value = false;
}

function closePlayer() {
  destroyPlayer();
}

// ---------- 上传 ----------
const uploadDialogVisible = ref(false);
const uploadForm = reactive({ title: '', chapterId: undefined as number | undefined, trialSeconds: 30 });
const selectedFile = ref<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);

function openUploadDialog(video?: Video) {
  uploadForm.title = video ? video.title : '';
  uploadForm.chapterId = video?.chapterId || undefined;
  uploadForm.trialSeconds = video?.trialSeconds || 30;
  selectedFile.value = null;
  if (fileInput.value) fileInput.value.value = '';
  uploadDialogVisible.value = true;
  if (currentCourse.value) loadChapters(currentCourse.value.id);
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] || null;
}

async function startUpload() {
  if (!currentCourse.value || !selectedFile.value) return;
  try {
    await upload(selectedFile.value, {
      courseId: currentCourse.value.id,
      chapterId: uploadForm.chapterId,
      title: uploadForm.title,
      trialSeconds: uploadForm.trialSeconds,
    }, (instant) => {
      MessagePlugin.success(instant ? '文件已存在（秒传），已触发转码' : '上传完成，开始转码');
      uploadDialogVisible.value = false;
      loadVideos();
    });
  } catch (e: any) {
    MessagePlugin.error(e?.message || '上传失败');
  }
}

async function cancelUpload() {
  await cancel();
  MessagePlugin.info('已取消上传');
}

// ---------- 工具 ----------
function formatDuration(seconds?: number) {
  if (!seconds) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}

function formatSize(bytes: number) {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(2)}GB`;
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)}MB`;
  return `${(bytes / 1024).toFixed(1)}KB`;
}

onMounted(() => {
  refreshCourses();
});
</script>

<style scoped lang="less">
.video-manage {
  padding: 16px;
}

.op-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;

  .status-filter {
    width: 160px;
    margin-left: auto;
  }
}

.main {
  display: flex;
  gap: 16px;
  min-height: 480px;
}

.course-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--td-component-border);
  border-radius: 8px;
  padding: 12px;
  max-height: 640px;
  overflow-y: auto;

  .course-card {
    padding: 10px 12px;
    margin-bottom: 8px;
    border: 1px solid var(--td-component-border);
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: var(--td-brand-color);
    }

    &.active {
      border-color: var(--td-brand-color);
      background: var(--td-brand-color-light);
    }

    .course-title {
      font-weight: 600;
      margin-bottom: 6px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .course-meta {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }
}

.video-panel {
  flex: 1;
  border: 1px solid var(--td-component-border);
  border-radius: 8px;
  padding: 12px;

  .video-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .panel-title {
      font-size: 16px;
      font-weight: 600;
    }
  }
}

.file-input {
  width: 100%;
}

.file-info {
  margin-top: 6px;
  color: var(--td-text-color-secondary);
  font-size: 13px;
}

.player-wrap {
  position: relative;
  width: 100%;
  background: #000;
  border-radius: 6px;
  overflow: hidden;
}

.player-video {
  display: block;
  width: 100%;
  max-height: 480px;
  background: #000;
}

.player-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  font-size: 14px;
}

.player-tip {
  margin-top: 10px;
}

.player-quality {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 4px;
  flex-wrap: wrap;
}

.player-quality-label {
  color: var(--td-text-color-secondary);
  font-size: 13px;
  margin-right: 4px;
}

.quality-btn {
  background: var(--td-bg-color-component);
  border: 1px solid var(--td-component-border);
  color: var(--td-text-color-primary);
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.quality-btn:hover {
  background: var(--td-bg-color-component-hover);
}

.quality-btn.active {
  background: var(--brand-color, #0052d9);
  color: #fff;
  border-color: var(--brand-color, #0052d9);
}
</style>
