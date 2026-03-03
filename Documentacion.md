# Documentacion Natural del Codigo

Este documento resume el funcionamiento real de la app de forma simple, apoyado por los comentarios agregados en cada clase Java.

## Flujo de entrada

- `LandingActivity`: muestra la puerta de entrada de autenticacion.
- `AuthActivity`: ejecuta el login con Google o correo/contrasena y controla casos mixtos (cuenta Google con password vinculada).
- `TarracoFestApp`: inicializa el proceso global de aplicacion.

## Flujo principal de usuario

- `HomeActivity`: pantalla principal tras autenticar.
  - carga eventos;
  - aplica filtros;
  - abre detalle;
  - controla menu lateral;
  - muestra opcion admin solo si el usuario tiene permisos.
- `EventosActivity`: listado clasico de eventos.
- `DetailActivity`: detalle del evento con descripcion, fuente e integracion de recordatorios.
- `AjustesActivity`: preferencias generales.

## Perfil y seguridad

- `PerfilActivity`: dividido en informacion general, seguridad y edicion.
  - editar datos del usuario;
  - vincular Google;
  - vincular password;
  - cambiar password por email reset.
- `UsuarioRepository`: integra Firebase Auth + Firestore para leer/guardar perfil y metodos de acceso.
- `UsuarioPerfil`: modelo completo del perfil, con datos visibles y metadatos internos (rol, bloqueo, metodos).

## Modulo admin

- `AdminPanelActivity`: entrada del modulo admin.
- `AdminUsuariosActivity`: gestion de usuarios (rol y bloqueo).
- `AdminEventosActivity`: CRUD de eventos y subida de imagenes.
- `AdminAccessRepository`: valida si el usuario actual es admin y no esta bloqueado.
- `AdminUsersRepository`: operaciones de usuarios para admin.
- `AdminEventosRepository`: operaciones de eventos para admin y subida de imagen a Firebase Storage.
- `AdminUser` y `AdminEvento`: modelos de trabajo para pantallas admin.

## Eventos y datos

- `EventosRepository`: repositorio principal de eventos para consumo en UI.
- `Evento`: modelo de dominio de evento para la parte de usuario.
- `FirestoreSchema`: contrato de nombres para colecciones/campos usados en Firestore.

## Recordatorios

- `ReminderRepository`: guarda y lee recordatorios del usuario.
- `ReminderScheduler`: programa y cancela alarmas locales.
- `ReminderReceiver`: recibe la alarma y crea la notificacion.

## Adaptadores (UI listados)

- `EventosAdapter`: render de eventos para usuario final.
- `AdminUsersAdapter`: render de usuarios y acciones admin.
- `AdminEventosAdapter`: render de eventos admin y acciones de gestion.

## Paquete legacy (prototipo inicial)

El paquete `com.example.prueba_tarracofests` conserva el prototipo original:

- `MainActivity`
- `DetailActivity`
- `Evento`
- `EventoAdapter`

No es el flujo principal actual, pero sirve como referencia historica del proyecto.
