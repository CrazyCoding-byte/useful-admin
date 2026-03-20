import { defineStore } from 'pinia';
import { TOKEN_NAME } from '@/config/global';
import { store, usePermissionStore } from '@/store';
import { Message, MessagePlugin } from 'tdesign-vue-next';
import { userAuthApi } from '@/api/user/user';
export const REFRESH_TOKEN_NAME='refreshToken';
export const CLIENT_ID='yaohw';
const InitUserInfo = {
  roles: [],
};

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_NAME) || '', // 默认token不走权限
    userInfo: { ...InitUserInfo },
  }),
  getters: {
    roles: (state) => {
      return state.userInfo?.roles;
    },
  },
  actions: {
    async login(userInfo: Record<string, unknown>) {
    const { account, password } = userInfo as { account: string; password: string };
    try{
      // 实际的 OAuth2 登录逻辑
        const data = await userAuthApi.login({ account, password });
        if (data.code === 200) {
          this.token = data.token;
          localStorage.setItem(TOKEN_NAME, data.token);
          // 登录成功后获取用户信息
          await this.getUserInfo();
          // 登录成功，返回成功信息
          return Promise.resolve(data);
        }else{
          const errmsg=data.msg||'登录失败'
          MessagePlugin.error(errmsg);
          return Promise.reject(new Error(errmsg))
        }
      }catch(error){
         const errMsg=(error as Error).message||"网络异常,登录失败";
         MessagePlugin.error(errMsg);
         return Promise.reject(new Error(errMsg));
        }
    },
    async getUserInfo() {
      try {
        // 实际的 OAuth2 获取用户信息逻辑
        const data = await userAuthApi.getUserInfo(this.token);
        console.log('获取用户信息数据:', data);
        this.userInfo = {
          name: data.user?.username || 'yaohw',
          roles: Array.from(data.roles || ['admin']),
        };
        console.log('设置用户信息:', this.userInfo);
        return Promise.resolve(this.userInfo);
      } catch (error) {
        console.error('获取用户信息错误:', error);
        return Promise.reject(error);
      }

    },

    async logout() {
      try {
        // 可选：调用 OAuth2 登出端点
        await userAuthApi.logout(this.token);
      } catch (error) {
        console.error('登出错误:', error);
      } finally {
        localStorage.removeItem(TOKEN_NAME);
        this.token = '';
        this.userInfo = { ...InitUserInfo };
      }
    },
    async removeToken() {
      this.token = '';
    },
  },
});

export function getUserStore() {
  return useUserStore(store);
}
