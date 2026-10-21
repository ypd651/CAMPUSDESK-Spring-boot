# 🎓 CampusDesk

**Sistema de gestión académica contenerizado con Spring Boot, PostgreSQL, Nginx y Docker Compose.**

CampusDesk es una aplicación de gestión académica diseñada con una arquitectura basada en contenedores, que facilita el despliegue, la configuración y la administración de sus servicios.

## 📋 Tabla de contenidos

- [Tecnologías](#-tecnologías)
- [Arquitectura del proyecto](#-arquitectura-del-proyecto)
- [Requisitos previos](#-requisitos-previos)
- [Configuración de variables de entorno](#️-configuración-de-variables-de-entorno)
- [Generación de JWT_SECRET](#-generación-de-jwt_secret)
- [Despliegue con Docker Compose](#-despliegue-con-docker-compose)
- [Puertos y servicios](#-puertos-y-servicios)
- [Configuración de pgAdmin](#️-configuración-de-pgadmin)
- [Desarrollo local del backend](#-desarrollo-local-del-backend)
- [Comandos útiles](#-comandos-útiles)
- [Seguridad](#-seguridad)
- [Solución de problemas](#-solución-de-problemas)

## 🛠️ Tecnologías

| Tecnología | Descripción |
|---|---|
| Java 21 | Plataforma de ejecución del backend |
| Spring Boot | Framework para desarrollar la API REST |
| Gradle | Compilación y gestión de dependencias |
| PostgreSQL 17 | Base de datos relacional |
| Nginx | Servidor web para el frontend |
| Docker | Contenerización de servicios |
| Docker Compose | Orquestación de contenedores |
| pgAdmin 4 | Administración de PostgreSQL |
| JWT | Autenticación basada en tokens |

## 🏗️ Arquitectura del proyecto

CampusDesk utiliza Docker Compose para coordinar sus servicios:

```text
CampusDesk
└── Docker Compose
    ├── Frontend (Nginx) — puerto 5500
    ├── Backend (Spring Boot) — puerto 8081
    ├── PostgreSQL 17 — puerto interno 5432
    └── pgAdmin 4 — puerto 5050
```

### Componentes principales

- **Frontend:** interfaz web servida por Nginx.
- **Backend:** API REST desarrollada con Spring Boot y Java 21.
- **Base de datos:** PostgreSQL 17.
- **pgAdmin 4:** interfaz gráfica para administrar la base de datos.
- **Docker Compose:** administra contenedores, redes y configuración.

## 📦 Requisitos previos

Instala las siguientes herramientas:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) o Docker Engine.
- Docker Compose V2.
- Git.
- IntelliJ IDEA o Visual Studio Code, si deseas desarrollar localmente.
- JDK 21 si ejecutarás el backend fuera de Docker.

Verifica las instalaciones:

```bash
docker --version
docker compose version
git --version
```

## ⚙️ Configuración de variables de entorno

El proyecto utiliza `.env.example` como plantilla y `.env` para las credenciales de tu entorno.

### 1. Crear el archivo `.env`

Linux, macOS o Git Bash:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

### 2. Configurar las variables

Edita `.env` y define tus propios valores:

| Variable | Descripción |
|---|---|
| `POSTGRES_DB` | Nombre de la base de datos, por ejemplo `campus_db` |
| `POSTGRES_USER` | Usuario de PostgreSQL |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL |
| `PGADMIN_EMAIL` | Correo para iniciar sesión en pgAdmin |
| `PGADMIN_PASSWORD` | Contraseña de pgAdmin |
| `JWT_SECRET` | Clave secreta para firmar tokens JWT |
| `JWT_ISSUER` | Emisor de los tokens, por ejemplo `campusdesk` |
| `JWT_EXPIRATION` | Duración del token, por ejemplo `8h` |
| `ADMIN_FULL_NAME` | Nombre del administrador inicial |
| `ADMIN_EMAIL` | Correo del administrador inicial |
| `ADMIN_PASSWORD` | Contraseña del administrador inicial |
| `CORS_ALLOWED_ORIGINS` | Orígenes autorizados para acceder al backend |

**Importante:** reemplaza los valores de ejemplo por credenciales seguras. La contraseña del administrador debe cumplir los requisitos de validación configurados en el proyecto.

### 3. Relación entre `.env` y Docker Compose

- **PostgreSQL:** recibe las variables `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD`.
- **pgAdmin:** utiliza `PGADMIN_EMAIL` y `PGADMIN_PASSWORD`, que deben mapearse a `PGADMIN_DEFAULT_EMAIL` y `PGADMIN_DEFAULT_PASSWORD` en Compose.
- **Backend:** recibe la configuración de base de datos, JWT, administrador inicial y CORS.
- **Frontend:** publica la interfaz web mediante Nginx.

Docker Compose interpola las variables del archivo `.env`, pero Spring Boot no necesariamente carga ese archivo cuando se ejecuta directamente desde un IDE.

## 🔑 Generación de JWT_SECRET

Genera una clave aleatoria, criptográficamente segura y compatible con la configuración JWT del backend.

### Linux, macOS y Git Bash

```bash
openssl rand -base64 48
```

### Windows PowerShell

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Copia el resultado en `JWT_SECRET` dentro de `.env`.

**Recomendaciones:**

- No reutilices la clave entre entornos.
- No publiques `.env` ni la clave secreta en GitHub.
- No compartas credenciales en capturas o registros públicos.
- Cambiar la clave puede invalidar los tokens existentes.

## 🚀 Despliegue con Docker Compose

Ejecuta los siguientes comandos desde la raíz del proyecto.

### 1. Construir e iniciar los servicios

```bash
docker compose up -d --build
```

### 2. Verificar los contenedores

```bash
docker compose ps
```

### 3. Consultar los registros

```bash
docker compose logs -f
```

Para consultar solamente el backend:

```bash
docker compose logs -f backend
```

Pulsa `Ctrl + C` para salir de los registros sin detener los contenedores.

### 4. Acceder a los servicios

- Frontend: [http://localhost:5500](http://localhost:5500)
- Backend: [http://localhost:8081](http://localhost:8081)
- Swagger UI: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html), si está habilitada en esa ruta.
- pgAdmin: [http://localhost:5050](http://localhost:5050)

Las direcciones dependen de la configuración efectiva del proyecto.

## 🔌 Puertos y servicios

| Servicio | Puerto local | Función |
|---|---:|---|
| Frontend (Nginx) | `5500` | Interfaz web |
| Backend (Spring Boot) | `8081` | API REST |
| Swagger UI | `8081` | Documentación interactiva de la API |
| pgAdmin 4 | `5050` | Administración de PostgreSQL |
| PostgreSQL | `5433` | Conexiones desde herramientas locales |

### Puertos internos y externos

- PostgreSQL escucha en el puerto interno `5432` dentro de Docker.
- El puerto local `5433` permite conexiones desde el equipo anfitrión.
- Los contenedores de backend y pgAdmin deben usar `postgres:5432` para conectarse a la base de datos dentro de la red Docker.
- Las herramientas instaladas en el equipo local deben utilizar `localhost:5433`.

Estas asignaciones deben coincidir con `docker-compose.yml`.

## 🗄️ Configuración de pgAdmin

### 1. Iniciar sesión

Abre [http://localhost:5050](http://localhost:5050) e inicia sesión con `PGADMIN_EMAIL` y `PGADMIN_PASSWORD`.

### 2. Registrar un servidor

1. Selecciona **Add New Server**.
2. En **General**, asigna un nombre, por ejemplo `CampusDesk BD`.
3. En **Connection**, completa los datos:

| Campo | Valor |
|---|---|
| Host name/address | `postgres` |
| Port | `5432` |
| Maintenance database | El valor de `POSTGRES_DB` |
| Username | El valor de `POSTGRES_USER` |
| Password | El valor de `POSTGRES_PASSWORD` |

4. Guarda y comprueba la conexión.

**Importante:** el host `postgres` funciona dentro de la red Docker. Para DBeaver, IntelliJ IDEA o DataGrip instalados en tu equipo, utiliza `localhost`, puerto `5433` y las credenciales de `.env`.

## 💻 Desarrollo local del backend

Puedes ejecutar PostgreSQL en Docker y el backend directamente desde tu IDE.

### 1. Iniciar PostgreSQL

```bash
docker compose up -d postgres
```

### 2. Ejecutar Spring Boot con Gradle

Linux, macOS o Git Bash:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### 3. Configurar la conexión local

La URL de conexión debe apuntar al puerto publicado del equipo:

```text
jdbc:postgresql://localhost:5433/campus_db
```

Sustituye `campus_db` si `POSTGRES_DB` tiene otro valor. Configura también el usuario, la contraseña, JWT y CORS necesarios.

**Nota:** Spring Boot no carga automáticamente `.env` en todas las configuraciones. Si el proyecto no incorpora un mecanismo para leerlo, define las variables en el entorno de ejecución del IDE o de Gradle.

## 🧰 Comandos útiles

| Acción | Comando |
|---|---|
| Construir e iniciar todos los servicios | `docker compose up -d --build` |
| Iniciar todos los servicios | `docker compose up -d` |
| Iniciar solo PostgreSQL | `docker compose up -d postgres` |
| Ver estado de los servicios | `docker compose ps` |
| Ver todos los registros | `docker compose logs -f` |
| Ver registros del backend | `docker compose logs -f backend` |
| Detener los servicios | `docker compose stop` |
| Detener y eliminar contenedores | `docker compose down` |
| Reconstruir imágenes | `docker compose build` |

### Reinicio completo

```bash
docker compose down
docker compose up -d --build
```

Este procedimiento no elimina por sí solo los volúmenes nombrados. Para eliminarlos también:

```bash
docker compose down -v
```

**Advertencia:** `docker compose down -v` puede borrar los datos persistidos de PostgreSQL. Utilízalo solo si realmente deseas eliminarlos.

## 🔒 Seguridad

- No subas `.env` al repositorio; inclúyelo en `.gitignore`.
- Mantén `.env.example` libre de credenciales reales.
- Utiliza contraseñas robustas y diferentes para cada servicio.
- Genera una clave `JWT_SECRET` aleatoria para cada entorno.
- Limita `CORS_ALLOWED_ORIGINS` a los orígenes necesarios.
- Evita exponer PostgreSQL a redes externas sin necesidad.
- En producción, utiliza HTTPS y una gestión segura de secretos.
- Protege Swagger UI si la aplicación se publica fuera del entorno de desarrollo.
- Realiza copias de seguridad periódicas de la base de datos.

## 🐛 Solución de problemas

### El backend no puede conectarse a PostgreSQL

Comprueba el estado y los registros:

```bash
docker compose ps
docker compose logs postgres
```

Verifica el host según dónde se ejecute el backend:

- Backend dentro de Docker: `postgres:5432`.
- Backend ejecutado localmente: `localhost:5433`.

Confirma también las credenciales y el nombre de la base de datos.

### No puedo acceder a pgAdmin

1. Ejecuta `docker compose ps`.
2. Revisa `docker compose logs pgadmin`.
3. Comprueba que el puerto `5050` esté libre.
4. Verifica las credenciales configuradas.

### Los cambios del backend no aparecen

Si el backend se ejecuta en Docker:

```bash
docker compose up -d --build backend
```

Si se ejecuta con Gradle, detén la ejecución anterior y vuelve a iniciarlo:

```bash
./gradlew bootRun
```

En Windows PowerShell puedes usar `.\gradlew.bat bootRun`.

### Un puerto ya está en uso

Identifica la aplicación que utiliza el puerto y modifica la asignación correspondiente en `docker-compose.yml` si es necesario.

## 📌 Notas finales

- Ejecuta los comandos desde la raíz del proyecto.
- Configura `.env` antes de iniciar los servicios.
- Utiliza los puertos internos para la comunicación entre contenedores y los puertos publicados para conexiones desde el equipo local.
- Conserva los volúmenes de PostgreSQL si necesitas mantener los datos entre reinicios.
- Verifica las rutas, variables y nombres de servicios frente a la configuración real del proyecto.

---

**CampusDesk** — Sistema de gestión académica con una infraestructura contenerizada, modular y preparada para facilitar el desarrollo y el despliegue.
