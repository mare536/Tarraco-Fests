# Modulo Perfil - Documentacion Tecnica

Este documento describe la implementacion actual del perfil de usuario en `Tarraco_Fest`.

## 1) Objetivo

El modulo de perfil permite:

- Consultar estado de datos de cuenta (informacion general).
- Vincular password en cuentas que no la tengan (seguridad).
- Editar datos de perfil (nombre, telefono, ciudad, bio).

El rol (`admin`/`usuario`) se mantiene interno para control de permisos y no se muestra al usuario final.

## 2) Arquitectura

Se aplica separacion por capas para facilitar mantenimiento y escalabilidad:

- `Activity` (UI y flujo de pantalla):
  - `app/src/main/java/com/example/tarraco_fest/Activity/PerfilActivity.java`
- `Repository` (acceso a datos Auth + Firestore):
  - `app/src/main/java/com/example/tarraco_fest/Repository/UsuarioRepository.java`
- `Modelo` (entidad de dominio):
  - `app/src/main/java/com/example/tarraco_fest/Modelo/UsuarioPerfil.java`
- `Schema centralizado` (nombres de campos/colecciones):
  - `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

## 3) UI y navegacion

La pantalla usa `DrawerLayout` con menu lateral (hamburguesa) y 3 secciones:

1. `Informacion general`
2. `Seguridad`
3. `Modificar datos`

Recursos principales:

- Layout principal: `app/src/main/res/layout/activity_perfil.xml`
- Header drawer: `app/src/main/res/layout/nav_header_perfil.xml`
- Menu drawer: `app/src/main/res/menu/menu_perfil_drawer.xml`

## 4) Flujos funcionales

### 4.1 Carga de perfil

1. `PerfilActivity` llama `usuarioRepository.cargarPerfilActual(...)`.
2. Si no existe `usuarios/{uid}`, el repository crea un documento base.
3. El repository mapea Firestore + FirebaseAuth a `UsuarioPerfil`.
4. La Activity renderiza:
   - Valores reales de cada campo.
   - `No establecida` cuando el campo esta vacio.

### 4.2 Guardar datos editables

1. Validaciones en UI (nombre obligatorio, telefono con patron valido).
2. `UsuarioRepository.guardarInformacionGeneral(...)`:
   - Actualiza `displayName` en Firebase Auth.
   - Actualiza Firestore con `nombreMostrado`, `telefono`, `ciudad`, `biografia`, `updatedAt`.

### 4.3 Vincular password

1. UI valida longitud minima y confirmacion.
2. `UsuarioRepository.vincularPassword(...)` usa `linkWithCredential(...)`.
3. Si ok, guarda en Firestore:
   - `tienePassword = true`
   - `passwordVinculadaEn`
   - `updatedAt`

## 5) Escalabilidad

Puntos preparados para crecer sin romper otras pantallas:

- Toda logica de datos de perfil esta concentrada en `UsuarioRepository`.
- El modelo `UsuarioPerfil` desacopla la UI del formato raw de Firestore.
- Los nombres de campos estan centralizados en `FirestoreSchema`.
- El rol se conserva internamente para reglas futuras sin exponerlo en UI.

## 6) Notas de integracion con equipo

- Si se agregan nuevos campos de usuario:
  1. Definir constante en `FirestoreSchema.UsuarioFields`.
  2. Mapear en `UsuarioRepository.mapearPerfil(...)`.
  3. Exponer en `UsuarioPerfil`.
  4. Mostrar/editar en `PerfilActivity` segun corresponda.

- Si se agregan privilegios de admin:
  - Consumir `rol` desde el backend/repository para permisos.
  - Evitar mostrar el rol al usuario final en la pantalla de perfil.
