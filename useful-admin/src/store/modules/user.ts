import { defineStore } from 'pinia';
import { TOKEN_NAME } from '@/config/global';
import { store, usePermissionStore } from '@/store';
import { Message, MessagePlugin } from 'tdesign-vue-next';
import { userAuthApi } from '@/api/user/user';
export const REFRESH_TOKEN_NAME='refreshToken';
export const CLIENT_ID_NAME='CLIENT_ID';
const InitUserInfo = {
  roles: [],
};

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_NAME) || '', // 默认token不走权限
    userInfo: { ...InitUserInfo },
    refreshToken:localStorage.getItem(REFRESH_TOKEN_NAME) || '',
    clientId:localStorage.getItem(CLIENT_ID_NAME) || 'yaohw',
  }),
  getters: {
    roles: (state) => {
      return state.userInfo?.roles;
    },
    getAccessToken:(state)=>  state.token,
    getRefreshToken:(state)=>  state.refreshToken,
    getClientId:(state)=>  state.clientId,
  },
  actions: {
    async login(userInfo: Record<string, unknown>) {
    const { account, password } = userInfo as { account: string; password: string };
    try{
      // 实际的 OAuth2 登录逻辑
        const data = await userAuthApi.login({ account, password });
        if (data.code === 200) {
          this.setToken(data.token,data.refreshToken);
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
    setToken(token:string,refreshToken:string){
      this.token=token;
      this.refreshToken=refreshToken;
      localStorage.setItem(TOKEN_NAME, token);
      localStorage.setItem(REFRESH_TOKEN_NAME, refreshToken);
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
        this.token = '';
        this.refreshToken = '';
        this.clientId='';
        this.userInfo = { ...InitUserInfo };
        localStorage.removeItem(TOKEN_NAME);
        localStorage.removeItem(REFRESH_TOKEN_NAME);
        localStorage.removeItem(CLIENT_ID_NAME);
      }
    },
    async removeToken() {
      this.token = '';
      this.refreshToken = '';
      localStorage.removeItem(TOKEN_NAME);
      localStorage.removeItem(REFRESH_TOKEN_NAME);
    },
  },
});

export function getUserStore() {
  return useUserStore(store);
}
