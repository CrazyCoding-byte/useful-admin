#include <SDL2/SDL.h>
#include <iostream>

// 窗口常量
const int WINDOW_WIDTH = 800;
const int WINDOW_HEIGHT = 600;

// 包含头文件
#include "utils/Color.hpp"
#include "line/Line.hpp"
#include "rectangle/Rectangle.hpp"
#include "circle/circle_util.hpp"
#include "circle/Circle.hpp"

// SDL2主程序框架
int main(int argc, char *argv[]) {
  // 初始化SDL2视频子系统
  if (SDL_Init(SDL_INIT_VIDEO) < 0) {
    std::cerr << "SDL初始化失败：" << SDL_GetError() << std::endl;
    return -1;
  }
  // 创建窗口
  SDL_Window *window = SDL_CreateWindow(
      "图形学核心：DDA直线算法", SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
      WINDOW_WIDTH, WINDOW_HEIGHT, SDL_WINDOW_SHOWN);
  if (!window) {
    std::cerr << "窗口创建失败：" << SDL_GetError() << std::endl;
    SDL_Quit();
    return -1;
  }

  // 创建渲染器（硬件加速+垂直同步）
  SDL_Renderer *renderer = SDL_CreateRenderer(
      window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
  if (!renderer) {
    std::cerr << "渲染器创建失败：" << SDL_GetError() << std::endl;
    SDL_DestroyWindow(window);
    SDL_Quit();
    return -1;
  }

  // ===================== 绘制直线（核心测试） =====================
  // 定义常用颜色（直接调用，无需重复写RGBA）
  Color red = {255, 0, 0, 255};       // 红色
  Color green = {0, 255, 0, 255};     // 绿色
  Color blue = {0, 0, 255, 255};      // 蓝色
  Color yellow = {255, 255, 0, 255};  // 黄色
  Color white = {255, 255, 255, 255}; // 白色
  Color black = {0, 0, 0, 255};       // 黑色

  // 先清空窗口，填充黑色背景（可选，让直线更明显）
  SDL_SetRenderDrawColor(renderer, black.r, black.g, black.b, black.a);
  SDL_RenderClear(renderer); // SDL2清空渲染器的接口，填充当前绘制颜色

  // // 绘制1：水平直线（绿色）：(100,100) → (700,100)（Δy=0）
  // drawLine(renderer, 100, 100, 700, 100,
  //          green); // 水平线 x轴可以变动但是上下不能变动
  // // 绘制2：垂直直线（红色）：(100,100) → (100,500)（Δx=0）
  // drawLine(renderer, 100, 100, 100, 500,
  //          red); // 垂直线 y轴可以变动但是左右不能变动
  // // 绘制3：正斜率斜线（蓝色）：(100,500) → (700,100)（斜率为负，左上到右下）
  // drawLine(renderer, 100, 500, 700, 100, blue);
  // // 绘制4：反向直线（黄色）：(700,100) → (100,500)（和3反向，效果一致）
  // drawLine(renderer, 700, 100, 100, 500, yellow);
  // // 绘制5：任意斜线（白色）：(400,200) → (600,400)（斜率为正，左下到右上）
  // drawLine(renderer, 400, 200, 600, 400, white);
  // // 绘制1：空心矩形（红色，左上角(100,100)，宽200，高100）
  // drawRect(renderer, 100, 100, 200, 100, red);
  // // 绘制2：实心矩形（绿色，左上角(400,100)，宽200，高100）
  // drawFillRect(renderer, 400, 100, 200, 100, green);

  // 绘制测试
  drawCircle(renderer, 200, 300, 100, red);       // 空心圆（红色，半径100）
  drawFillCircle(renderer, 600, 300, 100, green); // 实心圆（绿色，半径100）
  drawCircle(renderer, 600, 300, 100, blue);      // 实心圆加蓝色边框
  drawFillCircle(renderer, 400, 150, 50, yellow); // 半透明黄色圆

  // 使用Circle类绘制
  Circle circle(400, 450, 80);
  circle.drawFillCircle(renderer, white);
  circle.drawCircle(renderer, black);

  // 刷新缓冲区，将所有绘制的直线显示到屏幕
  SDL_RenderPresent(renderer);

  // ===================== 事件循环（保持窗口，ESC退出） =====================
  SDL_Event e;
  bool isRunning = true;
  while (isRunning) {
    while (SDL_PollEvent(&e)) {
      if (e.type == SDL_QUIT ||
          (e.type == SDL_KEYDOWN && e.key.keysym.sym == SDLK_ESCAPE)) {
        isRunning = false;
      }
    }
  }

  // 释放资源
  SDL_DestroyRenderer(renderer);
  SDL_DestroyWindow(window);
  SDL_Quit();

  return 0;
}