@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Building Virtual Redstone Wire...
gradle build
if %errorlevel% equ 0 (
    echo.
    echo Build successful!
    echo Output: build\libs\VirtualRedstoneWire-1.0.0.jar
) else (
    echo.
    echo Build failed with error code %errorlevel%
)
pause