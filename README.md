# 🎓 CampusDesk

**Sistema de gestión académica contenerizado con Spring Boot, PostgreSQL, Nginx y Docker Compose.**

CampusDesk es una aplicación de gestión académica diseñada con una arquitectura basada en contenedores, que facilita el despliegue, la configuración y la administración de sus servicios.

## 📋 Tabla de contenidos

- Tecnologías
- Arquitectura del proyecto
- Requisitos previos
- Configuración de variables de entorno
- Generación de JWT_SECRET
- Despliegue con Docker Compose
- Puertos y servicios
- Configuración de pgAdmin
- Desarrollo local del backend
- Comandos útiles
- Seguridad

## 🛠️ Tecnologías

| Tecnología     | Descripción                                          |
| -------------- | ---------------------------------------------------- |
| Java 21        | Plataforma de ejecución del backend                  |
| Spring Boot    | Framework para el desarrollo de la API REST          |
| Gradle         | Herramienta de compilación y gestión de dependencias |
| PostgreSQL 17  | Sistema de gestión de bases de datos relacional      |
| Nginx          | Servidor web para el frontend                        |
| Docker         | Plataforma de contenerización                        |
| Docker Compose | Orquestación de los servicios                        |
| pgAdmin 4      | Herramienta web para administrar PostgreSQL          |
| JWT            | Mecanismo de autenticación basado en tokens          |

## 🏗️ Arquitectura del proyecto

CampusDesk utiliza Docker Compose para coordinar los servicios necesarios para su funcionamiento.

```
                     CAMPUSDESK
                          |
                   Docker Compose
                          |
        +-----------------+------------------+
        |                 |                  |
   +----------+      +----------+       +----------+
   | Frontend |      | Backend  |       | pgAdmin  |
   |  Nginx   |      | Spring   |       |    4     |
   |          |      | Boot     |       |          |
   +----------+      +----------+       +----------+
        |                 |                  |
     Puerto            Puerto               Puerto
      5500              8081                 5050
                          |
                    +-----------+
                    | PostgreSQL|
                    |    17     |
                    +-----------+
                         5432
                    Puerto interno

```

### Componentes principales

- **Frontend:** interfaz web servida por Nginx y disponible en el puerto `5500`.
- **Backend:** API REST desarrollada con Spring Boot y Java 21, disponible en el puerto `8081`.
- **Base de datos:** PostgreSQL 17, que almacena la información de la aplicación.
- **pgAdmin 4:** interfaz gráfica para administrar y consultar la base de datos.
- **Docker Compose:** administra la configuración, las redes y el ciclo de vida de los contenedores.

## 📦 Requisitos previos

Antes de iniciar el proyecto, asegúrate de tener instalados:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) o Docker Engine.
- Docker Compose V2, disponible mediante el comando `docker compose`.
- Git, para clonar y administrar el repositorio.
- Un IDE como IntelliJ IDEA o Visual Studio Code, si deseas desarrollar localmente.

Para verificar la instalación, ejecuta:

```
docker --version
docker compose version
git --version

```

Si vas a ejecutar el backend fuera de Docker, también necesitarás un JDK compatible con Java 21.

## ⚙️ Configuración de variables de entorno

CampusDesk utiliza tres elementos fundamentales para gestionar la configuración de los servicios:

- `.env.example`: plantilla de referencia con las variables necesarias.
- `.env`: archivo con los valores específicos de tu entorno local o de despliegue.
- `docker-compose.yml`: archivo que utiliza las variables de entorno para configurar los contenedores.

### 1. Crear el archivo `.env`

En la raíz del proyecto, copia la plantilla:

```
cp .env.example .env

```

En Windows PowerShell, también puedes ejecutar:

```
Copy-Item .env.example .env

```

### 2. Configurar las variables

Abre el archivo `.env` y completa los valores según la siguiente tabla.

| Variable               | Valor de ejemplo                              | Descripción                                                |
| ---------------------- | --------------------------------------------- | ---------------------------------------------------------- |
| `POSTGRES_DB`          | `campus_db`                                   | Nombre de la base de datos.                                |
| `POSTGRES_USER`        | `TU_USUARIO_DB`                               | Usuario de PostgreSQL.                                     |
| `POSTGRES_PASSWORD`    | `TU_CONTRASEÑA_DB`                            | Contraseña del usuario de la base de datos.                |
| `PGADMIN_EMAIL`        | `admin@example.com`                           | Correo para acceder a pgAdmin.                             |
| `PGADMIN_PASSWORD`     | `TU_CONTRASEÑA_PGADMIN`                       | Contraseña de acceso a pgAdmin.                            |
| `JWT_SECRET`           | `TU_CLAVE_SECRET_JWT`                         | Clave secreta para firmar tokens JWT.                      |
| `JWT_ISSUER`           | `campusdesk`                                  | Emisor de los tokens JWT.                                  |
| `JWT_EXPIRATION`       | `8h`                                          | Duración de los tokens de sesión.                          |
| `ADMIN_FULL_NAME`      | `System Administrator`                        | Nombre completo del administrador inicial.                 |
| `ADMIN_EMAIL`          | `admin@example.com`                           | Correo del administrador inicial.                          |
| `ADMIN_PASSWORD`       | `TU_CONTRASEÑA_ADMIN`                         | Contraseña del administrador inicial.                      |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5500,http://127.0.0.1:5500` | Orígenes autorizados para realizar solicitudes al backend. |

**Importante:** los valores de ejemplo deben sustituirse por credenciales propias. Utiliza contraseñas seguras y una clave JWT aleatoria.

La contraseña inicial del administrador debe tener, como mínimo, ocho caracteres e incluir una letra mayúscula, una minúscula, un número y un símbolo, de acuerdo con los requisitos indicados para el proyecto.

### 3. Relación entre `.env` y `docker-compose.yml`

Docker Compose lee las variables definidas en `.env` y las utiliza para configurar los servicios.

- **postgres:** recibe `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD`.
- **pgAdmin:** utiliza `PGADMIN_EMAIL` y `PGADMIN_PASSWORD`, que deben mapearse en Compose a las variables esperadas por la imagen oficial: `PGADMIN_DEFAULT_EMAIL` y `PGADMIN_DEFAULT_PASSWORD`.
- **backend:** recibe la configuración de conexión a la base de datos, la clave JWT, las credenciales del administrador inicial y los orígenes CORS permitidos.
- **frontend:** expone la interfaz web a través de Nginx.

Por lo general, no necesitas modificar `docker-compose.yml` para configurar las credenciales. Solo tendrás que modificarlo si deseas cambiar los puertos publicados, las redes, los volúmenes u otros parámetros de infraestructura.

**Nota:** Docker Compose interpola las variables de `.env` en el archivo YAML, pero esto no garantiza que una aplicación ejecutada directamente desde un IDE cargue ese archivo. La carga de `.env` en el backend local depende de la configuración del proyecto.

## 🔑 Generación de `JWT_SECRET`

La variable `JWT_SECRET` debe contener una clave aleatoria, criptográficamente segura y de al menos 32 caracteres, compatible con el mecanismo de firma JWT utilizado por el backend.

### Linux, macOS y Git Bash

Se recomienda OpenSSL:

```
openssl rand -base64 48

```

Alternativa para Linux y macOS:

```
tr -dc 'A-Za-z0-9!@#$%^&*()' < /dev/urandom | head -c 64
echo

```

### Windows PowerShell

```
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)

```

Copia el resultado y asígnalo a `JWT_SECRET` dentro del archivo `.env`.

**Recomendaciones de seguridad:**

- No reutilices la clave en distintos entornos.
- No publiques la clave en el repositorio.
- No la compartas mediante capturas de pantalla o registros públicos.
- Si cambias la clave en un entorno en funcionamiento, ten en cuenta que los tokens firmados con la clave anterior podrían dejar de ser válidos.

## 🚀 Despliegue con Docker Compose

Una vez configurado el archivo `.env`, puedes iniciar la infraestructura completa desde la raíz del proyecto.

### 1. Construir e iniciar los servicios

```
docker compose up -d --build

```

Este comando construye las imágenes que lo requieran e inicia los servicios en segundo plano.

### 2. Verificar el estado de los contenedores

```
docker compose ps

```

Comprueba que los servicios estén en ejecución y que sus puertos estén publicados correctamente.

### 3. Consultar los registros

```
docker compose logs -f

```

Para revisar únicamente los registros del backend:

```
docker compose logs -f backend

```

Para salir de la visualización de registros, utiliza `Ctrl + C`. Esto no detiene los contenedores.

### 4. Acceder a la aplicación

Cuando los servicios estén listos, abre:

- Frontend: http\://localhost:5500
- Backend API: http\://localhost:8081
- Swagger UI: http\://localhost:8081/swagger-ui.html
- pgAdmin 4: http\://localhost:5050

La disponibilidad de cada dirección depende de que el servicio correspondiente haya iniciado correctamente y de que las rutas configuradas existan.

## 🔌 Puertos y servicios

| Servicio              | Puerto local | Dirección                                       | Función                                                      |
| --------------------- | ------------ | ----------------------------------------------- | ------------------------------------------------------------ |
| Frontend (Nginx)      | `5500`       | [http://localhost:5500](http://localhost:5500/) | Interfaz web de CampusDesk.                                  |
| Backend (Spring Boot) | `8081`       | http\://localhost:8081                          | API REST.                                                    |
| Swagger UI            | `8081`       | http\://localhost:8081/swagger-ui.html          | Documentación interactiva de la API, si está habilitada en esa ruta. |
| pgAdmin 4             | `5050`       | [http://localhost:5050](http://localhost:5050/) | Administración de PostgreSQL.                                |
| PostgreSQL            | `5433`       | `localhost:5433`                                | Acceso desde herramientas externas.                          |

### Puertos internos y externos

Es importante distinguir entre los puertos del equipo local y los puertos internos de la red Docker.

- PostgreSQL utiliza el puerto `5432` dentro de la red de contenedores.
- El puerto `5433` del equipo local se publica para conectarse desde herramientas externas.
- El backend y pgAdmin deben utilizar el nombre del servicio Docker `postgres` y el puerto interno `5432` cuando se conectan a la base de datos desde la misma red Docker.
- Las conexiones realizadas desde el equipo local utilizan `localhost:5433`.

Los puertos publicados pueden variar si se modifican las asignaciones de `docker-compose.yml`.

## 🗄️ Configuración de pgAdmin

pgAdmin permite administrar la base de datos PostgreSQL desde una interfaz web.

### 1. Iniciar sesión

Abre:

[http://localhost:5050](http://localhost:5050/)

Introduce el correo y la contraseña definidos en las variables `PGADMIN_EMAIL` y `PGADMIN_PASSWORD` del archivo `.env`.

### 2. Registrar un servidor

1. Haz clic en **Add New Server**.
2. En la pestaña **General**, introduce un nombre descriptivo, por ejemplo, `CampusDesk BD`.
3. Abre la pestaña **Connection**.
4. Completa los datos de conexión:

| Campo                | Valor                                           |
| -------------------- | ----------------------------------------------- |
| Host name/address    | `postgres`                                      |
| Port                 | `5432`                                          |
| Maintenance database | Valor de `POSTGRES_DB`, por ejemplo `campus_db` |
| Username             | Valor de `POSTGRES_USER`                        |
| Password             | Valor de `POSTGRES_PASSWORD`                    |

5. Guarda la configuración y comprueba que la conexión se establezca correctamente.

**Importante:** el host `postgres` funciona porque pgAdmin y PostgreSQL se comunican a través de la red Docker. No utilices `localhost` como host en esta configuración, ya que dentro del contenedor de pgAdmin ese nombre apunta al propio contenedor de pgAdmin.

Si te conectas desde DBeaver, IntelliJ IDEA o DataGrip instalados en tu equipo, utiliza:

- Host: `localhost`
- Puerto: `5433`
- Base de datos: `campus_db` o el valor configurado en `POSTGRES_DB`.
- Usuario y contraseña: los definidos en `.env`.

## 💻 Desarrollo local del backend

Si prefieres desarrollar y ejecutar el backend desde IntelliJ IDEA, Visual Studio Code u otro IDE, puedes iniciar PostgreSQL mediante Docker y ejecutar Spring Boot directamente en tu equipo.

### 1. Iniciar únicamente PostgreSQL

Desde la raíz del proyecto:

```
docker compose up -d postgres

```

Esto inicia el servicio de base de datos sin levantar necesariamente el frontend, pgAdmin ni el backend contenerizado.

### 2. Ejecutar el backend con Gradle

En Linux, macOS o Git Bash:

```
./gradlew bootRun

```

En Windows PowerShell:

```
.\gradlew.bat bootRun

```

### 3. Configurar la conexión local

Cuando Spring Boot se ejecuta fuera de Docker, la conexión a PostgreSQL debe apuntar al puerto publicado del equipo local.

La URL de conexión debe tener un formato similar a:

```
jdbc:postgresql://localhost:5433/campus_db

```

El nombre de la base de datos debe coincidir con `POSTGRES_DB`.

Asegúrate de que el backend utilice el usuario y la contraseña definidos en `.env`, así como los valores necesarios para la configuración JWT y CORS.

**Nota:** el archivo `.env` no se carga automáticamente en cualquier aplicación Spring Boot. Si el proyecto utiliza una librería o configuración específica para leerlo, verifica que esté habilitada al ejecutar `bootRun`. De lo contrario, define las variables mediante el entorno de ejecución de tu IDE o mediante la configuración de Gradle.

## 🧰 Comandos útiles

### Gestión de contenedores

| Acción                                  | Comando                          |
| --------------------------------------- | -------------------------------- |
| Construir e iniciar todos los servicios | `docker compose up -d --build`   |
| Iniciar todos los servicios             | `docker compose up -d`           |
| Iniciar solo PostgreSQL                 | `docker compose up -d postgres`  |
| Ver el estado de los servicios          | `docker compose ps`              |
| Ver todos los registros                 | `docker compose logs -f`         |
| Ver registros del backend               | `docker compose logs -f backend` |
| Detener los servicios                   | `docker compose stop`            |
| Detener y eliminar los contenedores     | `docker compose down`            |
| Reconstruir las imágenes                | `docker compose build`           |

### Reinicio completo de los servicios

Para detener y volver a construir la infraestructura:

```
docker compose down
docker compose up -d --build

```

Este procedimiento no elimina por sí solo los volúmenes nombrados. Para borrar también los volúmenes administrados por Compose, existe `docker compose down -v`, pero **utilízalo únicamente si deseas eliminar los datos persistidos**, ya que puede borrar la información almacenada en PostgreSQL.

## 🔒 Seguridad

Para mantener una configuración segura de CampusDesk, sigue estas recomendaciones:

- **No subas `.env` al repositorio.** Asegúrate de que esté incluido en `.gitignore`.
- Mantén `.env.example` sin contraseñas reales ni claves secretas.
- Utiliza contraseñas robustas y diferentes para PostgreSQL, pgAdmin y el administrador de la aplicación.
- Genera una clave `JWT_SECRET` aleatoria para cada entorno.
- Limita `CORS_ALLOWED_ORIGINS` a los orígenes que realmente necesiten acceder al backend.
- Evita publicar PostgreSQL directamente en redes externas cuando no sea necesario.
- En entornos de producción, utiliza HTTPS y una gestión segura de secretos.
- Protege el acceso a Swagger UI si la aplicación se expone fuera de un entorno de desarrollo.
- Realiza copias de seguridad periódicas de la base de datos.

## 🐛 Solución de problemas

### El backend no puede conectarse a PostgreSQL

Comprueba que PostgreSQL esté en ejecución:

```
docker compose ps
docker compose logs postgres

```

Verifica que el host y el puerto sean correctos:

- Backend ejecutado en Docker: `postgres:5432`.
- Backend ejecutado localmente: `localhost:5433`.

Confirma también que las credenciales y el nombre de la base de datos coincidan con los valores de `.env`.

### No puedo acceder a pgAdmin

1. Comprueba que el contenedor esté en ejecución con `docker compose ps`.
2. Revisa los registros con `docker compose logs pgadmin`.
3. Verifica que el puerto local `5050` no esté ocupado.
4. Confirma que el correo y la contraseña de acceso estén configurados correctamente.

### Los cambios del backend no aparecen

Si ejecutas el backend dentro de Docker, vuelve a construir la imagen:

```
docker compose up -d --build backend

```

Si lo ejecutas con Gradle, detén la ejecución anterior y vuelve a iniciar:

```
./gradlew bootRun

```

### Un puerto ya está en uso

Comprueba qué aplicación está utilizando el puerto y, si es necesario, modifica la asignación correspondiente en `docker-compose.yml`.

## 📌 Notas finales

- Ejecuta los comandos desde el directorio raíz del proyecto.
- Comprueba que el archivo `.env` esté configurado antes de iniciar los servicios.
- Utiliza los puertos internos al comunicar contenedores entre sí y los puertos publicados al conectarte desde el equipo local.
- Conserva los volúmenes de PostgreSQL cuando quieras mantener los datos entre reinicios.
- Revisa la configuración específica del proyecto si las rutas de Swagger, las variables de entorno o los nombres de los servicios difieren de los descritos en este documento.

---

**CampusDesk** — Sistema de gestión académica con una infraestructura contenerizada, modular y preparada para facilitar el desarrollo y el despliegue.
