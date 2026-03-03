# Modulo Admin - Documentacion Tecnica

Este documento describe la implementacion actual del modulo de administracion en `Tarraco_Fest`.

## 1) Objetivo

El modulo admin permite gestion interna de:

- Usuarios: bloqueo/desbloqueo y cambio de rol (`usuario`/`admin`).
- Eventos: crear, editar y activar/desactivar publicacion.

El rol es interno. El usuario final no ve su rol en la pantalla de perfil.

## 2) Arquitectura

Se mantiene separacion por capas para escalar sin acoplar UI y datos:

- `Activity` (flujo y UI):
  - `app/src/main/java/com/example/tarraco_fest/Activity/AdminPanelActivity.java`
  - `app/src/main/java/com/example/tarraco_fest/Activity/AdminUsuariosActivity.java`
  - `app/src/main/java/com/example/tarraco_fest/Activity/AdminEventosActivity.java`
- `Repository` (acceso a Firestore/Auth):
  - `app/src/main/java/com/example/tarraco_fest/Repository/AdminAccessRepository.java`
  - `app/src/main/java/com/example/tarraco_fest/Repository/AdminUsersRepository.java`
  - `app/src/main/java/com/example/tarraco_fest/Repository/AdminEventosRepository.java`
- `Modelo` (entidades):
  - `app/src/main/java/com/example/tarraco_fest/Modelo/AdminUser.java`
  - `app/src/main/java/com/example/tarraco_fest/Modelo/AdminEvento.java`
- `Adapter` (render y acciones por item):
  - `app/src/main/java/com/example/tarraco_fest/Adapter/AdminUsersAdapter.java`
  - `app/src/main/java/com/example/tarraco_fest/Adapter/AdminEventosAdapter.java`
- `Schema centralizado`:
  - `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

## 3) Control de acceso admin

`AdminAccessRepository.verificarAccesoAdmin(...)` valida:

1. Usuario autenticado (`uid` no nulo).
2. Documento `usuarios/{uid}`.
3. `rol == admin`.
4. `bloqueado != true`.

Solo si se cumplen los 4 puntos se habilita acceso.

Aplicacion actual:

- `HomeActivity`: `btnAdmin` inicia oculto y solo se muestra si la validacion devuelve admin.
- `AdminPanelActivity`, `AdminUsuariosActivity`, `AdminEventosActivity`: vuelven atras (`finish`) si no hay permisos.

## 4) Flujo de gestion de usuarios

Pantalla: `AdminUsuariosActivity`.

- Carga: `AdminUsersRepository.cargarUsuarios(...)`.
  - Fuente: coleccion `usuarios`.
  - Orden: por `email`.
  - Limite: `500`.
- Acciones:
  - Bloqueo/desbloqueo: `actualizarBloqueo(uid, bloqueado)`.
  - Cambio de rol: `actualizarRol(uid, rol)`.
- Auditoria basica:
  - Cada cambio escribe `updatedAt = Timestamp.now()`.

Render:

- `AdminUsersAdapter` aplica badges visuales para rol y estado.
- Botones por usuario:
  - `Bloquear/Desbloquear`.
  - `Hacer admin/Hacer usuario`.

## 5) Flujo de gestion de eventos

Pantalla: `AdminEventosActivity`.

- Carga: `AdminEventosRepository.cargarEventos(...)`.
  - Fuente: coleccion `eventos`.
  - Orden: por `inicio`.
  - Limite: `500`.
- Crear evento:
  - `crearEvento(...)` guarda `titulo`, `lugarNombre`, `inicio`, `activo`, `creadoEn`, `updatedAt`.
- Editar evento:
  - `actualizarEvento(...)` actualiza campos y `updatedAt`.
- Activar/desactivar:
  - `actualizarActivo(eventId, activo)` + `updatedAt`.

Validaciones actuales en dialogo:

- `titulo` obligatorio.
- `fecha` obligatoria y formato `yyyy-MM-dd HH:mm`.

## 6) Ajuste visual aplicado (problema de recorte)

Incidencia reportada: en pantallas estrechas la cabecera de `Eventos` cortaba botones/texto.

Correccion implementada:

- `app/src/main/res/layout/activity_admin_eventos.xml`:
  - `Crear evento` pasa a fila completa.
  - `Recargar` y `Volver` quedan en segunda fila.
  - Botones con `maxLines="1"` y `ellipsize="end"`.
  - Altura unificada `52dp`.
- `app/src/main/res/layout/activity_admin_usuarios.xml`:
  - Botones de cabecera ajustados tambien a `52dp`, `maxLines="1"` y `ellipsize="end"` para consistencia.

Resultado: se elimina el recorte de texto en la zona superior.

## 7) Como crear el primer admin

Bootstrap manual (actual):

1. Crear/iniciar sesion con un usuario normal.
2. En Firestore, ir a `usuarios/{uid}`.
3. Establecer:
   - `rol = "admin"`
   - `bloqueado = false`
4. Cerrar e iniciar sesion de nuevo.

Con eso aparecera el boton de admin en Home.

## 8) Recomendaciones de seguridad (importante)

La validacion en cliente mejora UX, pero no protege por si sola. Para produccion se recomienda:

- Reglas Firestore que permitan escrituras de `usuarios` y `eventos` solo a admins.
- Opcional: asignar custom claims (`admin`) desde backend para endurecer permisos.

### 8.1 Endurecimiento implementado: subida de imagenes solo admin

Se ha agregado `storage.rules` para que solo usuarios con:

- `usuarios/{uid}.rol == "admin"`
- `usuarios/{uid}.bloqueado != true`

puedan crear/editar/eliminar archivos en `eventos/**` (imagenes de eventos).

Restricciones adicionales:

- Solo `contentType` de imagen (`image/*`).
- Limite de tamano: `< 10 MB`.
- El resto del bucket queda denegado por defecto.

Archivos agregados:

- `storage.rules`
- `firebase.json` (apunta a `storage.rules`)

Despliegue:

1. Iniciar sesion con Firebase CLI (`firebase login`).
2. Seleccionar proyecto (`firebase use <project-id>`).
3. Desplegar reglas de Storage:
   - `firebase deploy --only storage`

Nota: este control depende del campo `rol` en Firestore. Si las reglas de Firestore
permiten que un usuario normal se autoasigne `rol = admin`, podria escalar permisos.
Para cerrar ese vector, se recomienda endurecer tambien las reglas de Firestore.

## 9) Extensiones previstas

- Paginacion real de listas de usuarios/eventos.
- Buscador y filtros en admin.
- Soft delete/historial de cambios.
- Registro de auditoria (quien modifica que y cuando).
