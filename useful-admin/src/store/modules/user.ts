import { defineStore } from 'pinia';
import { TOKEN_NAME } from '@/config/global';
import { store, usePermissionStore } from '@/store';
import { Message, MessagePlugin } from 'tdesign-vue-next';

const InitUserInfo = {
  roles: [],
};

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_NAME) , // 默认token不走权限
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
        const response = await fetch('/auth/user/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            clientId: 'yaohw',
            grantType: 'password',
            username: account,
            password: password,
          }),
        });
        const data = await response.json();
        if (data.code === 200) {
          this.token = data.token;
          localStorage.setItem(TOKEN_NAME, data.token);
        }else{
          throw new Error(data.msg || '登录失败');
        }
      }catch(error){
         const errMsg=(error as Error).message||"网络异常,登录失败";
         MessagePlugin.error(errMsg);
         throw new Error(errMsg);
        }
    },
    async getUserInfo() {
      try {
        // 实际的 OAuth2 获取用户信息逻辑
        const response = await fetch('/auth/user/getInfo', {
          method: 'GET',
          headers: {
            'Authorization': 'Bearer ' + this.token,
          },
        });

        if (response.ok) {
          const data = await response.json();
          this.userInfo = {
            name: data.user?.username || 'yaohw',
            roles: Array.from(data.roles || ['admin']),
          };
          return;
        }
      } catch (error) {
        console.error('获取用户信息错误:', error);
      }

    },
    async logout() {
      try {
        // 可选：调用 OAuth2 登出端点
        await fetch('/auth/logout', {
          method: 'POST',
          headers: {
            'Authorization': 'Bearer ' + this.token,
          },
        });
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
