#include "Rectangle.hpp"

void drawRect(SDL_Renderer *renderer, int x0, int y0, int w, int h, Color c) {
  int x1 = x0 + w - 1;                   // 右边界x坐标(-1适配像素0计数)
  int y1 = y0 + h - 1;                   // 下边界y坐标
  drawLine(renderer, x0, y0, x1, y0, c); // 上边 左上10,10 右上14,10
  drawLine(renderer, x1, y0, x1, y1, c); // 右边  右上14,10右下14,13
  drawLine(renderer, x1, y1, x0, y1, c); // 下边 左下14,13 10,13
  drawLine(renderer, x0, y1, x0, y0, c); // 左边 10,13 10,10
}

void drawFillRect(SDL_Renderer *renderer, int x0, int y0, int w, int h, Color color) {
  int x1 = x0 + w - 1; // 右边界x //x1=14
  int y1 = y0 + h - 1; // 下边界y //y1=13
  // 逐行遍历y轴，每一行画一条水平直线，拼接成实心矩形
  for (int y = y0; y <= y1; ++y) {
    drawLine(renderer, x0, y, x1, y, color);
    // 10 10 14 10
    // 10 11 14 11
    // 10 12 14 12
    // 10 13 14 13
  }
}