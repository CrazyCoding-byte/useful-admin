#include <SDL2/SDL.h>
#include <cmath> // 引入数学库，用fabs(绝对值)、round(四舍五入)函数
#include <iostream>

// 窗口常量
const int WINDOW_WIDTH = 800;
const int WINDOW_HEIGHT = 600;

// 封装颜色结构体（工程化，替代单独传RGBA四个参数）
// C++结构体，C语言可直接用（去掉struct后的typedef即可）
typedef struct {
  Uint8 r; // 红 0~255
  Uint8 g; // 绿 0~255
  Uint8 b; // 蓝 0~255
  Uint8 a; // 透明度 0~255
} Color;

// ===================== 核心：DDA直线绘制函数（可复用） =====================
// 参数：渲染器、起点x0y0、终点x1y1、绘制颜色  这个和下面的不一样是因为 斜率没有直接算其实斜率是等于 
/*
dx=8，dy=4，steps=8
代码算：xStep=8/8=1 （单独算，不碰 dy）
代码算：yStep=4/8=0.5（单独算，不碰 dx）
循环：x 每次 + 1，y 每次 + 0.5 → 坐标依次是 (0,0)→(1,0.5)→(2,1)→…→(8,4)✅ 
完美走在直线上，全程不用算 yStep/xStep，也不用算 dy/dx。
而我们做的 0.5/1​=4/8​=0.5，只是事后分析：“你看，你每次 x 走 1、y 走 0.5，这个比例刚好就是斜率 0.5，和视频里的 k 一样”。
 */
void drawLine(SDL_Renderer *renderer, int x0, int y0, int x1, int y1,
              Color color) {
  // 1. 计算x/y总增量（浮点数，避免整数除法丢失精度）
  float dx = static_cast<float>(x1 - x0);
  float dy = static_cast<float>(y1 - y0);

  // 2. 计算总步数：取dx、dy绝对值的最大值，保证覆盖所有像素
  int steps = static_cast<int>(fmax(fabs(dx), fabs(dy)));

  // 3. 计算每步增量：总增量/总步数
  float xStep = dx / static_cast<float>(steps);
  float yStep = dy / static_cast<float>(steps);

  // 4. 初始化当前坐标（浮点数，保存精确值）
  float x = static_cast<float>(x0);
  float y = static_cast<float>(y0);

  // 5. 设置渲染器绘制颜色
  SDL_SetRenderDrawColor(renderer, color.r, color.g, color.b, color.a);

  // 6. 循环步进，绘制所有像素点
  for (int i = 0; i <= steps; ++i) { // 包含起点和终点，所以i<=steps
    // 四舍五入取整为整数像素坐标，绘制点
    SDL_RenderDrawPoint(renderer,
                        static_cast<int>(round(x)), // round()四舍五入，cmath库
                        static_cast<int>(round(y)));
    // 增量步进，更新当前坐标
    x += xStep;
    y += yStep;
  }
}

void drawLine2(SDL_Renderer *renderer, int x0, int y0, int x1, int y1,
               Color color) {
  float dx = static_cast<float>(x1 - x0);
  float dy = static_cast<float>(y1 - y0);
  int k = dy / dx;
  SDL_SetRenderDrawColor(renderer, color.r, color.g, color.b, color.a);
  float y = y0;
  for (int x = x0; x <= x1; ++x) {
    SDL_RenderDrawPoint(renderer, x, static_cast<int>(round(y1)));
    y += k;
  }
}
// 绘制空心矩形:复用DDA直线函数,左上角(x0,y0),宽w,高h,颜色color
/**
左上：(x0, y0)
右上：(x0 + w - 1, y0) //-1 是因为像素从 0 计数，比如 x0=0，w=800，右边界是 799
而非 800 右下：(x0 + w - 1, y0 + h - 1) 左下：(x0, y0 + h - 1)
计算机是越在下面值越大
 */
void drawRect(SDL_Renderer *renderer, int x0, int y0, int w, int h, Color c) {
  int x1 = x0 + w - 1;                   // 右边界x坐标(-1适配像素0计数)
  int y1 = y0 + h - 1;                   // 下边界y坐标
  drawLine(renderer, x0, y0, x1, y0, c); // 上边 左上10,10 右上14,10
  drawLine(renderer, x1, y0, x1, y1, c); // 右边  右上14,10右下14,13
  drawLine(renderer, x1, y1, x0, y1, c); // 下边 左下14,13 10,13
  drawLine(renderer, x0, y1, x0, y0, c); // 左边 10,13 10,10
}
// 绘制实心矩阵
/**
这是本节唯一的新知识点，也是后续像素 3D 游戏绘制墙壁像素条带的核心思想
——实心图形的本质，是对指定像素区域的批量填充。
1. 为什么需要填充算法？
空心矩形只是画边框，实心矩形需要填满边框内部的所有像素，如果手动调用SDL_RenderDrawPoint逐像素写，效率低且麻烦，需要一个通用的填充逻辑。
2. 逐行扫描填充算法（图形学最基础的填充，无复杂数学）
核心思想
对矩形的每一行 y，都画一条水平直线（从左边界 x0 到右边界 x1），遍历所有行（从 y0
到 y1），所有水平直线拼接起来，就是实心矩形。→ 本质：用 “行级的直线” 替代
“逐像素的点”，减少绘制调用，简化逻辑。 算法执行步骤（无脑实现） 设矩形左上角
(x0,y0)、宽 w、高 h，推导右边界 x1=x0+w-1，下边界 y1=y0+h-1： 循环 y
从y0到y1（遍历矩形的每一行）； 对每一个 y，调用drawLine画水平直线 (x0, y) → (x1,
y)； 循环结束，实心矩形绘制完成。
假设x0=10 y0=10 w=5 h=4  宽高定义图形是什么类型 x0y0定义图形的位置
*/
void drawFillRect(SDL_Renderer *renderer, int x0, int y0, int w, int h,
                  Color color) {
  int x1 = x0 + w - 1; // 右边界x //x1=14
  int y1 = y0 + h - 1; // 下边界y //y1=13
  // 逐行遍历y轴，每一行画一条水平直线，拼接成实心矩形
  for (int y = y0; y <= y1; ++y) {
    drawLine(renderer, x0, y, x1, y, color);
    //10 10 14 10
    //10 11 14 11
    //10 12 14 12
    //10 13 14 13
  }
}
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

  // 绘制1：水平直线（绿色）：(100,100) → (700,100)（Δy=0）
  drawLine(renderer, 100, 100, 700, 100, green); //水平线 x轴可以变动但是上下不能变动
  // 绘制2：垂直直线（红色）：(100,100) → (100,500)（Δx=0）
  drawLine(renderer, 100, 100, 100, 500, red); //垂直线 y轴可以变动但是左右不能变动
  // 绘制3：正斜率斜线（蓝色）：(100,500) → (700,100)（斜率为负，左上到右下）
  drawLine(renderer, 100, 500, 700, 100, blue);
  // 绘制4：反向直线（黄色）：(700,100) → (100,500)（和3反向，效果一致）
  drawLine(renderer, 700, 100, 100, 500, yellow);
  // 绘制5：任意斜线（白色）：(400,200) → (600,400)（斜率为正，左下到右上）
  drawLine(renderer, 400, 200, 600, 400, white);
  // 绘制1：空心矩形（红色，左上角(100,100)，宽200，高100）
  drawRect(renderer, 100, 100, 200, 100, red);
  // 绘制2：实心矩形（绿色，左上角(400,100)，宽200，高100）
  drawFillRect(renderer, 400, 100, 200, 100, green);
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