<template>
  <div class="video-manage">
    <section class="library-hero">
      <div class="hero-copy">
        <div class="eyebrow"><t-icon name="play-circle" /> MEDIA OPERATIONS</div>
        <h1>视频资产中枢</h1>
        <p>管理课程内容、转码状态与多码率播放资产。</p>
      </div>
      <div class="hero-stats" aria-label="视频库概览">
        <div class="hero-stat"><strong>{{ courses.length }}</strong><span>课程</span></div>
        <div class="hero-stat"><strong>{{ videos.length }}</strong><span>当前视频</span></div>
        <div class="hero-stat"><strong>{{ readyVideoCount }}</strong><span>可播放</span></div>
      </div>
    </section>

    <div class="op-bar">
      <div class="toolbar-leading">
        <span class="toolbar-kicker">CONTENT LIBRARY</span>
        <span class="toolbar-count">{{ filteredVideos.length }} 项内容</span>
      </div>
      <div class="toolbar-actions">
        <t-select
          v-model="filterStatus"
          class="status-filter"
          placeholder="全部状态"
          clearable
          :options="[
            { label: '待转码', value: 0 },
            { label: '已转码', value: 1 },
            { label: '转码失败', value: 2 },
          ]"
          @change="applyStatusFilter"
        />
        <t-button variant="outline" @click="refreshCourses">
          <template #icon><t-icon name="refresh" /></template>
          刷新
        </t-button>
        <t-button theme="primary" @click="openCourseDialog()">
          <template #icon><t-icon name="add" /></template>
          新建课程
        </t-button>
      </div>
    </div>

    <div class="main">
      <!-- 左侧：课程列表 -->
      <div class="course-panel">
        <div class="course-panel-head">
          <div>
            <span class="section-label">COURSE TRACKS</span>
            <h2>课程轨道</h2>
          </div>
          <span class="panel-count">{{ courses.length }}</span>
        </div>
        <div
          v-for="course in courses"
          :key="course.id"
          class="course-card"
          :class="{ active: currentCourse?.id === course.id }"
          @click="selectCourse(course)"
        >
          <div class="course-index">{{ String(courses.indexOf(course) + 1).padStart(2, '0') }}</div>
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

      <!-- 中间：章节列表 -->
      <div class="chapter-panel">
        <div class="chapter-panel-head">
          <div>
            <span class="section-label">CHAPTER TRACKS</span>
            <h2>章节轨道</h2>
          </div>
          <t-button variant="text" theme="primary" size="small" :disabled="!currentCourse" @click="openChapterDialog()">
            <template #icon><t-icon name="add" /></template>
            章节
          </t-button>
        </div>
        <div v-if="currentCourse" class="chapter-tree">
          <div class="chapter-item" :class="{ active: currentChapterId === null }" @click="selectChapter(null)">
            <t-icon name="folder-open-1" class="chapter-icon" />
            <span class="chapter-title">全部视频</span>
          </div>
          <div class="chapter-item" :class="{ active: currentChapterId === 'uncategorized' }" @click="selectChapter('uncategorized')">
            <t-icon name="folder-1" class="chapter-icon" />
            <span class="chapter-title">未分类视频</span>
          </div>
          <div
            v-for="item in flatChapters"
            :key="item.id"
            class="chapter-item"
            :class="{ active: currentChapterId === item.id }"
            :style="{ paddingLeft: 12 + item.level * 14 + 'px' }"
            @click="selectChapter(item.id)"
          >
            <span class="chapter-toggle" @click.stop="toggleChapter(item.id)">
              <t-icon v-if="item.hasChildren" :name="expandedIds.has(item.id) ? 'chevron-down' : 'chevron-right'" />
            </span>
            <t-icon :name="item.hasChildren ? 'folder-1' : 'play-circle'" class="chapter-icon" />
            <span class="chapter-title">{{ item.title }}</span>
            <div class="chapter-actions">
              <t-button variant="text" size="small" @click.stop="openChapterDialog(undefined, item.id)">
                <t-icon name="add" />
              </t-button>
              <t-button variant="text" size="small" @click.stop="openChapterDialog(item.raw)">
                <t-icon name="edit-1" />
              </t-button>
              <t-popconfirm content="确定删除该章节？" @confirm="deleteChapter(item.raw)">
                <t-button variant="text" size="small" theme="danger">
                  <t-icon name="delete" />
                </t-button>
              </t-popconfirm>
            </div>
          </div>
        </div>
        <t-empty v-else description="先选择一门课程" />
      </div>

      <!-- 右侧：视频列表 -->
      <div class="video-panel">
        <div class="video-head">
          <div class="video-heading">
            <span class="section-label">VIDEO ASSETS</span>
            <h2>{{ videoPanelTitle }}</h2>
            <p>{{ videoPanelSubtitle }}</p>
          </div>
          <t-button theme="primary" :disabled="!currentCourse" @click="() => openUploadDialog()">
            <template #icon><t-icon name="upload" /></template>
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
        <t-empty v-if="!filteredVideos.length && !videoLoading" :description="currentCourse ? '当前节点下暂无视频' : '先选择课程和章节'" />
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

    <!-- 章节编辑弹窗 -->
    <t-dialog
      v-model:visible="chapterDialogVisible"
      :header="chapterForm.id ? '编辑章节' : '新建章节'"
      width="520px"
      :confirm-btn="{ content: '保存', loading: chapterSaving }"
      :cancel-btn="{ content: '取消' }"
      @confirm="saveChapter"
    >
      <t-form :data="chapterForm" label-width="90px">
        <t-form-item label="章节标题" name="title" :rules="[{ required: true, message: '请输入章节标题' }]">
          <t-input v-model="chapterForm.title" placeholder="例如：第一章 基础入门" />
        </t-form-item>
        <t-form-item label="父章节">
          <t-select v-model="chapterForm.parentId" :options="parentChapterOptions" placeholder="不选则作为顶层章节" clearable />
        </t-form-item>
        <t-form-item label="排序号">
          <t-input-number v-model="chapterForm.sortOrder" :min="0" :step="1" />
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
        <div v-if="playLoading" class="player-loading">
          {{ switchingQuality ? '正在切换清晰度，音视频已暂停…' : '正在加载视频…' }}
        </div>
        <div v-if="qualityLevels.length > 1" class="player-quality">
          <span class="player-quality-label">清晰度：</span>
          <button
            class="quality-btn"
            :class="{ active: currentQuality === -1 }"
            :disabled="switchingQuality"
            @click="switchQuality(-1)"
          >自动</button>
          <button
            v-for="lv in qualityLevels"
            :key="lv.value"
            class="quality-btn"
            :class="{ active: currentQuality === lv.value }"
            :disabled="switchingQuality"
            @click="switchQuality(lv.value)"
          >{{ lv.label }}</button>
        </div>
        <t-alert
          v-if="playLimitSeconds > 0 && playingTitle"
          theme="warning"
          :message="limitReached ? `试看已结束（${playLimitSeconds} 秒），VIP/购买后解锁完整版` : `试看模式：前 ${playLimitSeconds} 秒可观看，VIP/购买后解锁完整版`"
          class="player-tip"
        />
        <t-alert
          v-else-if="playingCanWatchFull && playingMode === 'trial'"
          theme="info"
          :message="`试看路径：前 ${playLimitSeconds || playingTrialSeconds || 30} 秒可观看`"
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

import Hls from 'hls.js';
import { MessagePlugin } from 'tdesign-vue-next';

import { chapterApi, courseApi, videoApi, type Course, type Chapter, type Video } from '@/api/video';
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
    if (alive) {
      await loadChaptersTree();
      loadVideos();
    }
  }
}

function selectCourse(course: Course) {
  currentCourse.value = course;
  currentChapterId.value = null;
  expandedIds.value = new Set();
  loadChaptersTree();
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
      chapters.value = [];
      currentChapterId.value = null;
    }
    await refreshCourses();
  } catch (e: any) {
    MessagePlugin.error(e?.message || '删除失败');
  }
}

// ---------- 章节 ----------
const chapters = ref<Chapter[]>([]);
const currentChapterId = ref<number | 'uncategorized' | null>(null);
const chapterDialogVisible = ref(false);
const chapterSaving = ref(false);
const chapterForm = reactive<Chapter>({ courseId: 0, parentId: 0, title: '', sortOrder: 0 });
const expandedIds = ref<Set<number>>(new Set());

const flatChapters = computed(() => {
  const result: { id: number; title: string; level: number; hasChildren: boolean; raw: Chapter }[] = [];
  function walk(list: Chapter[], level: number) {
    for (const ch of list) {
      const hasChildren = (ch.children?.length || 0) > 0;
      result.push({ id: ch.id!, title: ch.title, level, hasChildren, raw: ch });
      if (hasChildren && expandedIds.value.has(ch.id!)) {
        walk(ch.children!, level + 1);
      }
    }
  }
  walk(chapters.value, 0);
  return result;
});

const chapterOptions = computed(() => {
  const opts: { label: string; value: number }[] = [{ label: '直接挂在课程下', value: 0 }];
  function walk(list: Chapter[], prefix: string) {
    for (const ch of list) {
      opts.push({ label: prefix + ch.title, value: ch.id! });
      if (ch.children?.length) walk(ch.children, prefix + '　');
    }
  }
  walk(chapters.value, '');
  return opts;
});

const parentChapterOptions = computed(() => {
  const opts: { label: string; value: number }[] = [{ label: '顶层章节', value: 0 }];
  function walk(list: Chapter[], prefix: string) {
    for (const ch of list) {
      if (ch.id === chapterForm.id) continue;
      opts.push({ label: prefix + ch.title, value: ch.id! });
      if (ch.children?.length) walk(ch.children, prefix + '　');
    }
  }
  walk(chapters.value, '');
  return opts;
});

async function loadChaptersTree() {
  if (!currentCourse.value) return;
  try {
    const res: any = await courseApi.detail(currentCourse.value.id!);
    chapters.value = res.chapters || [];
    const ids = new Set<number>();
    (res.chapters || []).forEach((ch: Chapter) => ids.add(ch.id!));
    expandedIds.value = ids;
  } catch {
    chapters.value = [];
    expandedIds.value = new Set();
  }
}

function selectChapter(id: number | 'uncategorized' | null) {
  currentChapterId.value = id;
}

function toggleChapter(id: number) {
  const next = new Set(expandedIds.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  expandedIds.value = next;
}

function openChapterDialog(chapter?: Chapter, parentId?: number) {
  if (chapter) {
    Object.assign(chapterForm, chapter);
  } else {
    Object.assign(chapterForm, {
      id: undefined,
      courseId: currentCourse.value?.id,
      parentId: parentId ?? (typeof currentChapterId.value === 'number' ? currentChapterId.value : 0),
      title: '',
      sortOrder: 0,
    });
  }
  chapterDialogVisible.value = true;
}

async function saveChapter() {
  if (!chapterForm.title) {
    MessagePlugin.warning('请输入章节标题');
    return;
  }
  if (!currentCourse.value) return;
  chapterSaving.value = true;
  try {
    chapterForm.courseId = currentCourse.value.id!;
    if (chapterForm.id) {
      await chapterApi.update({ ...chapterForm });
    } else {
      await chapterApi.create({ ...chapterForm });
    }
    MessagePlugin.success('保存成功');
    chapterDialogVisible.value = false;
    await loadChaptersTree();
  } catch (e: any) {
    MessagePlugin.error(e?.message || '保存失败');
  } finally {
    chapterSaving.value = false;
  }
}

async function deleteChapter(chapter: Chapter) {
  try {
    await chapterApi.remove(chapter.id!);
    MessagePlugin.success('删除成功');
    if (currentChapterId.value === chapter.id) {
      currentChapterId.value = null;
    }
    await loadChaptersTree();
  } catch (e: any) {
    MessagePlugin.error(e?.message || '删除失败');
  }
}

// ---------- 视频 ----------
const videos = ref<Video[]>([]);
const videoLoading = ref(false);
const filterStatus = ref<number | undefined>(undefined);

const videoColumns = [
  { colKey: 'id', title: 'ID', width: 70 },
  { colKey: 'title', title: '标题', ellipsis: true },
  { colKey: 'duration', title: '时长', width: 90, slot: 'duration' },
  { colKey: 'trialSeconds', title: '试看(秒)', width: 90 },
  { colKey: 'status', title: '状态', width: 100, slot: 'status' },
  { colKey: 'op', title: '操作', width: 220, slot: 'op' },
];

const filteredVideos = computed(() => {
  let list = videos.value;
  if (currentChapterId.value === 'uncategorized') {
    list = list.filter((v) => !v.chapterId);
  } else if (currentChapterId.value !== null) {
    list = list.filter((v) => v.chapterId === currentChapterId.value);
  }
  if (filterStatus.value !== undefined) {
    list = list.filter((v) => v.status === filterStatus.value);
  }
  return list;
});

const readyVideoCount = computed(() => videos.value.filter((video) => video.status === 1).length);

const videoPanelTitle = computed(() => {
  if (!currentCourse.value) return '选择一条课程轨道';
  if (currentChapterId.value === 'uncategorized') return `${currentCourse.value.title} / 未分类视频`;
  if (currentChapterId.value === null) return currentCourse.value.title;
  const find = (list: Chapter[]): Chapter | null => {
    for (const ch of list) {
      if (ch.id === currentChapterId.value) return ch;
      if (ch.children?.length) {
        const found = find(ch.children);
        if (found) return found;
      }
    }
    return null;
  };
  const ch = find(chapters.value);
  return ch ? `${currentCourse.value.title} / ${ch.title}` : currentCourse.value.title;
});

const videoPanelSubtitle = computed(() => {
  if (!currentCourse.value) return '从左侧选择课程开始管理视频';
  return '维护视频版本、转码状态和访问策略';
});

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
    .playInfo(video.id!, mode)
    .then((info: any) => {
      if (info?.m3u8Url) {
        playingTitle.value = info.title || video.title || '';
        playingTrialSeconds.value = info.trialSeconds || 0;
        playingCanWatchFull.value = !!info.canWatchFull;
        playingMode.value = info.playMode || mode;
        playLimitSeconds.value = Number(info.maxPlaySeconds || 0);
        limitReached.value = false;
        playDialogVisible.value = true;
        nextTick(() => initPlayer(info.m3u8Url));
      } else {
        MessagePlugin.warning('暂无播放地址');
      }
    })
    .catch((e: any) => MessagePlugin.error(e?.message || '获取播放地址失败'));
}

// ---------- 播放器（hls.js 加载 m3u8，Safari 原生支持则直接用 video.src） ----------

const playDialogVisible = ref(false);
const playingTitle = ref('');
const playingTrialSeconds = ref(0);
const playingCanWatchFull = ref(true);
const playingMode = ref<'trial' | 'full'>('trial');
// 清晰度档位列表（hls.js 解析 master.m3u8 后填入）
const qualityLevels = ref<Array<{ label: string; value: number; height: number }>>([]);
const currentQuality = ref<number>(-1); // -1 = 自动
const playLoading = ref(false);
const switchingQuality = ref(false);
const playLimitSeconds = ref(0);
const limitReached = ref(false);
const playerRef = ref<HTMLVideoElement | null>(null);
let hls: Hls | null = null;
let qualitySwitchSeq = 0;

/**
 * 手动切换清晰度：先暂停并记住时间点，等 hls.js 真正切到目标档位后再定位并恢复播放。
 * 这样旧档位的音频不会在新档位缓冲期间继续播放。
 */
async function switchQuality(value: number) {
  const instance = hls;
  const videoEl = playerRef.value;
  if (!instance || !videoEl || switchingQuality.value || value === currentQuality.value) return;

  const seq = ++qualitySwitchSeq;
  const resumeTime = Number.isFinite(videoEl.currentTime) ? videoEl.currentTime : 0;
  const shouldResume = !videoEl.paused && !videoEl.ended;
  // 自动档已经落在目标档位时，hls.js 不会再次触发 LEVEL_SWITCHED，直接恢复即可。
  if (value >= 0 && instance.currentLevel === value) {
    currentQuality.value = value;
    if (shouldResume) videoEl.play().catch(() => {});
    return;
  }
  videoEl.pause();
  switchingQuality.value = true;
  playLoading.value = true;

  const switched = await new Promise<boolean>((resolve) => {
    let settled = false;
    const finish = (success: boolean) => {
      if (settled) return;
      settled = true;
      instance.off(Hls.Events.LEVEL_SWITCHED, onLevelSwitched);
      instance.off(Hls.Events.FRAG_BUFFERED, onFragBuffered);
      window.clearTimeout(timer);
      resolve(success);
    };
    const onLevelSwitched = (_event: string, data: { level: number }) => {
      if (value === -1 || data.level === value) finish(true);
    };
    const onFragBuffered = (_event: string, data: { frag?: { level?: number } }) => {
      if (value === -1 || data.frag?.level === value) finish(true);
    };
    const timer = window.setTimeout(() => finish(false), 8000);
    instance.on(Hls.Events.LEVEL_SWITCHED, onLevelSwitched);
    instance.on(Hls.Events.FRAG_BUFFERED, onFragBuffered);
    instance.currentLevel = value; // -1 自动；>=0 强制该档
  });

  if (seq !== qualitySwitchSeq || hls !== instance) return;
  if (!switched) {
    switchingQuality.value = false;
    playLoading.value = false;
    MessagePlugin.warning('清晰度切换超时，视频保持暂停');
    return;
  }
  if (Number.isFinite(resumeTime)) {
    try {
      videoEl.currentTime = Math.min(resumeTime, playLimitSeconds.value > 0 ? playLimitSeconds.value : resumeTime);
    } catch {
      // 新清晰度尚未暴露 duration 时，保持播放器默认定位。
    }
  }
  currentQuality.value = value;
  switchingQuality.value = false;
  playLoading.value = false;
  if (shouldResume) videoEl.play().catch(() => {});
}

function initPlayer(url: string) {
  destroyPlayer();
  const videoEl = playerRef.value;
  if (!videoEl) return;
  playLoading.value = true;
  qualityLevels.value = [];
  currentQuality.value = -1;
  switchingQuality.value = false;
  limitReached.value = false;
  videoEl.addEventListener('timeupdate', enforcePlaybackLimit);
  videoEl.addEventListener('seeking', enforcePlaybackLimit);

  if (Hls.isSupported()) {
    hls = new Hls();
    hls.loadSource(url);
    hls.attachMedia(videoEl);
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      playLoading.value = false;
      // master.m3u8 解析后填入档位列表
      qualityLevels.value = (hls?.levels || [])
        .map((lvl, idx) => {
          const url = lvl.url?.[0] || '';
          const pathHeight = Number(url.match(/(?:full_|_)(480|720|1080)(?:\.m3u8)?/)?.[1] || 0);
          const height = lvl.height || pathHeight;
          return {
            label: height > 0 ? `${height}p` : `清晰度${idx + 1}`,
            value: idx,
            height,
          };
        });
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
  qualitySwitchSeq += 1;
  if (hls) {
    hls.destroy();
    hls = null;
  }
  const videoEl = playerRef.value;
  if (videoEl) {
    videoEl.removeEventListener('timeupdate', enforcePlaybackLimit);
    videoEl.removeEventListener('seeking', enforcePlaybackLimit);
    videoEl.pause();
    videoEl.removeAttribute('src');
    videoEl.load();
  }
  playLoading.value = false;
  switchingQuality.value = false;
}

/** 对试看播放做前端硬截止，防止用户拖动进度条越过后继续听到音频。 */
function enforcePlaybackLimit() {
  const videoEl = playerRef.value;
  const limit = playLimitSeconds.value;
  if (!videoEl || limit <= 0 || !Number.isFinite(videoEl.currentTime)) return;
  if (videoEl.currentTime >= limit) {
    videoEl.currentTime = limit;
    videoEl.pause();
    limitReached.value = true;
  }
}

function closePlayer() {
  destroyPlayer();
  playLimitSeconds.value = 0;
  limitReached.value = false;
}

// ---------- 上传 ----------
const uploadDialogVisible = ref(false);
const uploadForm = reactive({ title: '', chapterId: undefined as number | undefined, trialSeconds: 30 });
const selectedFile = ref<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);

function openUploadDialog(video?: Video) {
  uploadForm.title = video ? video.title : '';
  uploadForm.chapterId = video?.chapterId ?? (typeof currentChapterId.value === 'number' ? currentChapterId.value : undefined);
  uploadForm.trialSeconds = video?.trialSeconds || 30;
  selectedFile.value = null;
  if (fileInput.value) fileInput.value.value = '';
  uploadDialogVisible.value = true;
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
  /* 局部质感变量：只影响当前页面，避免污染全局 theme */
  --video-surface: #0c1a2b;
  --video-surface-raised: #122236;
  --video-surface-soft: rgba(13, 28, 47, 0.96);
  --video-border: var(--app-line);
  --video-border-strong: var(--app-line-strong);
  --video-highlight: var(--app-highlight);
  --video-glow: rgba(100, 229, 224, 0.16);
  --video-shadow: 0 18px 46px rgba(0, 0, 0, 0.34), 0 2px 8px rgba(0, 0, 0, 0.2);
  --video-shadow-sm: 0 8px 24px rgba(0, 0, 0, 0.26);
  /* 统一缓动：收尾极缓，消除"直接到位"的僵硬感 */
  --video-ease: cubic-bezier(0.22, 1, 0.36, 1);
  --video-ease-spring: cubic-bezier(0.34, 1.36, 0.64, 1);
  --video-dur: 0.32s;
  --video-dur-fast: 0.18s;

  max-width: 1600px;
  margin: 0 auto;
  padding: 4px 0 24px;
  color: var(--td-text-color-primary);
}

.library-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  padding: 28px 32px;
  margin-bottom: 20px;
  border: 1px solid var(--video-border-strong);
  border-radius: var(--app-radius);
  background:
    radial-gradient(140% 200% at 100% 0%, var(--video-glow), transparent 48%),
    linear-gradient(165deg, var(--video-surface-raised) 0%, var(--video-surface) 100%);
  box-shadow:
    var(--video-shadow),
    inset 0 1px 0 var(--video-highlight);
  overflow: hidden;
  position: relative;
  animation: video-rise var(--video-dur) var(--video-ease) backwards;
}

.library-hero::after {
  content: '';
  position: absolute;
  width: 420px;
  height: 1px;
  right: 0;
  top: 38px;
  background: linear-gradient(90deg, transparent, rgba(100, 229, 224, 0.9), rgba(108, 155, 255, 0.45));
}

.hero-copy,
.hero-stats {
  position: relative;
  z-index: 1;
}

.eyebrow,
.section-label,
.toolbar-kicker {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--app-cyan);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.eyebrow {
  margin-bottom: 12px;
}

.library-hero h1 {
  margin: 0;
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.1;
  letter-spacing: -0.02em;
  text-shadow: 0 0 24px rgba(100, 229, 224, 0.18);
}

.library-hero p {
  margin-top: 10px;
  color: var(--td-text-color-secondary);
}

.hero-stats {
  display: flex;
  gap: 32px;
  padding: 4px 0;
}

.hero-stat {
  min-width: 80px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hero-stat strong {
  font-size: 32px;
  line-height: 1;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 0 20px rgba(100, 229, 224, 0.35);
}

.hero-stat span {
  color: var(--td-text-color-secondary);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.op-bar {
  min-height: 56px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 10px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--video-border);
  border-radius: 12px;
  background: linear-gradient(180deg, var(--video-surface-raised) 0%, var(--video-surface) 100%);
  box-shadow: var(--video-shadow-sm), inset 0 1px 0 var(--video-highlight);
  animation: video-rise var(--video-dur) var(--video-ease) 0.06s backwards;
}

.toolbar-leading,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-count {
  color: var(--td-text-color-secondary);
  font-size: 12px;
}

.status-filter {
  width: 150px;
}

.main {
  display: grid;
  grid-template-columns: minmax(220px, 240px) minmax(220px, 260px) minmax(0, 1fr);
  gap: 18px;
  min-height: 480px;
}

.course-panel,
.video-panel {
  border: 1px solid var(--video-border);
  border-radius: var(--app-radius);
  background: linear-gradient(180deg, var(--video-surface-raised) 0%, var(--video-surface) 100%);
  box-shadow: var(--video-shadow), inset 0 1px 0 var(--video-highlight);
  animation: video-rise var(--video-dur) var(--video-ease) backwards;
}

.course-panel {
  padding: 16px 12px;
  max-height: 680px;
  overflow-y: auto;
  animation-delay: 0.12s;
}

.course-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 3px 7px 14px;
  border-bottom: 1px solid var(--video-border);
  margin-bottom: 14px;
}

.course-panel h2,
.video-heading h2 {
  margin: 5px 0 0;
  color: var(--td-text-color-primary);
  font-size: 18px;
  line-height: 1.2;
}

.panel-count {
  display: inline-grid;
  place-items: center;
  min-width: 26px;
  height: 26px;
  border-radius: 8px;
  color: var(--app-cyan);
  background: rgba(100, 229, 224, 0.1);
  border: 1px solid rgba(100, 229, 224, 0.2);
  font-size: 12px;
  font-weight: 700;
}

.course-card {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 2px 10px;
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid var(--video-border);
  border-radius: 10px;
  cursor: pointer;
  background-color: var(--video-surface-soft);
  transition:
    transform var(--video-dur) var(--video-ease),
    background-color var(--video-dur-fast) var(--video-ease),
    border-color var(--video-dur-fast) var(--video-ease),
    box-shadow var(--video-dur) var(--video-ease);
  animation: video-rise var(--video-dur) var(--video-ease) backwards;
  will-change: transform;
}

.course-card:hover {
  border-color: var(--video-border-strong);
  background-color: rgba(100, 229, 224, 0.08);
  box-shadow:
    0 0 0 1px rgba(100, 229, 224, 0.06),
    0 8px 24px rgba(0, 0, 0, 0.22);
  transform: translate3d(3px, 0, 0);
}

.course-card.active {
  border-color: var(--app-cyan);
  background-color: rgba(100, 229, 224, 0.1);
  background-image: linear-gradient(to right, var(--app-cyan) 3px, transparent 3px);
  background-size: 3px 100%;
  background-repeat: no-repeat;
  box-shadow: 0 0 18px rgba(100, 229, 224, 0.12);
}

/* 课程卡片依次渐入，避免整块内容"一次性直接出来"
   注意：.course-panel 的第一个子元素是 .course-panel-head，所以卡片从 nth-child(2) 起算 */
.course-panel .course-card:nth-child(2) {
  animation-delay: 0.16s;
}

.course-panel .course-card:nth-child(3) {
  animation-delay: 0.21s;
}

.course-panel .course-card:nth-child(4) {
  animation-delay: 0.26s;
}

.course-panel .course-card:nth-child(5) {
  animation-delay: 0.31s;
}

.course-panel .course-card:nth-child(6) {
  animation-delay: 0.36s;
}

.course-panel .course-card:nth-child(7) {
  animation-delay: 0.41s;
}

.course-panel .course-card:nth-child(n + 8) {
  animation-delay: 0.46s;
}

.course-index {
  grid-row: 1 / span 2;
  color: var(--app-cyan);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  padding-top: 2px;
}

.course-title {
  min-width: 0;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--td-text-color-primary);
  font-weight: 650;
}

.course-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}

.course-meta :deep(.t-button) {
  padding: 0 5px;
  min-height: 22px;
  font-size: 12px;
}

.video-panel {
  min-width: 0;
  padding: 18px;
  animation-delay: 0.16s;
}

.video-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 2px 2px 18px;
  margin-bottom: 2px;
  border-bottom: 1px solid var(--video-border);
}

.video-heading p {
  margin: 7px 0 0;
  color: var(--td-text-color-secondary);
  font-size: 12px;
}

.video-panel :deep(.t-table) {
  margin-top: 14px;
}

.video-panel :deep(.t-table th) {
  color: var(--td-text-color-secondary);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  background: transparent !important;
  border-bottom: 1px solid var(--video-border) !important;
}

.video-panel :deep(.t-table td) {
  color: var(--td-text-color-primary);
  border-bottom: 1px solid rgba(141, 195, 229, 0.08) !important;
  transition: background-color var(--video-dur-fast) var(--video-ease);
}

.video-panel :deep(.t-table tbody tr:hover td),
.video-panel :deep(.t-table__body tr:hover td),
.video-panel :deep(.t-table__tr--hover td) {
  background-color: rgba(100, 229, 224, 0.06) !important;
}

.video-panel :deep(.t-table .t-button) {
  padding: 0 5px;
  min-height: 26px;
  font-size: 12px;
}

.file-input {
  width: 100%;
  padding: 10px;
  border: 1px dashed var(--video-border-strong);
  border-radius: 9px;
  color: var(--td-text-color-secondary);
  background: var(--video-surface-soft);
  transition: border-color var(--video-dur-fast) var(--video-ease), background-color var(--video-dur-fast) var(--video-ease);
}

.file-input:hover {
  border-color: var(--app-cyan);
  background-color: rgba(100, 229, 224, 0.06);
}

.file-info {
  margin-top: 7px;
  color: var(--app-cyan);
  font-size: 12px;
}

.player-wrap {
  position: relative;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--video-border);
  border-radius: 12px;
  background: #030912;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.player-video {
  display: block;
  width: 100%;
  max-height: 480px;
  border-radius: 7px;
  background: #02060b;
}

.player-loading {
  position: absolute;
  inset: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #effcff;
  background: rgba(3, 12, 22, 0.72);
  border-radius: 7px;
  font-size: 13px;
  letter-spacing: 0.03em;
}

.player-tip {
  margin-top: 10px;
}

.player-quality {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 12px 2px 3px;
  flex-wrap: wrap;
}

.player-quality-label {
  color: var(--td-text-color-secondary);
  font-size: 12px;
  margin-right: 2px;
}

.quality-btn {
  min-width: 48px;
  min-height: 28px;
  padding: 3px 10px;
  border: 1px solid var(--video-border);
  border-radius: 7px;
  color: var(--td-text-color-secondary);
  background: var(--video-surface-soft);
  font-size: 12px;
  cursor: pointer;
  transition:
    transform var(--video-dur-fast) var(--video-ease-spring),
    background-color var(--video-dur-fast) var(--video-ease),
    border-color var(--video-dur-fast) var(--video-ease),
    color var(--video-dur-fast) var(--video-ease);
  will-change: transform;
}

.quality-btn:hover:not(:disabled) {
  color: var(--td-text-color-primary);
  border-color: var(--video-border-strong);
  background-color: var(--video-surface-raised);
  transform: translateY(-1px);
}

.quality-btn.active {
  color: #06131f;
  border-color: transparent;
  background: var(--app-cyan);
  font-weight: 700;
}

.quality-btn:disabled {
  cursor: wait;
  opacity: 0.55;
}

/* 入场动画：淡入 + 轻微上浮。
   使用 backwards（而非 both）填充模式，动画结束后不保留 transform，
   否则会覆盖 .course-card:hover 的位移。 */
@keyframes video-rise {
  from {
    opacity: 0;
    transform: translate3d(0, 14px, 0);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

/* 页面内交互元素统一圆润过渡 + 轻微上浮反馈 */
.video-manage :deep(.t-button) {
  transition:
    transform var(--video-dur-fast) var(--video-ease-spring),
    background-color var(--video-dur-fast) var(--video-ease),
    border-color var(--video-dur-fast) var(--video-ease),
    color var(--video-dur-fast) var(--video-ease);
}

.video-manage :deep(.t-button:not(.t-is-disabled):hover) {
  transform: translateY(-1px);
}

.video-manage :deep(.t-tag) {
  transition:
    background-color var(--video-dur-fast) var(--video-ease),
    border-color var(--video-dur-fast) var(--video-ease);
}

.chapter-panel {
  border: 1px solid var(--video-border);
  border-radius: var(--app-radius);
  background: linear-gradient(180deg, var(--video-surface-raised) 0%, var(--video-surface) 100%);
  box-shadow: var(--video-shadow), inset 0 1px 0 var(--video-highlight);
  animation: video-rise var(--video-dur) var(--video-ease) backwards;
  animation-delay: 0.14s;
  padding: 16px 12px;
  max-height: 680px;
  overflow-y: auto;
  min-width: 0;
}

.chapter-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 3px 7px 14px;
  border-bottom: 1px solid var(--video-border);
  margin-bottom: 14px;
}

.chapter-tree {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chapter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--video-border);
  border-radius: 10px;
  cursor: pointer;
  background-color: var(--video-surface-soft);
  transition:
    transform var(--video-dur) var(--video-ease),
    background-color var(--video-dur-fast) var(--video-ease),
    border-color var(--video-dur-fast) var(--video-ease);
  will-change: transform;
}

.chapter-item:hover {
  border-color: var(--video-border-strong);
  background-color: rgba(100, 229, 224, 0.08);
  transform: translate3d(2px, 0, 0);
}

.chapter-item.active {
  border-color: var(--app-cyan);
  background-color: rgba(100, 229, 224, 0.1);
  background-image: linear-gradient(to right, var(--app-cyan) 3px, transparent 3px);
  background-size: 3px 100%;
  background-repeat: no-repeat;
  box-shadow: 0 0 18px rgba(100, 229, 224, 0.12);
}

.chapter-item .chapter-toggle {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  color: var(--td-text-color-secondary);
  flex-shrink: 0;
}

.chapter-item .chapter-icon {
  color: var(--app-cyan);
  flex-shrink: 0;
}

.chapter-item .chapter-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--td-text-color-primary);
  font-weight: 650;
}

.chapter-item .chapter-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--video-dur-fast) var(--video-ease);
}

.chapter-item:hover .chapter-actions {
  opacity: 1;
}

.chapter-item .chapter-actions :deep(.t-button) {
  padding: 0 4px;
  min-height: 22px;
  font-size: 12px;
}

.chapter-children {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 16px;
  border-left: 1px solid var(--video-border);
  margin-left: 12px;
}

/* 无障碍：系统开启"减少动态效果"时关闭入场/交互动画 */
@media (prefers-reduced-motion: reduce) {
  .video-manage,
  .video-manage *,
  .video-manage *::before,
  .video-manage *::after {
    animation-duration: 0.01ms !important;
    animation-delay: 0ms !important;
    transition-duration: 0.01ms !important;
  }
}

@media screen and (max-width: 900px) {
  .library-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px;
  }

  .hero-stats {
    width: 100%;
    justify-content: space-between;
  }

  .main {
    grid-template-columns: 1fr;
  }

  .course-panel {
    max-height: none;
  }
}

@media screen and (max-width: 620px) {
  .video-manage {
    padding-top: 0;
  }

  .op-bar,
  .toolbar-actions,
  .video-head {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions .status-filter {
    width: 100%;
  }

  .video-panel {
    padding: 13px;
  }

  .video-panel :deep(.t-table__content) {
    overflow-x: auto;
  }
}
</style>
