# Setup script for SDL2 with vcpkg in VS Code project
# Run this in PowerShell from the project root

# Assume vcpkg is at E:\vspkg (change if different)
$VCPKG_DIR = "E:\vspkg"

# Bootstrap vcpkg if not done
& "$VCPKG_DIR\vcpkg.exe" --version
if ($LASTEXITCODE -ne 0) {
    & "$VCPKG_DIR\bootstrap-vcpkg.bat"
}

# Install SDL2 for MinGW
& "$VCPKG_DIR\vcpkg.exe" install sdl2:x64-mingw-dynamic

# Configure CMake
cmake -S . -B build -G "MinGW Makefiles" -DCMAKE_TOOLCHAIN_FILE="$VCPKG_DIR/scripts/buildsystems/vcpkg.cmake" -DVCPKG_TARGET_TRIPLET=x64-mingw-dynamic -DSDL2_DIR="$VCPKG_DIR/installed/x64-mingw-dynamic/share/sdl2"

# Build
cmake --build build

# Copy DLL
Copy-Item "$VCPKG_DIR/installed/x64-mingw-dynamic/bin/SDL2.dll" "build/bin/"

Write-Host "Setup complete! Run .\build\bin\StudyComputerGraphics.exe"