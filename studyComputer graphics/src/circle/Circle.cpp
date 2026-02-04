#include "Circle.hpp"
#include <cmath>

Circle::Circle(int x, int y, int radius) : x0(x), y0(y), r(radius) {}

void Circle::drawCirclePoint(SDL_Renderer *renderer, int x0, int y0, int x, int y, Color c) {
  SDL_RenderDrawPoint(renderer, x0 + x, y0 + y);
  SDL_RenderDrawPoint(renderer, x0 + y, y0 + x);
  SDL_RenderDrawPoint(renderer, x0 + y, y0 - x);
  SDL_RenderDrawPoint(renderer, x0 + x, y0 - y);
  SDL_RenderDrawPoint(renderer, x0 - x, y0 - y);
  SDL_RenderDrawPoint(renderer, x0 - y, y0 - x);
  SDL_RenderDrawPoint(renderer, x0 - y, y0 + x);
  SDL_RenderDrawPoint(renderer, x0 - x, y0 + y);
}

void Circle::drawCircle(SDL_Renderer *renderer, Color c) {
  SDL_SetRenderDrawColor(renderer, c.r, c.g, c.b, c.a);
  int x = 0, y = r;
  int d = 1 - r; // 初始化判别式
  drawCirclePoint(renderer, x0, y0, x, y, c);
  while (x < y) {
    if (d < 0) {
      d += 2 * (x - y) + 5;
      y--;
    }
    x++;
    drawCirclePoint(renderer, x0, y0, x, y, c);
  }
}

void Circle::drawFillCircle(SDL_Renderer *renderer, Color c) {
  for (int y = -r; y <= r; y++) {
    int x = static_cast<int>(sqrt(r * r - y * y));
    drawLine(renderer, x0 - x, y0 + y, x0 + x, y0 + y, c);
  }
}