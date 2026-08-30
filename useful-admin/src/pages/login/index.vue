<template>
  <div class="login-wrapper">
    <login-header />

    <div class="login-container">
      <div class="login-visual">
        <div class="visual-kicker"><t-icon name="play-circle" /> USEFUL MEDIA CONTROL</div>
        <div class="visual-title">把知识，<br /><em>交给时间。</em></div>
        <p class="visual-copy">一个面向课程内容团队的媒体工作台，上传、转码、分发都在同一条轨道上。</p>
        <div class="signal-line"><span v-for="n in 18" :key="n" :style="{ '--i': n }"></span></div>
        <div class="visual-meta"><span>VIDEO PIPELINE</span><span>ONLINE / 24H</span></div>
      </div>

      <div class="login-form-pane">
        <div class="title-container">
          <div class="title-kicker">WELCOME BACK</div>
          <h1 class="title margin-no">RuoYi-Vue-Plus</h1>
          <h1 class="title">多租户管理系统</h1>
          <div class="sub-title">
            <p class="tip">{{ type == 'register' ? '已有账号?' : '没有账号吗?' }}</p>
            <p class="tip" @click="switchType(type == 'register' ? 'login' : 'register')">
              {{ type == 'register' ? '登录' : '注册新账号' }}
            </p>
          </div>
        </div>

        <login v-if="type === 'login'" />
        <register v-else @register-success="switchType('login')" />
      </div>
      <tdesign-setting />
    </div>

    <footer class="copyright">Copyright @ 2021-2022 Tencent. All Rights Reserved</footer>
  </div>
</template>
<script lang="ts">
export default {
  name: 'LoginIndex',
};
</script>
<script setup lang="ts">
import { ref } from 'vue';

import Login from './components/Login.vue';
import Register from './components/Register.vue';
import LoginHeader from './components/Header.vue';
import TdesignSetting from '@/layouts/setting.vue';

const type = ref('login');
const switchType = (val: string) => {
  type.value = val;
};
</script>

<style lang="less" scoped>
@import url('./index.less');
</style>
