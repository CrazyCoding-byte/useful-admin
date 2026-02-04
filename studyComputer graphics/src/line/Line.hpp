#pragma once
#include <SDL2/SDL.h>
#include "../utils/Color.hpp"

void drawLine(SDL_Renderer *renderer, int x0, int y0, int x1, int y1, Color color);
void drawLine2(SDL_Renderer *renderer, int x0, int y0, int x1, int y1, Color color);