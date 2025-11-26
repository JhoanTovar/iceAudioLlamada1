@echo off
echo ========================================
echo   Iniciando Servicios del Chat (Windows)
echo ========================================
echo.

echo [1/3] Iniciando Proxy HTTP...
start "Proxy HTTP" proxy-server-win.exe

timeout /t 2 /nobreak > nul

echo [2/3] Iniciando Servidor de Mensajes TCP...
start "Chat Server" java -jar chat-server.jar

timeout /t 2 /nobreak > nul

echo [3/3] Iniciando Servidor de Audio ICE...
start "Audio Server" java -jar audio-server.jar

timeout /t 2 /nobreak > nul

echo.
echo ========================================
echo   Todos los servicios iniciados!
echo ========================================
echo.
echo Abriendo cliente web en http://localhost:8080
echo.

cd web
start http://localhost:8080
python -m http.server 8080

REM Si no tienes Python, usa: npx serve -s . -p 8080