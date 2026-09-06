# Enhanced-vq 0.9.4.1 — Parche de corrección

Parche enfocado 100% en pulir la 0.9.4: interfaz del libro (bordes, textos cortados, alineación) y contenido del Bestiario. Sin misiones ni recompensas nuevas.

---

## 🖼️ Interfaz del libro

- **Textos pegados al borde rojo**: el título de cada pestaña, el link "« Menú", el título de Bestiario/Progreso y el indicador de página ("1-11/20") ya no tocan el marco decorativo — separados por un margen prolijo, ni pegados ni flotando lejos.
- **Títulos y numeración de página centrados**: antes quedaban pegados a la izquierda dejando aire de sobra sin usar; ahora aprovechan todo el ancho de la página (afecta a todas las listas de misiones, Menú, Progreso y Bestiario).
- **Clicks desalineados**: al reordenar los elementos de arriba, la detección de click en las filas de la lista de misiones y del Bestiario había quedado corrida unos píxeles del dibujo real — corregido para que ambos vuelvan a coincidir exactamente.
- **Pestañas "Común / Especial / Jefes" cortadas**: la pestaña resaltada (en negrita) se salía de su recuadro porque el cálculo de espacio no tenía en cuenta que el texto en negrita ocupa más ancho — ahora ninguna de las tres se corta, sea cual sea la seleccionada.
- **Nombres largos cortados en la lista del Bestiario** (ej. "Elder Gu..."): ahora se acomodan en dos líneas en vez de truncarse con "...".
- **Ficha ilustrada del Bestiario**: se ensanchó la columna de texto — nombres largos como "Zombified Piglin" ya no fuerzan un corte a mitad de palabra.
- **Corte de palabra en mitad de una letra** (ej. "Zombificad" + "o" solo): el sistema que arma las líneas de texto ahora evita dejar una única letra huérfana al partir una palabra muy larga.
- **Íconos de la página de Progreso invertidos**: la manzana dorada animada aparecía junto a "Main" en vez de junto a "Minecraft Achievements" (y viceversa) — corregido; "Main" ahora tiene su propio ícono.
- La última fila de cada lista de misiones ya no puede llegar a superponerse con el indicador de página de abajo.

## 📖 Contenido del Bestiario

- **Traducción y redacción revisadas** en las 44 fichas del Bestiario (descripción, curiosidad y consejo), en español neutro — sin voseo ni modismos regionales.
- Corregidos dos nombres que no correspondían en español: **Piglin Zombificado → Piglin Zombie**, **Pez de Plata → Lepisma** (actualizado también en los nombres de misión y trofeos de Guerrero que los mencionaban).
- Reescrita la curiosidad del Esqueleto, que mezclaba mecánicas del juego de forma confusa.
- Los "Consejos" de combate ahora son independientes por ficha en vez de compartirse entre mobs con el mismo tipo interno — Bebé Zombi, Jinete Zombie y Chicken Jockey ya no repiten el mismo consejo del Zombi común.
- Traducciones al inglés de descripción, curiosidad y consejo actualizadas para que coincidan con el texto en español corregido.

---

*Parche exclusivamente de corrección de errores e interfaz sobre la 0.9.4 — no agrega contenido nuevo.*
