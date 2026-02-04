#pragma once
#include <SDL2/SDL.h>
#include "../utils/Color.hpp"
#include "../line/Line.hpp"

void drawRect(SDL_Renderer *renderer, int x0, int y0, int w, int h, Color c);
void drawFillRect(SDL_Renderer *renderer, int x0, int y0, int w, int h, Color color);