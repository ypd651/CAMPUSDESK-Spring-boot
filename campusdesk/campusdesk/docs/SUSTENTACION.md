# Guía de sustentación de CampusDesk

Este documento es para preparar la presentación: qué mostrar, en qué orden, dónde está cada cosa en el código
y cómo responder las preguntas más probables. Los nombres de clases son los reales del proyecto.

## 1. Antes de empezar (5 minutos)

- Base de datos y pgAdmin arriba: `docker compose up -d` y `docker compose ps` (todo en `healthy`/`running`).
- Si la base ya existía de antes, ejecuta `carpeta_scripts/migrations/02_extended_features.sql`.
- Tener abiertas: la app (frontend), Swagger (`/swagger-ui.html`), pgAdmin (tabla `tickets`, `status_history`, `audit_log`).
- Tener a mano cuatro sesiones: ADMIN (el del `.env`), un USER, un TECHNICIAN y otro USER (para el caso "ticket ajeno").
  Los crea el admin en *Administración > Usuarios*. Si usas `carpeta_scripts/optional/sample_data.sql`, ya existen.
- Ten un token válido copiado para Swagger (botón **Authorize**, pegar solo el token, sin "Bearer ").

## 2. Guion de la demostración completa

Flujo pedido: creación de una incidencia hasta su cierre, con la cuenta de cada rol.

| Paso | Cuenta | Acción | Qué decir / mostrar |
| --- | --- | --- | --- |
| 1 | ADMIN | Crear el usuario USER en *Administración* | No hay registro público; solo el admin crea cuentas (RF-01) |
| 2 | USER | Iniciar sesión, crear un ticket | Queda en `OPEN` (CP-05); mostrar la fila en pgAdmin |
| 3 | USER | Intentar abrir el ticket de otro usuario por URL | 403/404 (CP-06): la política está en `TicketAccessPolicy`, no en la interfaz |
| 4 | ADMIN | Asignar el técnico | Pasa a `ASSIGNED` (CP-07); mostrar el historial y la fila en `audit_log` |
| 5 | USER | En Swagger intentar `PATCH /tickets/{id}/assign` | 403 (CP-08) |
| 6 | TECH | Cambiar a `EN_PROCESO` y luego a `RESUELTA` | Se crea un registro en el historial por cada cambio (CP-11) |
| 7 | TECH | En Swagger, resolver un ticket que no pasó por `IN_PROGRESS` | 409 (CP-10) |
| 8 | TECH | Intentar modificar un ticket de otro técnico | Rechazado (CP-09) |
| 9 | USER | Cerrar el ticket `RESOLVED` | `CLOSED` (CP-12); mostrar que ya no permite comentar (CP-13) |
| 10 | ADMIN | Abrir el dashboard | Los números coinciden con una consulta SQL en pgAdmin (CP-14) |
| 11 | cualquiera | En Swagger, llamar sin token / con token alterado | 401 (CP-04 y CP-16) |
| 12 | USER | Registrar un correo duplicado (como ADMIN, al crear usuario) | Rechazo con 409 (CP-02) |

Consultas SQL útiles para tener listas en pgAdmin:

```sql
SELECT status, COUNT(*) FROM tickets GROUP BY status ORDER BY status;
SELECT * FROM status_history WHERE ticket_id = 1 ORDER BY changed_at;
SELECT action, details, created_at FROM audit_log ORDER BY created_at DESC LIMIT 10;
```

Extras para lucirte, en este orden:

1. **Bloqueo optimista**: abre el mismo ticket con dos admins en dos pestañas, asigna técnico en ambas; la segunda recibe 409.
2. **Cancelar** un ticket `OPEN` y **rechazar una resolución** con nota.
3. **Paginación y filtros** en la lista (`/api/tickets/search` en Swagger).
4. **Auditoría** en *Administración > Audit*.

## 3. Dónde está cada cosa en el código

| Tema | Clase o archivo |
| --- | --- |
| Arquitectura por capas | `controller`, `service`, `repository`, `entity`, `dto`, `security`, `exception` |
| Reglas de negocio del ciclo de vida | `TicketStatus.canTransitionTo` y `TicketService.changeStatus` |
| Quién puede hacer qué | `TicketAccessPolicy` (también `availableActions` que usa la interfaz) |
| Permisos y roles configurables | `Permission` (enum), `Role` (conjunto de permisos), `UserPrincipal.hasPermission` |
| JWT | `JwtUtil` (crea y valida, HMAC256, expiración), `JwtFilter` (lee el header en cada petición) |
| Configuración de seguridad | `SecurityConfig` (stateless, CSRF off, CORS, filtro JWT, `@EnableMethodSecurity`) |
| 401 y 403 en JSON | handlers de `SecurityConfig` (`authenticationEntryPoint`, `accessDeniedHandler`) |
| Excepciones globales | `GlobalExceptionHandler` (`@RestControllerAdvice`), sin trazas ni datos sensibles |
| CORS por origen | `CorsConfig` + `CorsProperties` (`app.cors.allowed-origins`) |
| Primer admin sin credenciales en el código | `DataInitializer` + `BootstrapProperties` (variables de `.env`) |
| Historial y estado en una sola transacción | `@Transactional` en `TicketService` |
| Estadísticas desde PostgreSQL | `ReportService` + `TicketRepository` (conteos con `count(Specification)`) |
| Filtros que respetan permisos | `TicketAccessPolicy.visibleTo` (`Specification` de JPA) |
| Paginación y orden | `TicketService.search` (lista blanca de campos para ordenar) |
| Auditoría | `AuditService`, entidad `AuditLog` |
| Bloqueo optimista | `@Version` en `Ticket`, `checkVersion` en `TicketService`, handler 409 |
| Tarea programada | `TicketAutoCloseJob` + `SchedulingConfig` |
| Eventos | `TicketStatusChangedEvent` + `TicketNotificationListener` |

## 4. Preguntas de explicación y cómo responderlas

**¿Cómo funciona el JWT de principio a fin?**
El usuario envía correo y contraseña a `POST /api/auth/login`. `AuthService` busca el usuario, compara la contraseña con
BCrypt y, si coincide, `JwtUtil` genera un token firmado con HMAC256 que lleva el correo como *subject* y una fecha de
expiración. El frontend lo guarda y lo manda en `Authorization: Bearer ...`. En cada petición `JwtFilter` lo valida, carga
el usuario desde la base de datos (así un cambio de rol o una cuenta deshabilitada se nota enseguida) y deja la
autenticación en el `SecurityContext`. Si falta, está alterado o expiró, la petición queda sin autenticar y el
*entry point* responde 401.

**¿Por qué la sesión es `STATELESS` y por qué se desactiva CSRF?**
Con JWT el servidor no guarda sesión: cada petición lleva su credencial. CSRF protege sesiones basadas en cookies que el
navegador envía solo; aquí el token va en un header que el navegador no añade automáticamente, así que ese ataque no aplica.

**¿Diferencia entre 401 y 403?**
401: no sabemos quién eres (sin token, inválido o expirado). 403: sabemos quién eres, pero no tienes permiso.

**¿Por qué BCrypt y no cifrar la contraseña?**
Cifrar es reversible; BCrypt es un hash lento con *salt* propio: no se puede recuperar la contraseña y cada hash es
distinto aunque la contraseña se repita. Solo se guarda el hash (`password_hash`).

**¿Por qué la lógica va en el servicio y no en el controlador?**
El controlador solo traduce HTTP (parámetros, estado de respuesta). Las reglas viven en `TicketService`/`TicketAccessPolicy`,
así se prueban sin levantar un servidor (por eso los tests con Mockito) y se reutilizan.

**¿Cómo funciona `@Transactional` y qué pasa si falla el historial?**
Spring envuelve el servicio en un proxy que abre una transacción, ejecuta el método y hace *commit*; si lanza una
excepción de runtime hace *rollback*. En `changeStatus` el cambio de estado y el registro del historial van en la misma
transacción: si falla el segundo, el primero se deshace. Límite conocido: una llamada a un método `@Transactional` de la
misma clase no pasa por el proxy y no abre transacción; por eso el job de cierre automático llama a `TicketService`
desde otra clase.

**¿Qué es `LAZY` y por qué `open-in-view=false`?**
`LAZY` carga una relación solo cuando se usa. Con `open-in-view=false` la sesión de Hibernate termina al salir del
servicio, así que los datos que necesita la respuesta se leen dentro del servicio (y `default_batch_fetch_size`
reduce el problema de las N+1 consultas). Fuera de la transacción, tocar una relación `LAZY` lanza
`LazyInitializationException`.

**¿Por qué dos relaciones entre `Ticket` y `User`?**
Un ticket tiene un solicitante (obligatorio) y un técnico (opcional). Son dos `@ManyToOne` con claves foráneas distintas:
`requester_id` y `technician_id`.

**¿Qué hace `@RestControllerAdvice`?**
Centraliza el manejo de excepciones: cada excepción de negocio se convierte en un código HTTP (404, 409, 400...) y un
cuerpo JSON uniforme. La última red (`Exception`) devuelve un 500 genérico y solo escribe el detalle en el log, así no se
filtran trazas internas.

**¿Para qué sirve `@Version` (bloqueo optimista)?**
Evita que dos usuarios pisen el cambio del otro. Hibernate agrega `WHERE version = ?` a cada UPDATE y lo incrementa; si
otro cambió la fila antes, la actualización no afecta ninguna fila y se lanza una excepción que devolvemos como 409. Es
"optimista" porque no bloquea la fila: asume que el conflicto es raro y lo detecta al final.

**¿Qué es un `@TransactionalEventListener(AFTER_COMMIT)`?**
Un oyente de eventos que solo se ejecuta cuando la transacción terminó bien. Así no se notifica un cambio que luego se
deshizo. Con `@Async` corre en otro hilo y no retrasa la respuesta.

**¿Qué hace `ddl-auto`?**
`update` ajusta las tablas al código (cómodo en desarrollo); `validate` solo comprueba que coincidan; `create-drop`
(tests con H2) crea y borra. En producción se usa `validate`/`none` con migraciones.

**¿`@ConfigurationProperties` o `@Value`?**
`@ConfigurationProperties` agrupa y valida un bloque de propiedades en un objeto tipado (`CorsProperties`,
`BootstrapProperties`); `@Value` inyecta un valor suelto, como los días del cierre automático.

**¿Cómo evitas que un usuario vea tickets ajenos?**
En dos niveles: `TicketAccessPolicy.requireView` para un ticket concreto, y `visibleTo` para las listas, que añade la
restricción directamente a la consulta SQL. Nunca se confía en la interfaz.

**¿Cómo evitas que alguien se asigne ADMIN al registrarse?**
No existe registro público. El admin crea las cuentas y elige el rol; el primer admin sale de variables de entorno en
`DataInitializer`.

## 4.1 Pruebas automatizadas

- `./gradlew test` ejecuta tres niveles: pruebas unitarias con Mockito (`TicketServiceTests`, `AuthServiceTests`),
  pruebas de integración con MockMvc (`CampusDeskApiIntegrationTests`, base H2 en memoria) y la prueba de arranque.
- Las de integración recorren la seguridad real: sin token da 401, con un usuario sin permiso da 403, y el ciclo
  completo de un ticket pasa por todos los estados. Son buena evidencia de los casos CP-01 a CP-13 y CP-16.

## 4.2 Recetas para cambios en vivo

Si el profesor pide "agrega X", casi siempre cae en uno de estos cinco moldes. Cada receta indica los archivos
exactos y un ejemplo del proyecto que ya lo hace, para que lo uses como referencia. Rutas del backend relativas a
`backend/src/main/java/com/technova/campusdesk/`.

### A. Agregar un campo a una entidad (ejemplo en el proyecto: `dueDate`)

1. `entity/Ticket.java`: el atributo con `@Column`.
2. `dto/TicketRequest.java`: el componente del record y su validación (`@Size`, `@NotBlank`...). Si los tests usan el
   constructor, deja uno secundario sin el campo nuevo.
3. `dto/TicketResponse.java`: el componente del record y el valor en `from(...)`.
4. `service/TicketService.java`: copia el valor en `create` y en `update` (`ticket.setX(request.x())`).
5. SQL: la columna en `carpeta_scripts/01_schema.sql` y un `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` en
   `carpeta_scripts/migrations/02_extended_features.sql`. Con `ddl-auto=update` Hibernate agrega la columna solo, pero
   si la columna es `NOT NULL` y ya hay filas, falla: por eso se da un `DEFAULT`.
6. Frontend: el campo en el formulario de `frontend/js/pages/tickets.js` (crear) y `ticket.js` (editar y detalle).
7. Prueba: ajusta `TicketServiceTests` si cambió algún constructor.

### B. Agregar un endpoint (ejemplos: `GET /tickets/my-assigned`, `GET /tickets/unassigned`)

1. `controller/TicketController.java`: el método con `@GetMapping`, `@Operation` (Swagger) y, si es restringido,
   `@PreAuthorize("hasAuthority('PERMISO')")`.
2. `service/TicketService.java`: la lógica. Para listas, reutiliza `policy.visibleTo(actor, filtro)`, que ya respeta
   los permisos del usuario.
3. Si necesita una consulta nueva: `repository/TicketRepository.java` (derived query o `@Query`).
4. Declara el orden: un mapping literal (`/overdue`) gana sobre `/{id}`.
5. Prueba: un test en `CampusDeskApiIntegrationTests` (con y sin permiso) y pruébalo en Swagger con **Authorize**.

### C. Agregar un estado o una regla de transición (ejemplo: `CANCELLED` y rechazar una resolución)

1. `entity/TicketStatus.java`: el valor del enum y la regla en `canTransitionTo` (y `isActive` si es final).
2. `service/TicketService.java`, método `changeStatus`: quién puede hacerlo (403) y qué se exige (por ejemplo una
   nota). Una transición imposible es 409; una persona equivocada es 403.
3. `service/TicketAccessPolicy.java`, `availableActions`: el botón que la interfaz debe mostrar.
4. SQL: las restricciones `CHECK` de `tickets` y `status_history` en `01_schema.sql` y en la migración (Hibernate no
   cambia restricciones existentes).
5. Estadísticas: `dto/SummaryResponse.java` y `service/ReportService.java` si se debe contar.
6. Frontend: `frontend/js/ui.js` (`STATUS_LABELS`), el estilo del *badge* en `frontend/css/styles.css` y el botón en
   `frontend/js/pages/ticket.js`.
7. Pruebas: una por transición válida, una inválida y una de permiso en `TicketServiceTests`.

### D. Agregar un filtro de búsqueda (ejemplo: rango de fechas `createdFrom` / `createdTo`)

1. `dto/TicketFilter.java`: el campo nuevo.
2. `service/TicketAccessPolicy.java`, `visibleTo(actor, filter)`: el predicado, con `cb.equal`, `cb.like`, etc. Se suma
   con AND a la restricción de permisos, así que nunca expone tickets ajenos.
3. `controller/TicketController.java`, método `search`: el `@RequestParam` y su paso a `TicketFilter`.
4. Si el campo se puede usar para ordenar: agrégalo a `SORTABLE_FIELDS` en `TicketService`.
5. Frontend: el control en `frontend/js/pages/tickets.js` y el parámetro en `loadTickets`.

### E. Agregar un permiso o un rol (ejemplo: el permiso `TICKET_ASSIGN`)

1. `entity/Permission.java`: el nuevo valor.
2. SQL: el `CHECK` de `role_permissions` en `01_schema.sql` y en la migración, porque lista los permisos válidos.
3. `config/DataInitializer.java`: `ensureRole` le da el permiso nuevo al ADMIN en cada arranque; a los demás roles
   se lo das desde *Administración > Roles* o en la lista de permisos del rol del sistema.
4. Úsalo donde corresponda: `@PreAuthorize("hasAuthority('NUEVO')")` o `policy`/`actor.hasPermission(...)`.
5. Un **rol nuevo** no requiere código: se crea desde *Administración > Roles* combinando permisos.

### Cómo ensayarlo

Elige una petición inventada por ti ("que el ticket tenga una ubicación", "un endpoint de tickets vencidos",
"un estado EN_ESPERA", "filtrar por técnico") y hazla sin mirar esta guía, usando solo el molde. Si te trabas, mira
la receta y repite. Hacerlo dos veces con cada molde es suficiente.

## 5. Si algo sale mal durante la demo

- **401 de golpe**: el token expiró (8 h por defecto). Vuelve a iniciar sesión.
- **500 al listar tickets**: casi seguro falta la migración (`version`, `due_date`, `resolved_at`). Ejecútala y reinicia el backend.
- **CORS en el navegador**: el origen del frontend debe estar en `CORS_ALLOWED_ORIGINS`.
- **Swagger devuelve 401**: pulsa **Authorize** y pega solo el token.
