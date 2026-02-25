# Tarraco-Fests

**Tarraco-Fests** és una aplicació mòbil dissenyada per centralitzar, filtrar i actualitzar la consulta d'esdeveniments a la ciutat de Tarragona. Aquest projecte neix de la necessitat d'oferir una agenda cultural unificada i accessible per a ciutadans i visitants.

## Membres
 - Jordan Roig López
 - Eric Fabricio Alessi
 - Mateo Leonardo Reyes Pineda
 - Raúl Silva Moreno

## 🎯 Objectius del Projecte

### Objectiu General
Dissenyar i desenvolupar una solució mòbil que millori l'accessibilitat a l'oferta cultural i d'oci de Tarragona mitjançant una interfície usable i informació en temps real.

### Objectius Específics
- **Anàlisi:** Estudiar les necessitats dels usuaris potencials.
- **UX/UI:** Dissenyar una interfície centrada en la usabilitat.
- **Desenvolupament:** Crear una APP funcional (Android natiu o Multiplataforma).
- **Backend:** Implementar gestió d'esdeveniments i connexió amb base de dades/API.
- **Qualitat:** Aplicar bones pràctiques d'arquitectura de programari i testing.

## 🚀 Funcionalitats

### 👤 Usuari
* **Llistat d'esdeveniments:** Visualització amb paginació/càrrega incremental.
* **Detalls:** Fitxa completa (títol, descripció, data/hora, ubicació, categoria).
* **Filtratge:** Per data, categoria (cultura, esport, música, familiar) i ubicació.
* **Cerca:** Per paraules clau.
* **Favorits:** Gestió d'esdeveniments desats localment o al núvol.
* **Vistes:** Opció de visualització en llista o calendari.

### 🛠 Administració
* CRUD complet d'esdeveniments (Afegir, Editar, Eliminar).
* Gestió de categories.
* Gestió d'usuaris i rols (Admin/Usuari bàsic).

### ⭐ Opcionals (Roadmap)
* Notificacions push per esdeveniments propers.
* Integració amb APIs de mapes (Google Maps).
* Compartir a xarxes socials.
* Mode fosc.

## 💻 Stack Tecnològic Proposat

| Capa | Tecnologies |
| :--- | :--- |
| **Plataforma** | Android (Java) / Flutter (Dart) |
| **Persistència** | SQLite (Local) / Firebase (Núvol) |
| **Control de Versions** | Git & GitHub |
| **Arquitectura** | MVC / MVVM (segons implementació final) |

## 📅 Metodologia

El desenvolupament seguirà un enfocament **iteratiu i incremental**, dividit en les fases següents:
1.  Anàlisi de requisits.
2.  Disseny del sistema i prototipatge.
3.  Implementació del codi.
4.  Proves (unitàries/funcionals) i validació.
5.  Documentació i presentació.

---
*Projecte desenvolupat com a part del cicle formatiu de DAM (Desenvolupament d'Aplicacions Multiplataforma).*

## Documentacion tecnica reciente

- Perfil de usuario (arquitectura, flujos, extensibilidad): `DOCUMENTACION_PERFIL.md`
- Esquema Firestore actualizado: `Diseño de la base de datos.md`
