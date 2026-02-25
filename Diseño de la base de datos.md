# Tarraco Fests - Diseno de base de datos (Firestore)

Este documento refleja el esquema que usa el codigo actual del proyecto.

## 1) Coleccion `usuarios`

Ruta: `usuarios/{uid}`  
`uid` = id de Firebase Auth.

Campos usados por la app:

- `metodoAuth` (string): metodo de la sesion actual (`email` o `google`).
- `metodosVinculados` (array<string>): metodos vinculados a la cuenta.
- `nombreMostrado` (string): nombre visible del usuario.
- `email` (string): email principal de la cuenta.
- `telefono` (string): telefono opcional del perfil.
- `ciudad` (string): ciudad opcional del perfil.
- `biografia` (string): bio corta opcional del perfil.
- `rol` (string): por defecto `usuario`.
- `bloqueado` (boolean): por defecto `false`.
- `creadoEn` (timestamp): solo en alta inicial.
- `updatedAt` (timestamp): cuando se actualiza perfil o metodos vinculados.
- `aceptaTerminos` (boolean): control de terminos y condiciones.
- `aceptaTerminosEn` (timestamp): fecha de aceptacion.
- `tienePassword` (boolean): marca para cuentas que anaden password.
- `passwordVinculadaEn` (timestamp): fecha de vinculacion de password.

Subcoleccion:

- `usuarios/{uid}/recordatorios/{eventId}`
  - `eventId` (string)
  - `createdAt` (timestamp)

## 2) Coleccion `eventos`

Ruta: `eventos/{eventId}`

Campos leidos por la app:

- `titulo` (string)
- `inicio` (timestamp)
- `lugarNombre` (string)
- `activo` (boolean)

Notas:

- El listado actual ordena por `inicio`.
- El listado actual filtra en cliente por `activo != false`.
- El `eventId` se toma del `docId`.

## 3) Convenciones de codigo

Para evitar desalineaciones, el proyecto centraliza nombres de colecciones y campos en:

- `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

Si se cambia un nombre en Firestore, hay que actualizar esa clase y luego ajustar/migrar datos.

## 4) Nota sobre roles

- El campo `rol` se mantiene para control interno de permisos (por ejemplo admin/usuario).
- La UI de perfil no muestra el rol al usuario final.
