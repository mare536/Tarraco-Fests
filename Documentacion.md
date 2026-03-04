# Documentacion Natural del Codigo

Este documento resume el estado real del proyecto `Tarraco_Fest` y las mejoras implementadas.
Ultima actualizacion: 2026-03-04.

## 1) Flujo general de la app

- `LandingActivity`: puerta de entrada visual.
- `AuthActivity`: login y registro.
- `HomeActivity`: pantalla principal tras autenticar.
- `DetailActivity`: detalle completo del evento.
- `PerfilActivity`: gestion de datos y seguridad de cuenta.
- `AdminPanelActivity`, `AdminUsuariosActivity`, `AdminEventosActivity`: modulo interno de administracion.
- `AjustesActivity`: permisos y accesos del sistema.

## 2) Autenticacion y sesion

Archivos principales:

- `app/src/main/java/com/example/tarraco_fest/Activity/AuthActivity.java`
- `app/src/main/java/com/example/tarraco_fest/Repository/UsuarioRepository.java`
- `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

Implementado:

- Login con Google y con correo/contrasena.
- Deteccion de cuenta existente para evitar crear duplicados.
- Soporte de cuenta mixta (Google + password Firebase).
- Flujo para anadir contrasena a cuenta creada con Google.
- Recuperacion de contrasena (dialogo dedicado + envio de email reset).
- Validacion de usuario bloqueado antes de entrar.
- Seleccion de cuenta Google (incluyendo casos con varias cuentas en el dispositivo).
- Sesion persistente mientras la app sigue abierta y control de cierre de sesion desde menu.

## 3) Home, eventos y filtros

Archivos principales:

- `app/src/main/java/com/example/tarraco_fest/Activity/HomeActivity.java`
- `app/src/main/java/com/example/tarraco_fest/Repository/EventosRepository.java`
- `app/src/main/java/com/example/tarraco_fest/Adapter/EventosAdapter.java`

Implementado:

- Fusion de eventos propios (Firestore) con eventos de API externa.
- Cache temporal de API para reducir llamadas repetidas.
- Refresco inteligente del listado cuando hay cambios.
- Filtros por categoria, fecha, precio, franja horaria, distancia y zona/calle.
- Filtro de favoritos (`solo favoritos`) integrado con chips y panel de filtros.
- Filtro "Cerca de mi" usando ubicacion del dispositivo.
- Persistencia de filtros en `SharedPreferences`.
- Drawer lateral con estado de seleccion sincronizado con la pantalla actual.
- Respeto de tema claro/oscuro y ajustes visuales de contraste.

## 4) Favoritos por usuario

Archivos principales:

- `app/src/main/java/com/example/tarraco_fest/Repository/FavoritosRepository.java`
- `app/src/main/java/com/example/tarraco_fest/Activity/DetailActivity.java`
- `app/src/main/java/com/example/tarraco_fest/Activity/HomeActivity.java`

Implementado:

- Cada usuario puede guardar/quitar favoritos.
- Estado favorito se refleja en detalle y en Home.
- El filtro de favoritos funciona sobre datos reales del usuario autenticado.

## 5) Detalle de evento y recordatorios

Archivos principales:

- `app/src/main/java/com/example/tarraco_fest/Activity/DetailActivity.java`
- `app/src/main/java/com/example/tarraco_fest/Repository/ReminderRepository.java`
- `app/src/main/java/com/example/tarraco_fest/Reminder/ReminderScheduler.java`
- `app/src/main/java/com/example/tarraco_fest/Reminder/ReminderReceiver.java`

Implementado:

- Estado contextual del recordatorio:
  - evento finalizado;
  - evento empieza pronto;
  - evento sin fecha valida;
  - recordatorio activo.
- Validacion de rango (no permite hora pasada ni posterior al inicio del evento).
- Recordatorios locales con `AlarmManager` + notificacion local.
- Mensajeria de error mejorada:
  - errores importantes se muestran persistentes en `Snackbar` con accion `Entendido`;
  - el estado tambien queda visible en pantalla para no perder el mensaje.
- Integracion con Google Calendar:
  - tras guardar recordatorio local, la app ofrece anadir el evento a Calendar;
  - usa intent de calendario nativo;
  - fallback a calendario generico;
  - fallback final web (`calendar.google.com`) si no hay app compatible.

## 6) Perfil de usuario

Objetivo:

- Permitir al usuario gestionar su informacion sin exponer detalles internos de permisos.
- Mantener el rol como dato interno (`admin`/`usuario`) no visible en UI.

Arquitectura:

- `app/src/main/java/com/example/tarraco_fest/Activity/PerfilActivity.java`
- `app/src/main/java/com/example/tarraco_fest/Repository/UsuarioRepository.java`
- `app/src/main/java/com/example/tarraco_fest/Modelo/UsuarioPerfil.java`
- `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

UI y navegacion:

- Drawer lateral con hamburguesa.
- Secciones:
  - Informacion general.
  - Seguridad.
  - Modificar datos.
- Layout principal: `app/src/main/res/layout/activity_perfil.xml`.
- Header drawer: `app/src/main/res/layout/nav_header_perfil.xml`.
- Menu drawer: `app/src/main/res/menu/menu_perfil_drawer.xml`.

Flujos funcionales:

- Carga de perfil:
  - `PerfilActivity` solicita datos a `UsuarioRepository.cargarPerfilActual(...)`.
  - Si `usuarios/{uid}` no existe, se crea base minima.
  - Se renderiza valor real o `No establecida` si el campo esta vacio.
- Guardado de datos editables:
  - Validacion UI (nombre obligatorio, telefono valido).
  - Actualizacion en Firebase Auth (`displayName`) y Firestore (`nombreMostrado`, `telefono`, `ciudad`, `biografia`, `updatedAt`).
- Seguridad:
  - Vinculacion de Google desde perfil.
  - Vinculacion/anadido de password con `linkWithCredential(...)`.
  - Cambio de contrasena por email reset.

Escalabilidad:

- Campos centralizados en `FirestoreSchema.UsuarioFields`.
- Modelo `UsuarioPerfil` desacopla UI del formato Firestore.
- Repository unico para toda logica de perfil, listo para extender campos sin romper Activities.

## 7) Modulo admin

Objetivo:

- Gestion interna de usuarios y eventos.
- No exponer capacidades admin al usuario estandar.

Arquitectura:

- Activity:
  - `app/src/main/java/com/example/tarraco_fest/Activity/AdminPanelActivity.java`
  - `app/src/main/java/com/example/tarraco_fest/Activity/AdminUsuariosActivity.java`
  - `app/src/main/java/com/example/tarraco_fest/Activity/AdminEventosActivity.java`
- Repository:
  - `app/src/main/java/com/example/tarraco_fest/Repository/AdminAccessRepository.java`
  - `app/src/main/java/com/example/tarraco_fest/Repository/AdminUsersRepository.java`
  - `app/src/main/java/com/example/tarraco_fest/Repository/AdminEventosRepository.java`
- Modelos:
  - `app/src/main/java/com/example/tarraco_fest/Modelo/AdminUser.java`
  - `app/src/main/java/com/example/tarraco_fest/Modelo/AdminEvento.java`
- Adapter:
  - `app/src/main/java/com/example/tarraco_fest/Adapter/AdminUsersAdapter.java`
  - `app/src/main/java/com/example/tarraco_fest/Adapter/AdminEventosAdapter.java`
- Contrato:
  - `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

Control de acceso:

- `AdminAccessRepository.verificarAccesoAdmin(...)` valida:
  - sesion activa;
  - documento `usuarios/{uid}` existente;
  - `rol == admin`;
  - `bloqueado != true`.
- Si falla, las pantallas admin salen (`finish`) y en Home se oculta el acceso admin.

Gestion de usuarios:

- Carga con limite y orden por email.
- Acciones:
  - bloquear/desbloquear;
  - cambiar rol admin/usuario;
  - eliminar solo cuando el usuario esta bloqueado.
- Cada cambio actualiza `updatedAt`.

Gestion de eventos:

- CRUD de eventos con estado `activo/inactivo`.
- Boton de eliminar solo visible para eventos inactivos.
- Formulario con pickers de fecha/hora para evitar errores manuales.
- Imagenes:
  - subida a Firebase Storage cuando hay configuracion valida;
  - fallback a `imagenBase64` en Firestore si Storage no esta disponible;
  - imagen predeterminada por categoria cuando no hay imagen propia.

Bootstrap del primer admin:

- Crear usuario normal.
- En Firestore: `usuarios/{uid}` -> `rol = "admin"` y `bloqueado = false`.
- Cerrar e iniciar sesion para refrescar permisos.

Seguridad recomendada:

- Reglas Firestore para impedir escalado de privilegios desde cliente.
- Reglas Storage para permitir subida solo a admin no bloqueado.

## 8) Push notifications (Firebase Messaging)

Archivos principales:

- `app/src/main/java/com/example/tarraco_fest/Push/TarracoMessagingService.java`
- `app/src/main/java/com/example/tarraco_fest/Push/PushContract.java`
- `app/src/main/java/com/example/tarraco_fest/Repository/PushTokenRepository.java`
- `app/src/main/java/com/example/tarraco_fest/TarracoFestApp.java`

Implementado:

- Registro y sincronizacion de token FCM en `usuarios/{uid}`.
- Canal de notificaciones push general.
- Recepcion de mensajes remotos y apertura contextual hacia Home.
- Soporte de `eventId` en payload para navegar al evento.
- Limpieza/desvinculacion de token al cerrar sesion.

## 9) Icono, splash y nombre de app

Archivos principales:

- `app/src/main/res/values/strings.xml` (`app_name = Tarraco Fests`)
- `app/src/main/res/drawable/ic_launcher_foreground_logo.xml`
- `app/src/main/res/drawable/ic_launcher_background_tarraco.xml`
- `app/src/main/res/drawable/ic_splash_logo.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`

Implementado:

- Separacion de icono de escritorio y logo de splash.
- Escritorio usa `logo_icono` con fondo claro.
- Splash usa `logo_app` via `windowSplashScreenAnimatedIcon`.
- Nombre visible de la app actualizado a `Tarraco Fests`.

## 10) Permisos y configuracion Android

Archivo principal:

- `app/src/main/AndroidManifest.xml`

Permisos actuales:

- `INTERNET`
- `POST_NOTIFICATIONS`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

Extras:

- `android:enableOnBackInvokedCallback="true"`.
- `adjustResize` en pantallas con inputs para mejorar experiencia con teclado.
- Registro de `ReminderReceiver` y `TarracoMessagingService`.

## 11) Modelo de datos (FirestoreSchema)

Archivo principal:

- `app/src/main/java/com/example/tarraco_fest/Data/FirestoreSchema.java`

Estructura central:

- Colecciones: `eventos`, `usuarios`.
- Subcolecciones: `recordatorios`, `favoritos`.
- Campos de usuario para seguridad y permisos:
  - `rol`, `bloqueado`, `eliminado`, `metodosVinculados`, `tienePassword`.
- Campos para push:
  - `fcmToken`, `fcmTokens`, `fcmTokenUpdatedAt`.
- Campos de recordatorio:
  - `eventStartAt`, `remindAt`, offsets de minutos/horas.

## 12) Consolidacion de documentacion

- Este archivo es la fuente unica de documentacion funcional y tecnica del proyecto.
- El detalle de Perfil y Admin ya esta integrado aqui mismo.

## 13) Paquete legacy

El paquete `com.example.prueba_tarracofests` se conserva como prototipo historico y no corresponde al flujo principal actual.

## 14) Soporte multilenguaje

Idiomas soportados:

- Castellano (`es`)
- Catalan (`ca`)
- Ingles (`en`)
- Japones (`ja`)

Archivos de recursos:

- Base: `app/src/main/res/values/strings.xml` (castellano)
- Ingles: `app/src/main/res/values-en/strings.xml`
- Catalan: `app/src/main/res/values-ca/strings.xml`
- Japones: `app/src/main/res/values-ja/strings.xml`

Configuracion Android:

- `app/src/main/res/xml/locales_config.xml`
- `app/src/main/AndroidManifest.xml` con `android:localeConfig="@xml/locales_config"`

### Traduccion automatica de eventos (admin)

- Al crear/editar un evento desde admin, el sistema genera automaticamente versiones de `titulo` y `descripcion` para:
  - `es`, `ca`, `en`, `ja`
- Se guarda en Firestore en:
  - `tituloI18n`
  - `descripcionI18n`
- En lectura (Home/Detalle) se aplica fallback:
  - idioma actual de la app -> `es` -> campo base (`titulo`/`descripcion`)
