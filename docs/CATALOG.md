# TweakOS — каталог моделей

Папка: `config/tweakos/catalog/<id>/`

## Структура одной модели

```
my_cap/
  meta.json
  preview.png              # рендер для меню (делаешь сам)
  model.json               # опционально — замена модели
  textures/
    blue.png
    green.png
  previews/
    blue.png               # опционально — превью цвета
    green.png
```

## meta.json

```json
{
  "id": "my_cap",
  "name": "My Cap",
  "category": "hats",
  "replacePath": "assets/minecraft/models/item/leather_helmet.json",
  "replaceTexture": "assets/minecraft/textures/models/armor/leather_layer_1.png",
  "defaultVariant": "blue",
  "variants": [
    {
      "id": "blue",
      "name": "Blue",
      "color": "#4A7CFF",
      "texture": "textures/blue.png",
      "preview": "previews/blue.png"
    },
    {
      "id": "green",
      "name": "Green",
      "color": "#5BD67A",
      "texture": "textures/green.png",
      "preview": "previews/green.png"
    }
  ]
}
```

Категории: `hats` (шапки), `hand` (косметика на руку).

## Логика work_pack

1. При входе на сервер мод клонирует серверный RP в `config/tweakos/work_pack/`.
2. Apply копирует `model.json` / текстуру варианта в этот work_pack по `replacePath` / `replaceTexture`.
3. work_pack стоит выше серверного в приоритете — игра видит твои модели.

## Добавление из игры

Кнопка **+** → положи файлы в `config/tweakos/inbox/` → укажи `replacePath` (путь модели с серверного RP).

## Приоритет паков

Кнопка шестерёнки: чем выше строка, тем сильнее пак. `Server resource pack` можно двигать вверх/вниз.
