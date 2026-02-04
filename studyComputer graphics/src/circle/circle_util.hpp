#pragma once
#include <SDL2/SDL.h>
#include "../utils/Color.hpp"

void drawCirclePoint(SDL_Renderer *renderer, int x0, int y0, int x, int y, Color c);
void drawCircle(SDL_Renderer *renderer, int x0, int y0, int r, Color c);
void drawFillCircle(SDL_Renderer *renderer, int x0, int y0, int r, Color c);