#  Computación en Internet 1  
##  Integrantes  
- **Jhoan Manuel Tovar Rendón** – A0408185  
- **Juan Pablo Martínez Rosero** – A00407215  
 - **Juan Felipe Sinisterra** – A00159604  

---

# Proyecto de Comunicación Cliente–Servidor con TCP, Proxy y ICE

Este proyecto implementa un sistema de mensajería y llamadas de audio utilizando tres servidores diferentes (TCP, Proxy HTTP–TCP e ICE RPC) y un cliente web construido en HTML y JavaScript.

---

## Estructura del Repositorio
- **Cliente Web** (HTML + JavaScript)
- **Servidor TCP** (Java – maneja mensajes y base de datos)
- **Servidor ICE** (Java – maneja llamadas individuales, grupales y mensajes de audio)
- **Proxy HTTP → TCP** (Node.js – puente entre el cliente y el servidor TCP)
- **Carpeta `deploy/`** para ejecutar el sistema empaquetado en Windows y Linux

---

# Instalación Inicial (primera vez)

Antes de iniciar cualquier servidor debes instalar los módulos de Node.js:

```bash
1. cd proxy
2. npm i
3. cd ..
4. cd client
5. npm i
````

---

# Cómo ejecutar el proyecto (modo desarrollo)

### 1️. Iniciar el servidor Proxy

```bash
cd proxy
npm start
```

El Proxy escucha en **puerto 3001**.

---

### 2️. Iniciar el servidor TCP

En la raíz del proyecto:

```bash
.\gradlew :server:runServer
```

El servidor TCP abre en:

```
localhost:5000
```

---

### 3. Iniciar el servidor ICE (llamadas y audio)

```bash
.\gradlew :audio-call:runAudioServer
```

El servidor ICE abre en:

```
ws://0.0.0.0:9099
```

---

### 4️. Iniciar el cliente web

Puedes abrir `index.html` con:

* Live Server (VSCode)
* `npx http-server`
* Cualquier web server local

---

# Flujo de Comunicación del Sistema

El proyecto utiliza un flujo distribuido entre varios componentes cliente–servidor:

---

## 1. Comunicación de Mensajes (TCP)

* El cliente envía **peticiones HTTP** al **Proxy**.
* El Proxy convierte las peticiones **HTTP → TCP**.
* El Proxy reenvía las peticiones al **Servidor TCP**, encargado de:

  * procesar mensajes,
  * almacenar en base de datos,
  * despachar mensajes a usuarios correspondientes.

---

## 2. Comunicación de Audio y Llamadas (ICE)

El sistema emplea **ZeroC ICE** mediante WebSockets para realizar llamadas RPC entre JavaScript y Java.

Permite:

* llamadas individuales,
* llamadas grupales,
* mensajes de audio individuales,
* mensajes de audio grupales.

El cliente:

1. Usa módulos JS generados por Slice.
2. Se conecta al servidor ICE vía WebSocket.
3. Ejecuta métodos remotos compartidos Java ↔ JavaScript.

---

## Servidores y Puertos

| Componente       | Puerto            | Descripción                              |
| ---------------- | ----------------- | ---------------------------------------- |
| Proxy HTTP → TCP | **3001**          | Convierte peticiones HTTP a mensajes TCP |
| Servidor TCP     | **5000**          | Manejo de mensajes y base de datos       |
| Servidor ICE     | **9099**          | Llamadas, grupos, audio                  |
| Cliente Web      | **8080 (deploy)** | Interfaz web                             |

---

# Cómo ejecutar el **deploy** (versión empaquetada)

## Windows

1. Abrir una terminal dentro de la carpeta `deploy/`
2. Ejecutar:

```bash
start-all-win.bat
```

(o doble clic en el archivo)

Todos los servidores se iniciarán automáticamente.

---

## Linux

### 1. Dar permisos de ejecución

```bash
cd deploy
chmod +x proxy-server
chmod +x start-all-linux.sh
```

### 2️. Ejecutar el sistema

```bash
./start-all-linux.sh
```

---

## Obtener la IP del servidor

```bash
ip addr show | grep "inet "
```

Anota tu **IPv4**.

### Acceso desde otro computador:

```
http://TU_IP:8080
```

---

# Listo para desplegar en Windows o Linux

El sistema queda disponible para navegación web y permite:

* envío de mensajes,
* envío de audios,
* llamadas individuales,
* llamadas grupales.

---

