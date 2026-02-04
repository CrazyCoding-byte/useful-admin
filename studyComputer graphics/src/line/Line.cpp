#include "Line.hpp"
#include <cmath>

void drawLine(SDL_Renderer *renderer, int x0, int y0, int x1, int y1, Color color) {
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
  for (int i = 0; i <= steps; ++i) {
    // 包含起点和终点，所以i<=steps
    // 四舍五入取整为整数像素坐标，绘制点
    SDL_RenderDrawPoint(renderer,
                        static_cast<int>(round(x)), // round()四舍五入，cmath库
                        static_cast<int>(round(y)));
    // 增量步进，更新当前坐标
    x += xStep;
    y += yStep;
  }
}

void drawLine2(SDL_Renderer *renderer, int x0, int y0, int x1, int y1, Color color) {
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