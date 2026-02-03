cmake_minimum_required(VERSION 3.10)  #指定最低cmake版本
project(StudyComputerGraphics) #项目名称

set(CMAKE_CXX_STANDARD 17) #使用c++17标准
set(CMAKE_CXX_STANDARD_REQUIRED ON) #强制使用c++17标准
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -mconsole")

# Add source files # 自动查找 src 目录下的所有 .cpp 和 .c 文件作为源代码
file(GLOB_RECURSE SOURCES "src/*.cpp" "src/*.c")

# Create executable  创建可执行文件，名称与项目名相同
add_executable(${PROJECT_NAME} ${SOURCES})

# Include directories 设置包含目录（头文件路径）
target_include_directories(${PROJECT_NAME} PRIVATE ${PROJECT_SOURCE_DIR}/src)

# Find and link libraries (uncomment and modify as needed)
find_package(OpenCV QUIET)
if(OpenCV_FOUND)
    target_link_libraries(${PROJECT_NAME} ${OpenCV_LIBS})
    message(STATUS "OpenCV found: ${OpenCV_VERSION}")
else()
    message(STATUS "OpenCV not found, continuing without it")
endif()

# SDL2 configuration
set(SDL2_INCLUDE_DIR "E:/vspkg/packages/sdl2_x64-mingw-dynamic/include")
set(SDL2_LIBRARY_DIR "E:/vspkg/packages/sdl2_x64-mingw-dynamic/lib")
set(SDL2_LIBRARY "E:/vspkg/packages/sdl2_x64-mingw-dynamic/lib/libSDL2.dll.a")
set(SDL2_MAIN_LIBRARY "E:/vspkg/packages/sdl2_x64-mingw-dynamic/lib/libSDL2main.a")

if(EXISTS ${SDL2_INCLUDE_DIR} AND EXISTS ${SDL2_LIBRARY})
    target_include_directories(${PROJECT_NAME} PRIVATE ${SDL2_INCLUDE_DIR})
    target_link_directories(${PROJECT_NAME} PRIVATE ${SDL2_LIBRARY_DIR})
    target_link_libraries(${PROJECT_NAME} PRIVATE mingw32 ${SDL2_MAIN_LIBRARY} ${SDL2_LIBRARY})
    message(STATUS "SDL2 found and linked successfully")
else()
    message(STATUS "SDL2 not found, continuing without it")
endif()