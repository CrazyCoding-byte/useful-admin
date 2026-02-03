# MinGW 工具链配置文件
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

# 设置 MinGW 编译器路径
set(CMAKE_C_COMPILER gcc)
set(CMAKE_CXX_COMPILER g++)
set(CMAKE_RC_COMPILER windres)

# 设置编译选项
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11")

# 设置链接选项
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -mconsole")

# 确保使用 MinGW 生成器
set(CMAKE_GENERATOR "MinGW Makefiles")
set(CMAKE_BUILD_TYPE Debug)

# 设置 vcpkg 工具链文件
set(CMAKE_TOOLCHAIN_FILE "E:/vspkg/scripts/buildsystems/vcpkg.cmake" CACHE FILEPATH "vcpkg toolchain file")
set(VCPKG_TARGET_TRIPLET "x64-mingw-dynamic" CACHE STRING "vcpkg target triplet")

# 跳过平台测试
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 确保 CMake 不会尝试使用 MSVC
set(CMAKE_MAKE_PROGRAM mingw32-make CACHE FILEPATH "mingw32-make")
