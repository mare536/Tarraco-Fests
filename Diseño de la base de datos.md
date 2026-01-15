# 🏛️ Tarraco Fests — Base de datos (Firebase Firestore)

Convención general:
- **Colecciones** en plural (`events`, `categories`, `users`).
- Cada registro es un **documento** identificado por un **docId**.
- Las “relaciones” se hacen guardando IDs (no hay joins).

---

## 1) 🎉 `events` — Eventos
**Ruta:** `events/{eventId}`  
**Documento:** `{eventId}` (**docId**)

**Campos**
- **id** *(docId)*
- **title** *(string)*
- **description** *(string)*
- **startAt** *(timestamp)*
- **endAt** *(timestamp, opcional)*
- **categoryId** *(string, referencia lógica a `categories/{categoryId}`)*
- **locationName** *(string, nombre del sitio)*
- **address** *(string)*
- **city** *(string, ej. “Tarragona”)*
- **lat** *(number, opcional)*
- **lng** *(number, opcional)*
- **extraInfo** *(string, opcional: web/teléfono/etc.)*
- **imageUrl** *(string, opcional)*
- **isActive** *(boolean, para ocultar sin borrar)*
- **createdAt** *(timestamp)*
- **updatedAt** *(timestamp)*
- **createdBy** *(string uid, opcional)*
- **keywords** *(array<string>, búsqueda simple)*

---

## 2) 🏷️ `categories` — Categorías
**Ruta:** `categories/{categoryId}`  
**Documento:** `{categoryId}` (**docId**: `music`, `sport`, `culture`…)

**Campos**
- **id** *(docId)*
- **name** *(string, ej. “Música”, “Esport”…)*  
- **icon** *(string, opcional: nombre de icono)*
- **color** *(string, opcional)*
- **order** *(number, opcional: orden en la UI)*
- **isActive** *(boolean)*

---

## 3) 👤 `users` — Usuarios
**Ruta:** `users/{uid}`  
**Documento:** `{uid}` (**docId = uid de Firebase Auth**)

**Campos**
- **id** *(docId)*
- **displayName** *(string)*
- **email** *(string)*
- **role** *(string: “user” | “admin”)*
- **createdAt** *(timestamp)*
- **isBlocked** *(boolean, opcional)*

---

## 4) ⭐ `users/{uid}/favorites` — Favoritos (subcolección)
**Ruta:** `users/{uid}/favorites/{eventId}`  
**Documento:** `{eventId}` (**docId puede ser el `eventId`**)

**Campos**
- **eventId** *(string, solo si NO usas el docId como id)*
- **createdAt** *(timestamp)*

---

## 5) 📝 `reports` — Reportes (opcional)
**Ruta:** `reports/{reportId}`  
**Documento:** `{reportId}` (**docId**)

**Campos**
- **id** *(docId)*
- **eventId** *(string)*
- **userId** *(string uid)*
- **reason** *(string)*
- **createdAt** *(timestamp)*
- **status** *(string: “open” | “reviewed”)*

---

## 6) ⏰ `notifications` — Notificaciones (opcional)
**Ruta:** `notifications/{notificationId}`  
**Documento:** `{notificationId}` (**docId**)

**Campos**
- **id** *(docId)*
- **userId** *(string uid)*
- **eventId** *(string)*
- **scheduledAt** *(timestamp)*
- **type** *(string: “reminder”)*
- **sent** *(boolean)*

---

## 🔗 Relaciones (lógicas)
- `events.categoryId` → `categories/{categoryId}`
- `users/{uid}/favorites/{eventId}` → `events/{eventId}`
- `reports.eventId` → `events/{eventId}`
- `notifications.eventId` → `events/{eventId}`
