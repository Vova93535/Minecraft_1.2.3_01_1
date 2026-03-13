@echo off
title Minecraft 1.2.3_01.1 (LWJGL) Launcher
color 0A

echo ===================================================
echo   Minecraft 1.2.3_01.1 (Creepy 3D) - LWJGL
echo   Created by Scripter — DeepSeek, Idea — by vova
echo ===================================================
echo.

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found.
    pause
    exit /b 1
)

javac -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] JDK not found.
    pause
    exit /b 1
)
echo [OK] Java found.

:: Check LWJGL libraries
if not exist lib\lwjgl.jar (
    echo [ERROR] LWJGL libraries missing!
    echo Please download LWJGL 3 from GitHub and copy all JARs to 'lib' folder,
    echo and natives to 'lib\natives'.
    pause
    exit /b 1
)

:: Set classpath
set CLASSPATH=.;lib\lwjgl.jar;lib\lwjgl-opengl.jar;lib\lwjgl-glfw.jar;lib\lwjgl-stb.jar

:: Compile
echo [1/3] Compiling Minecraft123_01_1.java...
javac -cp "%CLASSPATH%" Minecraft123_01_1.java
if %errorlevel% neq 0 (
    pause
    exit /b %errorlevel%
)
echo [OK] Compiled.

:: Run
echo [2/3] Starting game...
java -cp "%CLASSPATH%" -Djava.library.path=lib\natives Minecraft123_01_1
echo [3/3] Game closed.
pause