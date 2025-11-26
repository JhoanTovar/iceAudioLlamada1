#!/bin/bash

echo "========================================"
echo "  Iniciando Servicios del Chat (Linux)"
echo "========================================"
echo ""

echo "[1/3] Iniciando Proxy HTTP..."
./proxy-server-linux &
PROXY_PID=$!

sleep 2

echo "[2/3] Iniciando Servidor de Mensajes TCP..."
java -jar chat-server.jar &
MSG_PID=$!

sleep 2

echo "[3/3] Iniciando Servidor de Audio ICE..."
java -jar audio-server.jar &
AUDIO_PID=$!

sleep 2

echo ""
echo "========================================"
echo "  Todos los servicios iniciados!"
echo "========================================"
echo "Servicios:"
echo "- Proxy (PID: $PROXY_PID)"
echo "- Mensajes (PID: $MSG_PID)"
echo "- Audio (PID: $AUDIO_PID)"
echo ""
echo "Abriendo cliente web en http://localhost:8080"
echo ""

cd web
python3 -m http.server 8080

# Al cerrar con Ctrl+C, mata todos los procesos
trap "kill $PROXY_PID $MSG_PID $AUDIO_PID 2>/dev/null" EXIT