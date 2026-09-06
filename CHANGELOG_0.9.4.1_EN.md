# Enhanced-vq 0.9.4.1 — Bugfix Patch

A patch focused entirely on polishing 0.9.4: book UI (borders, cut-off text, alignment) and Bestiary content. No new quests or rewards.

---

## 🖼️ Book UI

- **Text touching the red border**: each tab's title, the "« Menu" link, the Bestiary/Progress titles, and the page indicator ("1-11/20") no longer touch the decorative frame — now spaced neatly, neither stuck to it nor floating too far away.
- **Titles and page counters now centered**: they used to sit flush left with unused space to the right; they now make use of the full page width (affects every quest list, Menu, Progress, and Bestiary).
- **Misaligned clicks**: after the spacing changes above, click detection on quest list rows and Bestiary rows had drifted a few pixels away from what was actually drawn — fixed so both match exactly again.
- **Cut-off "Common / Special / Bosses" tabs**: the highlighted tab (bold) overflowed its box because the layout math didn't account for bold text being wider — none of the three get cut off anymore, whichever is selected.
- **Long names cut off in the Bestiary list** (e.g. "Elder Gu..."): they now wrap onto two lines instead of being truncated with "...".
- **Bestiary illustrated detail page**: widened the text column — long names like "Zombified Piglin" no longer force a mid-word break.
- **Mid-letter word breaks** (e.g. "Zombificad" + a lone "o"): the line-wrapping system now avoids leaving a single orphaned letter when splitting a very long word.
- **Swapped Progress page icons**: the animated golden apple showed up next to "Main" instead of "Minecraft Achievements" (and vice versa) — fixed; "Main" now has its own icon.
- The last row of any quest list can no longer overlap the page indicator below it.

## 📖 Bestiary content

- **Reviewed writing and translation** across all 44 Bestiary entries (description, trivia, and tip) in neutral Spanish.
- Fixed two mistranslated Spanish names: **Piglin Zombificado → Piglin Zombie**, **Pez de Plata → Lepisma** (also updated in the Warrior quest names and trophies that referenced them).
- Rewrote the Skeleton's trivia, which mixed up game mechanics in a confusing way.
- Combat tips are now unique per entry instead of being shared between mobs with the same underlying type — Baby Zombie, Zombie Horseman, and Chicken Jockey no longer repeat the regular Zombie's tip.
- English translations for description, trivia, and tips updated to match the corrected Spanish text.

---

*A pure bugfix and UI-polish patch on top of 0.9.4 — no new content added.*
