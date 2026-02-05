# Informe de Seguiment i Línia Temporal del Projecte: Tarraco Fests

Aquest document detalla l'evolució tècnica i cronològica del projecte, identificant les fites assolides i els responsables de cada tasca.

---

## 14/01/2026 - Inici del projecte i definició d'estructures
* **Detalls tècnics:** Sessió de llançament operativa. S'han establert les bases metodològiques del projecte, la jerarquia de rols de l'equip i la configuració inicial de l'entorn de treball col·laboratiu per garantir una traçabilitat correcta del codi i les tasques.
* **Autoria:** Jordan Roig

## 14/01/2026 - Disseny conceptual i primers mockups
* **Detalls tècnics:** Elaboració dels esquemes visuals inicials (*wireframes*) per definir l'arquitectura d'informació i l'experiència d'usuari (UX). L'objectiu ha estat validar el flux de navegació principal abans d'iniciar el desenvolupament de frontend.
* **Autoria:** Raúl Silva

## 14/01/2026 - Identitat visual: Branding i Logotip
* **Detalls tècnics:** Fase d'exploració creativa i pluja d'idees centrada en la identitat de marca. S'han realitzat diverses iteracions de logotips buscant una estètica moderna i escalable per a diferents resolucions de pantalla.
* **Autoria:** Eric Alessi, Mateo Reyes i Jordan Roig

## 21/01/2026 – 22/01/2026 - Refinament d'interfície (UI)
* **Detalls tècnics:** Pulit d'alta fidelitat de les pantalles del mockup. S'han definit els estils visuals finals, components reutilitzables i la guia d'estils que servirà de base per a la implementació del codi.
* **Autoria:** Raúl Silva

## 21/01/2026 – 22/01/2026 - Setup del repositori i Auth Social
* **Detalls tècnics:** Inicialització del projecte de codi (boilerplate) i configuració del repositori. Implementació inicial del SDK de Firebase i configuració de l'autenticació mitjançant Google (OAuth 2.0).
* **Autoria:** Eric Alessi

## 22/01/2026 – 23/01/2026 - Flux d'Autenticació complet i Firestore
* **Detalls tècnics:** Implementació del flux de *Auth* complet. S'ha integrat el login híbrid (Google + Email/Password) i s'ha configurat la persistència de dades a Cloud Firestore, creant automàticament un document d'usuari a `usuarios/{uid}` en el moment del registre.
* **Autoria:** Eric Alessi

## 28/01/2026 – 29/01/2026 - Redisseny visual: Glassmorphism
* **Detalls tècnics:** Actualització del disseny del diàleg de registre cap a una estètica *glass/festival*. S'han implementat variants de disseny per a l'adaptació nativa de la interfície entre els modes clar i fosc (*Light/Dark mode*).
* **Autoria:** Eric Alessi

## 29/01/2026 – 30/01/2026 - Validació i Feedback visual
* **Detalls tècnics:** Implementació de la lògica de verificació d'email obligatòria. S'han afegit elements de UX per millorar el feedback: efectes de vibració (*shake*) en errors, mètodes `setError` en camps de text i banners informatius d'estat.
* **Autoria:** Eric Alessi

## 04/02/2026 – 05/02/2026 - Implementació de Linking de comptes
* **Detalls tècnics:** Evolució del flux de "Google-only" cap a un sistema multi-accés. S'ha desenvolupat la funcionalitat d'*Account Linking*, permetent als usuaris vincular credencials de correu/contrasenya a un perfil ja existent creat via Google.
* **Autoria:** Eric Alessi