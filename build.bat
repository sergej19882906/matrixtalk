@echo off
REM Скрипт для сборки Matrix Talk Android

echo ========================================
echo Matrix Talk - Build Script
echo ========================================
echo.

REM Проверка наличия JAVA_HOME
if "%JAVA_HOME%"=="" (
    echo [ERROR] JAVA_HOME не установлен. Установите JDK 17.
    exit /b 1
)

echo [INFO] JAVA_HOME: %JAVA_HOME%
echo [INFO] Java version:
java -version
echo.

REM Проверка наличия Android SDK
if "%ANDROID_HOME%"=="" (
    echo [WARNING] ANDROID_HOME не установлен. Убедитесь, что Android SDK установлен.
) else (
    echo [INFO] ANDROID_HOME: %ANDROID_HOME%
)
echo.

REM Синхронизация Gradle
echo [STEP 1] Синхронизация проекта...
call gradlew.bat --version
echo.

REM Очистка
echo [STEP 2] Очистка...
call gradlew.bat clean
echo.

REM Сборка Debug версии
echo [STEP 3] Сборка debug версии...
call gradlew.bat assembleDebug
echo.

REM Сборка Release версии
echo [STEP 4] Сборка release версии...
call gradlew.bat assembleRelease
echo.

REM Проверка результата
if %ERRORLEVEL% EQU 0 (
    echo ========================================
    echo [SUCCESS] Сборка завершена успешно!
    echo APK файл: app\build\outputs\apk\debug\app-debug.apk
    echo Release APK: app\build\outputs\apk\release\app-release-unsigned.apk
    echo ========================================
) else (
    echo ========================================
    echo [ERROR] Ошибка сборки!
    echo ========================================
    exit /b 1
)
