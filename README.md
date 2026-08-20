# AyanamiCosmetics

Клиентский мод Minecraft **Java Edition 1.12.2** / Forge **14.23.5.2860**.

Локальный косметический Resource Pack перекрывает совпадающие файлы серверного Resource Pack через стандартный `ResourceManager` (textures, models, blockstates, sounds, fonts, lang, shaders и др.). Серверный пак не удаляется и не подменяется на этапе подтверждения загрузки.

## Принцип

В 1.12.2 серверный RP добавляется последним и перекрывает выбранные игроком паки. Мод ставит в слот серверного пака обёртку `ResourcePackOverride`: сначала пользовательский ZIP, иначе серверный файл.

Reflection нужен только чтобы записать `IResourcePack` в приватное поле `ResourcePackRepository.serverResourcePack` — публичного setter для произвольного пака нет. Download/accept серверного RP не затрагивается.

## Инструкция

1. Установить **Java 8** (`java -version` → 1.8.x).
2. Открыть папку проекта `AyanamiCosmetics` (уже содержит ForgeGradle 3 / wrapper из MDK 1.12.2-14.23.5.2860).
3. При необходимости сверить/обновить файлы проекта.
4. Подготовка (для FG3 обычно достаточно build; `setupDecompWorkspace` может отсутствовать):
   ```bash
   ./gradlew build
   ```
   Если используете старый FG2-workspace: `./gradlew setupDecompWorkspace`, затем `./gradlew build`.
5. Сборка: `./gradlew build`.
6. Скопировать `build/libs/AyanamiCosmetics-1.0.0.jar` в `.minecraft/mods/`.
7. Положить `AyanamiCosmetics.zip` (или другой косметический ZIP) в `.minecraft/resourcepacks/`.
8. Запустить Minecraft с Forge 1.12.2 (серверу мод **не нужен**).
9. Подключиться к серверу — серверный RP загрузится стандартно.
10. Нажать **O** → **Enable Override** (при необходимости выбрать пак через **Select Pack**).

## GUI

- Клавиша по умолчанию: **O** (Controls → AyanamiCosmetics)
- Enable/Disable Override, Current pack, Select Pack, Reload Resources
- Статус: Override ON/OFF, Server Resource Pack LOADED/NOT LOADED

## Конфиг

`config/ayanamicosmetics.cfg` — `overrideEnabled`, `selectedPackName` (по умолчанию `AyanamiCosmetics.zip`).
