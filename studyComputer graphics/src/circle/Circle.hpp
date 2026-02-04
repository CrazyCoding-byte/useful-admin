#pragma once
#include <SDL2/SDL.h>
#include "../utils/Color.hpp"
#include "../line/Line.hpp"

class Circle {
private:
  int x0, y0, r;
  void drawCirclePoint(SDL_Renderer *renderer, int x0, int y0, int x, int y, Color c);

public:
  Circle(int x, int y, int radius);
  void drawCircle(SDL_Renderer *renderer, Color c);
  void drawFillCircle(SDL_Renderer *renderer, Color c);
};