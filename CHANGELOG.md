# Enhanced-vq 0.9.3 — Notas de actualización

Rediseño completo del HUD del libro (arte nuevo de 6 pestañas), pestaña nueva "Mago" con 64 misiones de pociones y encantamientos, aviso en pantalla al completar una misión, y varias mejoras de calidad de vida.

---

## 🎨 HUD del libro — arte nuevo

- Fondo del libro reemplazado por el diseño nuevo (6 pestañas de color en vez de 5).
- Pestañas reordenadas: Explorador/Minero/Constructor/Técnico se mantienen, se agregó **Mago** (libro encantado animado) como anteúltima, y **Logros** (manzana animada) pasó al último lugar.
- Página izquierda (lista de misiones) agrandada para usar todo el marco rojo disponible, igual que ya usaba la derecha — antes tenía mucho más margen sin motivo y mostraba menos filas de las que entraban.
- El ícono de "ticket" de cada misión (antes un ✔/○ de texto) ahora es la carita del creeper: gris mientras no está completada, a color apenas se completa.
- Nuevos íconos animados para las 4 categorías (brújula/pico/bloque/redstone) y Mago (libro encantado), extraídos de gifs de referencia y ajustados para que la animación se vea a velocidad natural, no acelerada.
- Tooltips al pasar el mouse por cualquier pestaña, mostrando el progreso (ej. "Explorador (4/12)").

## 🆕 Pestaña "Mago" — contenido nuevo (64 misiones)

- Consigue una Mesa de Pociones y una Mesa de Encantamientos (esta última movida desde Principal, ya no está duplicada).
- 16 misiones de pociones (una por efecto base, sin contar nivel/duración/variante splash-lingering).
- 41 misiones de encantamientos (una por tipo, sin contar nivel) — se excluyeron a propósito las 2 maldiciones.
- 5 hitos de equipo encantado: 1 pieza de armadura, set completo, pico, espada, hacha.
- Recompensas escaladas por qué tan difícil es conseguir cada cosa en el juego real (no por número de nivel): materiales comunes (libros, estanterías, lapislázuli) para lo fácil, y equipo YA encantado de verdad (espada con Filo IV, pico y hacha con Eficiencia IV, armadura con Protección IV, botas con Velocidad de Alma, grebas con Sigilo Veloz) para los logros más difíciles.
- **Detección automática incluida**: las 64 misiones se completan solas al jugar normalmente — tener la poción correspondiente o cualquier ítem con el encantamiento correspondiente en el inventario (o puesto) marca la misión como lista para reclamar. No hace falta ningún paso extra ni comando.

## 🐛 Creeper (pestaña "Completadas") — pulido

- Ícono propio (ya no depende del arte de fondo, que en el diseño nuevo no lo incluye).
- Aparece deslizándose de abajo hacia arriba en 6 pasos durante 1 segundo, justo después de que termina la animación de pasar página — simulando que sale de atrás de la página en vez de aparecer de golpe.
- Corregido de tamaño y posición un par de veces durante el desarrollo (quedaba más grande que la casita, y en la zona transparente entre la casita y la tapa en vez de sobre la tapa).

## 🔔 Aviso de "¡Quest Completada!" — nuevo

- Cartel en la esquina superior izquierda de la pantalla (HUD, no dentro del libro) cada vez que se completa una misión.
- Entra con un barrido de izquierda a derecha, se mantiene 3 segundos completo, y se cierra como una cortina hacia el centro.
- Si se completan varias misiones de golpe, se encolan y se muestran una por una — ninguna se pisa ni se pierde.
- Con protección para no bombardear con carteles al conectarse a un mundo con progreso viejo (solo dispara para misiones nuevas después del primer sync de la sesión).

## ⚙️ Calidad de vida

- Botón "Reclamar Todo" en la pestaña Completadas, para cobrar todas las recompensas pendientes de un solo click.
- Sonidos al reclamar una recompensa y al fijar/desfijar una misión (antes solo el pasar de página tenía sonido).
- Toggle "ocultar completadas" en cada categoría, para no tener que scrollear entre lo que ya hiciste.

## 🏷️ Renombrado

- Quedaban varias referencias sueltas al nombre viejo del proyecto ("HomeQuest") en textos que sí ve el jugador: prefijo de los mensajes de chat/consola, autor en `fabric.mod.json`, copyright del `LICENSE`, y el nombre interno del proyecto en Gradle. Todo eso ahora dice **Enhanced-vq**.
- No se tocaron identificadores técnicos internos (nombres de paquete/clases Java, el id interno del mod, claves de datos guardados) para no arriesgar romper compatibilidad con mundos existentes — eso no es "el nombre del proyecto", es su cimiento técnico.

---

## ⚠️ Antes de publicar

No fue posible compilar el proyecto en este entorno de desarrollo — todo el trabajo se hizo a mano contra documentación oficial, sin un compilador real al lado. En el camino aparecieron y se corrigieron **3 errores de compilación reales** (dos relacionados con renombres de la API entre versiones — `CommandSourceStack#hasPermission`, `RegistryAccess` para leer registros de encantamientos/pociones, y `ResourceKey#location→identifier` — y uno de clases de herramientas que ya no existen, reemplazadas por etiquetas `ItemTags`). El código ya pasó por varias rondas de compilación real y quedó corregido en cada una.

Aun así, para esta versión en particular, antes de subirla a Modrinth:

1. Confirmar que compila limpio una vez más — no hay garantía de que no quede algún error suelto que todavía no salió a la luz.
2. Probar el libro a fondo en el juego: las 6 pestañas, el creeper apareciendo al pasar página, el cartel de misión completada, y sobre todo **las 64 misiones de Mago completándose solas** al brebar pociones/encantar objetos (es la parte más nueva y menos probada en el juego real).
3. Confirmar que el mapeo de colores de las 6 pestañas coincide con las categorías esperadas (verde=Explorador, dorado=Minero, morado=Constructor, celeste=Técnico, rosa=Mago, gris=Logros).

---

# Enhanced-vq 0.9.2 — Notas de actualización

Revisión completa del mod antes de su primera publicación en Modrinth: corrección de misiones rotas, limpieza de código sin usar, optimización de rendimiento, sistema bilingüe completo y cambio de nombre/versión.

---

## 🐛 Corrección de errores

- **"La Fortaleza Olvidada" no se podía completar.** Le faltaba por completo el disparador de detección del Stronghold — ningún jugador podía terminarla sin importar lo que hiciera. Se agregó la detección real de la estructura, con su propio seguimiento independiente del de "La Fortaleza Perdida".
- **67 de las 98 misiones mostraban una recompensa distinta a la que realmente entregaban.** Por ejemplo, "¡Inquebrantable!" prometía 32x Tablones de Roble en el libro pero en realidad entregaba 4 Lingotes de Netherite. Se corrigió el texto del libro para que coincida en todos los casos con la recompensa real que otorga el código.
  - Caso aparte: "Hacia lo Desconocido", "Jugando con Fuego" y "Bajo las Olas" prometían pociones específicas (Resistencia al Fuego / Respiración) pero el código entregaba una Poción sin ningún efecto asignado. El texto ahora dice "Poción" a secas para reflejar la realidad — si el objetivo real era que esas pociones sí tuvieran su efecto, es un ajuste aparte a considerar.
- **Las misiones "Consigue X de [mineral]" (carbón, hierro, cobre, oro, redstone, lapislázuli, diamante, Ancient Debris, esmeralda — 20 misiones en total) contaban bloques de mena rotos en vez del recurso realmente obtenido.** Esto causaba dos problemas concretos:
  - Con **Toque de Seda**, el bloque te da la mena (ej. "Mena de Hierro"), no el recurso — igual sumaba progreso aunque nunca recibieras el ítem.
  - Con **Fortuna**, puedes obtener varios ítems de un solo bloque — antes solo sumaba 1 por bloque, subestimando el progreso real.
  - Ahora se revisa cuánto se tiene en el inventario en ese momento, sin importar cómo se consiguió (minando, comerciando, saqueando un cofre).
- El HUD (rastreador fijado en pantalla) de "No Pasaremos Frío" decía "Recoge 10 de Carbón **minando**" — quedó igual de desactualizado que el bug anterior. Corregido a "Consigue 10 de Carbón", coherente con el libro y con el nuevo mecanismo.

## 🧹 Limpieza de código y archivos sin usar

- Constante `QUEST_NEW_HOME` definida pero nunca referenciada en ningún lado.
- Método `hasQuestBook()` duplicado y muerto en `QuestChestBlock.java` (la lógica real ya estaba resuelta unas líneas antes) + su import huérfano.
- Variable `synced` que se declaraba, se reasignaba una vez al mismo valor y nunca se leía.
- Dos archivos de configuración de Mixins (`homequest.mixins.json`, `homequest.client.mixins.json`) completamente vacíos — no hay ni una sola clase `@Mixin` en todo el proyecto. El del cliente ni siquiera estaba registrado en `fabric.mod.json`.
- Textura huérfana `quest_book_full.png`, no referenciada en ningún json ni código.
- 3 carpetas vacías, incluyendo `assets/homequest/loot_tables/blocks` — mal ubicada (las loot tables van en `data/`, no en `assets/`).

## ⚡ Rendimiento y confiabilidad del guardado

- **Antes:** cada bloque roto o colocado, para cada jugador, releía y reescribía el archivo de datos completo de **todos** los jugadores, de forma síncrona. Con varios jugadores o construcción rápida, esto podía causar microlag perceptible en servidores reales.
- **Ahora:** los datos se cargan una sola vez (al iniciar el servidor/mundo) y se mantienen en caché en memoria. El guardado a disco pasó a ser diferido: se marca "hay cambios pendientes" al instante (sin tocar el disco) y se escribe realmente cada ~2 segundos, solo si hubo cambios.
- Redes de seguridad agregadas para no perder progreso: guardado automático al desconectarse cada jugador y guardado final al apagar el servidor (o cerrar el mundo en singleplayer).
- **Escritura atómica:** se escribe primero a un archivo temporal y recién al final se reemplaza el real. Antes, si el servidor se colgaba a mitad de una escritura, el archivo de datos de **todos** los jugadores podía quedar corrupto; ahora eso ya no puede pasar.

## 🌐 Sistema bilingüe (Español / English) — nuevo

- Ventana de selección de idioma la primera vez que un jugador abre el Libro de Quests en un mundo. La elección se guarda por jugador en el servidor, así que no vuelve a preguntar.
- Traducción completa del **libro**: las 98 misiones (nombre, descripción, recompensa) y toda la interfaz — pestañas (Principal/Explorador/Minero/Constructor/Técnico), botones ("Obtener" / "Fijar en HUD") y estados ("En progreso" / "Completada" / "Reclamada").
- Traducción completa del **HUD** (rastreador de misión fijada en pantalla): las 97 misiones genéricas, el checklist especial de "Primera Casa" (Cofre/Cama/Mesa de Trabajo/Horno), y las barras de progreso dinámicas (ej. "Bloques: 34/250").
- Botón de opciones (⚙) en la esquina inferior derecha del libro, dentro del marco rojo de la página y sin superponerse con los botones existentes, para volver a abrir el selector de idioma en cualquier momento por si alguien se equivocó.
- Quedó pendiente (fuera del alcance de esta revisión): los mensajes de chat que aparecen al completar una misión siguen solo en español.

## 🏷️ Renombrado

- Nombre del mod: `Enhanced Vanilla Quests` → **`Enhanced-vq`**.
- Versión: `0.9.0` → **`0.9.2`**.
- `archives_base_name` en Gradle actualizado (`enhanced-vanilla-quests` → `enhanced-vq`), así el `.jar` final se genera como `enhanced-vq-0.9.2.jar`.
- El ID interno del mod (`homequest`), el paquete Java (`com.homequest`) y el namespace de assets se dejaron sin tocar a propósito — cambiarlos habría sido una modificación mucho más riesgosa (rompe compatibilidad con mundos ya probados) e innecesaria para un simple cambio de nombre público.

## 📄 Otros

- Se agregó el archivo `LICENSE` (MIT) que faltaba — `fabric.mod.json` ya declaraba licencia MIT y `build.gradle` ya esperaba este archivo, pero no existía.

---

## ⚠️ Antes de publicar

No fue posible compilar el proyecto en este entorno (sin acceso al Maven de Fabric). Todos los cambios se verificaron manualmente (balance de código, referencias cruzadas, consistencia de nombres), pero se recomienda:

1. Correr `./gradlew build` y confirmar que compila sin errores.
2. Probar en el juego: abrir el libro, elegir cada idioma, fijar una misión al HUD, y completar al menos una misión de cada categoría (mineral, construcción, redstone) para confirmar que el progreso se registra bien.
3. Revisar visualmente que el botón de opciones (⚙) se vea bien posicionado contra la textura real del libro.

### Checklist para Modrinth (reglas vigentes de la plataforma)

- La descripción del proyecto en Modrinth necesita traducción al inglés (a menos que el mod se declare exclusivo para hispanohablantes) — esto es solo la descripción de la página del proyecto, no el mod en sí.
- Marcar manualmente **Fabric API** como dependencia requerida en la sección de Dependencias al subir la versión — `fabric.mod.json` no se detecta automáticamente ahí.
- El título del proyecto debe ser solo el nombre, sin agregar versión ni loader (ej. no "[Fabric] Enhanced-vq 0.9.2").
