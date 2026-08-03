# Enhanced-vq 0.9.3 — Update Notes

Full redesign of the book HUD (new 6-tab artwork), a new "Mage" tab with 64 potion and enchantment quests, an on-screen quest-completed notification, and several quality-of-life improvements.

---

## 🎨 Book HUD — new artwork

- Book background replaced with the new design (6 color tabs instead of 5).
- Tabs reordered: Explorer/Miner/Builder/Engineer stay the same, **Mage** (animated enchanted book) was added as the second-to-last tab, and **Achievements** (animated golden apple) moved to the last slot.
- Left page (quest list) enlarged to use the full red border frame, matching what the right page already did — it used to have far more margin than necessary and showed fewer rows than actually fit.
- Each quest's status "ticket" (previously a text ✔/○) is now a creeper face: gray while incomplete, colored as soon as it's completed.
- New animated icons for the 4 categories (compass/pickaxe/block/redstone) and Mage (enchanted book), extracted from reference gifs and tuned so the animation plays at a natural speed instead of looking sped up.
- Hover tooltips on every tab, showing progress (e.g. "Explorer (4/12)").

## 🆕 "Mage" tab — new content (64 quests)

- Get a Brewing Stand and an Enchanting Table (the latter moved from the Main tab, no longer duplicated).
- 16 potion quests (one per base effect, not counting level/duration/splash-lingering variants).
- 41 enchantment quests (one per type, not counting level) — the 2 curses were intentionally excluded.
- 5 enchanted-gear milestones: 1 armor piece, a full set, a pickaxe, a sword, an axe.
- Rewards scaled by how hard each thing actually is to get in-game (not by enchantment level number): common materials (books, bookshelves, lapis lazuli) for the easy ones, and genuinely pre-enchanted gear (Sharpness IV sword, Efficiency IV pickaxe and axe, Protection IV armor, Soul Speed boots, Swift Sneak leggings) for the hardest achievements.
- **Automatic detection included**: all 64 quests complete themselves through normal play — having the matching potion or any item with the matching enchantment in your inventory (or worn) marks the quest ready to claim. No extra steps or commands needed.

## 🐛 Creeper (Completed tab) — polish

- Now has its own icon (no longer relies on the background art, which doesn't include it in the new design).
- Slides up into view in 6 steps over 1 second, right after the page-flip animation finishes — simulating it emerging from behind the page instead of popping in all at once.
- Fixed size and position a couple of times during development (it was bigger than the house icon, and sitting in the transparent gap between the house and the cover instead of on the cover itself).

## 🔔 "Quest Completed!" notification — new

- Banner in the top-left corner of the screen (HUD, not inside the book) every time a quest is completed.
- Sweeps in left-to-right, holds fully visible for 3 seconds, then closes like a curtain toward the center.
- If several quests complete at once, they queue up and show one after another — none get skipped or overwritten.
- Guarded against flooding the screen with banners when joining a world with old progress (only fires for quests newly completed after the session's first sync).

## ⚙️ Quality of life

- "Claim All" button on the Completed tab, to collect every pending reward in one click.
- Sounds when claiming a reward and when pinning/unpinning a quest (previously only the page-flip had sound).
- "Hide completed" toggle on each category, so you don't have to scroll past what you've already done.

## 🏷️ Rebranding

- Several loose references to the project's old name ("HomeQuest") remained in player-visible text: the chat/console message prefix, the author field in `fabric.mod.json`, the `LICENSE` copyright line, and the internal Gradle project name. All of those now read **Enhanced-vq**.
- Internal technical identifiers (Java package/class names, the mod's internal id, saved-data keys) were left untouched to avoid risking compatibility with existing worlds — those aren't "the project's name," they're its technical foundation.

---

## ⚠️ Before publishing

It wasn't possible to compile the project in this development environment — all the work was done by hand against official documentation, with no real compiler on hand. Along the way, **3 real compile errors** came up and got fixed (two from API renames between versions — `CommandSourceStack#hasPermission`, `RegistryAccess` for reading enchantment/potion registries, and `ResourceKey#location→identifier` — and one from tool item classes that no longer exist, replaced with `ItemTags`). The code has already been through several rounds of real compilation feedback and was corrected each time.

Even so, for this version specifically, before uploading to Modrinth:

1. Confirm it compiles clean one more time — there's no guarantee some other error hasn't slipped through unnoticed.
2. Test the book thoroughly in-game: all 6 tabs, the creeper appearing on page-flip, the quest-completed banner, and especially **the 64 Mage quests completing themselves** by brewing potions/enchanting items (that's the newest, least battle-tested part in real gameplay).
3. Confirm the 6 tabs' color mapping matches the expected categories (green=Explorer, gold=Miner, purple=Builder, teal=Engineer, pink=Mage, gray=Achievements).

---

# Enhanced-vq 0.9.2 — Changelog

Full review of the mod ahead of its first Modrinth release: fixed broken quests, cleaned up unused code, optimized save performance, added a complete bilingual system, and updated the name/version.

---

## 🐛 Bug Fixes

- **"The Forgotten Fortress" quest could never be completed.** It was missing its Stronghold detection trigger entirely — no player could finish it no matter what they did. Added real structure detection for it, tracked independently from "The Lost Fortress."
- **67 of the 98 quests displayed a different reward than what they actually granted.** For example, "Unbreakable!" promised 32x Oak Planks in the book but actually granted 4 Netherite Ingots. Fixed the book text so it matches the real reward granted by the code in every case.
  - Special case: "Into the Unknown," "Playing with Fire," and "Under the Waves" promised specific potions (Fire Resistance / Water Breathing), but the code was granting a Potion with no effect assigned. The text now just says "Potion" to reflect reality — if the original intent was for those potions to actually carry their effect, that's a separate fix worth considering.
- **The "Get X of [resource]" quests (coal, iron, copper, gold, redstone, lapis lazuli, diamond, Ancient Debris, emerald — 20 quests total) counted ore blocks mined instead of the resource actually obtained.** This caused two concrete problems:
  - With **Silk Touch**, the block gives you the ore (e.g. "Iron Ore"), not the resource itself — progress still counted even though the player never received the item.
  - With **Fortune**, you can get several items from a single block — previously it only added 1 per block, undercounting real progress.
  - Now it checks how much you currently have in your inventory, regardless of how you got it (mining, trading, looting a chest).
- The HUD (on-screen pinned quest tracker) for "Staying Warm" said "Collect 10 Coal **by mining**" — same outdated wording as the bug above. Fixed to "Get 10 Coal," consistent with the book and the corrected mechanic.

## 🧹 Dead Code & Unused File Cleanup

- `QUEST_NEW_HOME` constant defined but never referenced anywhere.
- Duplicate, dead `hasQuestBook()` method in `QuestChestBlock.java` (the real logic was already handled a few lines above) + its orphaned import.
- `synced` variable that was declared, reassigned once to the same value, and never read.
- Two empty Mixin config files (`homequest.mixins.json`, `homequest.client.mixins.json`) — there isn't a single `@Mixin` class anywhere in the project. The client one wasn't even registered in `fabric.mod.json`.
- Orphaned `quest_book_full.png` texture, not referenced by any json or code.
- 3 empty folders, including a misplaced `assets/homequest/loot_tables/blocks` — loot tables belong under `data/`, not `assets/`.

## ⚡ Save Performance & Reliability

- **Before:** every single block broken or placed, for every player, re-read and rewrote the **entire** data file for **all** players, synchronously. With several players or fast building, this could cause noticeable microlag on real servers.
- **Now:** data is loaded once (when the server/world starts) and kept in an in-memory cache. Saving became deferred: a "changes pending" flag is set instantly (no disk access), and the actual write happens roughly every 2 seconds, only if something changed.
- Added safety nets so progress is never lost: auto-save when each player disconnects, and a final save when the server shuts down (or the world closes in singleplayer).
- **Atomic writes:** data is now written to a temporary file first, then swapped in at the very end. Previously, if the server crashed mid-write, the data file for **every** player could end up corrupted; that's no longer possible.

## 🌐 Bilingual System (Spanish / English) — New

- A language selection window appears the first time a player opens the Quest Book in a world. The choice is saved per player on the server, so it won't ask again.
- Full translation of the **book**: all 98 quests (name, description, reward) plus the entire UI — tabs (Main/Explorer/Miner/Builder/Engineer), buttons ("Claim" / "Pin to HUD"), and statuses ("In progress" / "Completed" / "Claimed").
- Full translation of the **HUD** (on-screen pinned quest tracker): all 97 generic quests, the special "First Home" checklist (Chest/Bed/Crafting Table/Furnace), and the dynamic progress bars (e.g. "Blocks: 34/250").
- An options button (⚙) in the bottom-right corner of the book, kept inside the page's red border and never overlapping the existing buttons, to reopen the language selector at any time in case someone picked the wrong one.
- Left out of this pass (out of scope for now): the chat messages that appear when completing a quest are still Spanish-only.

## 🏷️ Renaming

- Mod name: `Enhanced Vanilla Quests` → **`Enhanced-vq`**.
- Version: `0.9.0` → **`0.9.2`**.
- Updated Gradle's `archives_base_name` (`enhanced-vanilla-quests` → `enhanced-vq`), so the built `.jar` is now `enhanced-vq-0.9.2.jar`.
- The internal mod ID (`homequest`), Java package (`com.homequest`), and asset namespace were deliberately left untouched — changing those would have been a much riskier change (breaks compatibility with worlds already tested on it) and unnecessary for a simple public-facing rename.

## 📄 Other

- Added the missing `LICENSE` file (MIT) — `fabric.mod.json` already declared an MIT license and `build.gradle` already expected this file, but it didn't exist.

---

## ⚠️ Before Publishing

This project couldn't be compiled in this environment (no access to Fabric's Maven). Every change was verified manually (brace/code balance, cross-references, naming consistency), but it's recommended to:

1. Run `./gradlew build` and confirm it compiles without errors.
2. Test in-game: open the book, try both languages, pin a quest to the HUD, and complete at least one quest from each category (resource, building, redstone) to confirm progress is tracked correctly.
3. Visually check that the options button (⚙) looks properly positioned against the real book texture.

### Modrinth Checklist (current platform rules)

- Your Modrinth project description needs an English translation (unless the mod is explicitly marked as exclusive to Spanish speakers) — this is just the project page description, not the mod's in-game text.
- Manually mark **Fabric API** as a required dependency in the Dependencies section when uploading the version — `fabric.mod.json` isn't auto-detected there.
- The project title should be just the name, without appending version or loader info (e.g. not "[Fabric] Enhanced-vq 0.9.2").
