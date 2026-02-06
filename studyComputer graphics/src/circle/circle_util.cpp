#include "circle_util.hpp"
#include "../line/Line.hpp"
#include <cmath>

void drawCirclePoint(SDL_Renderer *renderer, int x0, int y0, int x, int y,
                     Color c) {
  SDL_RenderDrawPoint(renderer, x0 + x, y0 + y);
  SDL_RenderDrawPoint(renderer, x0 + y, y0 + x);
  SDL_RenderDrawPoint(renderer, x0 - y, y0 + x);
  SDL_RenderDrawPoint(renderer, x0 - x, y0 + y);
  SDL_RenderDrawPoint(renderer, x0 - x, y0 - y);
  SDL_RenderDrawPoint(renderer, x0 - y, y0 - x);
  SDL_RenderDrawPoint(renderer, x0 + y, y0 - x);
  SDL_RenderDrawPoint(renderer, x0 + x, y0 - y);
}

void drawCircle(SDL_Renderer *renderer, int x0, int y0, int r, Color c) {
  SDL_SetRenderDrawColor(renderer, c.r, c.g, c.b, c.a);
  int x = 0, y = r;
  int d = 1 - r; // 初始化判别式
  drawCirclePoint(renderer, x0, y0, x, y, c);
  while (x < y) {
    if (d < 0) {
      d += 2 * x + 3;
    } else {
      d += 2 * (x - y) + 5;
      y--;
    }
    x++;
    drawCirclePoint(renderer, x0, y0, x, y, c);
  }
}

void drawFillCircle(SDL_Renderer *renderer, int x0, int y0, int r, Color c) {
  for (int y = -r; y <= r; y++) {
    int x = static_cast<int>(sqrt(r * r - y * y));
    drawLine(renderer, x0 - x, y0 + y, x0 + x, y0 + y, c);
  }
}