import { defineStore } from 'pinia';
import { TOKEN_NAME } from '@/config/global';
import { store, usePermissionStore } from '@/store';

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
      
      // 模拟登录功能，当 OAuth2 服务不可用时使用
      if (account === 'yaohw' && password === 'yaohw') {
        const mockToken = 'mock_token_' + Date.now();
        this.token = mockToken;
        localStorage.setItem(TOKEN_NAME, mockToken);
        return {
          code: 200,
          message: '登录成功',
          data: mockToken,
        };
      }
      
      // 实际的 OAuth2 登录逻辑
      try {
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
        
        if (!response.ok) {
          throw new Error('登录失败: ' + response.statusText);
        }
        
        const data = await response.json();
        if (data.code === 200) {
          this.token = data.token;
          localStorage.setItem(TOKEN_NAME, data.token);
        }
        return data;
      } catch (error) {
        console.error('登录错误:', error);
        // 如果 OAuth2 服务不可用，使用模拟登录
        if (account === 'yaohw' && password === 'yaohw') {
          const mockToken = 'mock_token_' + Date.now();
          this.token = mockToken;
          localStorage.setItem(TOKEN_NAME, mockToken);
          return {
            code: 200,
            message: '登录成功（模拟）',
            data: mockToken,
          };
        }
        throw {
          code: 401,
          message: error instanceof Error ? error.message : '登录失败',
        };
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
      
      // 如果 OAuth2 服务不可用，返回模拟的用户信息
      this.userInfo = {
        name: 'yaohw',
        roles: ['admin'],
      };
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
  persist: {
    afterRestore: (ctx) => {
      if (ctx.store.roles && ctx.store.roles.length > 0) {
        const permissionStore = usePermissionStore();
        permissionStore.initRoutes(ctx.store.roles);
      }
    },
  },
});

export function getUserStore() {
  return useUserStore(store);
}
