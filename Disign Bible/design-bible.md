# MDAOPay — Design Bible

> **Версия:** 1.0.0
> **Дата:** Июнь 2026
> **Источник истины:** `tokens.css` + `components.css` + `transitions.css` + `notif.js`
> **Прототип:** 26 экранов в `/download/` (см. `index.html`)

---

## Содержание

1. [Foundations](#1-foundations)
2. [Components](#2-components)
3. [Patterns](#3-patterns)
4. [Motion](#4-motion)
5. [Screens](#5-screens)
6. [Voice & Copy](#6-voice--copy)
7. [Accessibility](#7-accessibility)
8. [Theming](#8-theming)

---

# 1. Foundations

## 1.1 Бренд MDAOPay

**Миссия.** Дать каждому полный контроль над своими средствами — без посредников, без компромиссов, без скрытых комиссий.

**Тон.** Уверенный, тёплый, технически грамотный. Не инфантильный, не корпоративный. Пользователь чувствует, что ему доверяют и не водят за руку.

**Ценности (отражаются в UI):**
- **Прозрачность.** Все комиссии показаны до отправки. Нет скрытых шагов.
- **Скорость.** Анимации быстрые, переходы мгновенные. Никаких лишних экранов.
- **Безопасность.** Backup подсвечивается с первого дня. Антифишинг включён по умолчанию.
- **Эстетика.** Soft neomorphic shadows, аккуратная типографика, никаких кислотных цветов.

## 1.2 Логотип

- **Символ:** `◈` (ромб с точкой) — используется в hero, splash, лого-блоках
- **Логотип:** `MDAOPay` — `MDAO` цветом `--text`, `Pay` цветом `--accent`
- **Градиент лого-блоков:** `linear-gradient(135deg, #FF6B00, #FF9A4D)`
- **Скругление лого-плиток:** 22–24px (var(--r-2xl))
- **Тени лого:** `0 10px 24px -6px rgba(255,107,0,0.5), inset 0 2px 4px rgba(255,255,255,0.4), inset 0 -3px 6px rgba(0,0,0,0.15)`

**Не использовать:**
- ❌ Логотип на кислотных фонах
- ❌ Деформацию пропорций
- ❌ Изменение цвета `Pay` (всегда `--accent`)

## 1.3 Цветовая система

### Базовые токены (3 темы)

| Token | Dark | Light | AMOLED |
|-------|------|-------|--------|
| `--bg` | `#0A0A0F` | `#E8EBF1` | `#000000` |
| `--card` | `#1A1A22` | `#FBFCFE` | `#0B0B0D` |
| `--surface` | `#121218` | `#FFFFFF` | `#050505` |
| `--tile` | `#23232C` | `#E4E8EE` | `#151518` |
| `--text` | `#FFFFFF` | `#0A0A0F` | `#FFFFFF` |
| `--text2` | `#7E8089` | `#6B7280` | `#5A5A60` |
| `--text3` | `#4D4F58` | `#9CA3AF` | `#3A3A40` |
| `--border` | `#26262F` | `#DCE0E8` | `#1C1C1C` |
| `--soft-border` | `rgba(255,255,255,0.04)` | `#D6DAE2` | `#161616` |

### Бренд-акцент (общий для всех тем)

| Token | Значение | Usage |
|-------|----------|-------|
| `--accent` | `#FF6B00` | Основной акцент, CTA, активные состояния |
| `--accent-press` | `#E55F00` | Pressed state для primary кнопок |
| `--accent-soft` | `rgba(255,107,0,0.10)` | Фоны для accent-иконок, чипы |

### Альтернативные акценты (Appearance screen)

| Имя | Token | Hex |
|-----|-------|-----|
| Оранжевый (default) | `--accent` | `#FF6B00` |
| Синий | `--accent-blue` | `#2D7FF9` |
| Зелёный | `--accent-green` | `#00B377` |
| Фиолетовый | `--accent-purple` | `#7B4DFF` |
| Розовый | `--accent-pink` | `#F94D9E` |

### Семантические цвета

| Token | Hex | Soft | Usage |
|-------|-----|------|-------|
| `--success` | `#00D68F` | `rgba(0,214,143,0.12)` | Подтверждения, входящие tx, OK-статусы |
| `--danger` | `#FF4D6D` | `rgba(255,77,109,0.12)` | Ошибки, исходящие tx, danger zone |
| `--warning` | `#FFB300` | `rgba(255,179,0,0.12)` | Предупреждения, pending, backup warning |

### Overlay-цвета (без blur по дизайну)

| Token | Dark | Light | AMOLED |
|-------|------|-------|--------|
| `--overlay-dim` | `rgba(0,0,0,0.4)` | `rgba(20,22,32,0.32)` | `rgba(0,0,0,0.55)` |
| `--overlay-strong` | `rgba(0,0,0,0.6)` | `rgba(20,22,32,0.55)` | `rgba(0,0,0,0.8)` |

## 1.4 Типографика

**Шрифты:**
- `--font-sans` — `-apple-system, BlinkMacSystemFont, 'Inter', 'SF Pro Display', sans-serif` (основной)
- `--font-mono` — `'SF Mono', 'JetBrains Mono', 'Roboto Mono', ui-monospace, Menlo, monospace` (адреса, суммы, хэши)

### Type scale

| Token | Size | Weight | Usage |
|-------|------|--------|-------|
| `--fs-caption` | 10px | 600 | Tiny labels, units, list-item labels |
| `--fs-meta` | 11px | 600 | Secondary meta, section headers |
| `--fs-label` | 12px | 600 | Section titles, sub-text, badges |
| `--fs-body` | 13px | 500 | Default body text |
| `--fs-body-lg` | 14px | 600 | Emphasized body, list-item titles |
| `--fs-title` | 15px | 600 | Card titles, list-item primary |
| `--fs-h3` | 18px | 700 | Sub-headings, modal titles |
| `--fs-h2` | 20px | 700 | Screen titles (compact) |
| `--fs-h1` | 24px | 700 | Large screen titles, greeting |
| `--fs-display` | 36px | 700 | Balance, hero numbers |
| `--fs-mega` | 44px | 700 | Result overlays, amount inputs |

### Line heights

| Token | Value | Usage |
|-------|-------|-------|
| `--lh-tight` | 1.1 | Display numbers |
| `--lh-snug` | 1.2 | Titles |
| `--lh-normal` | 1.4 | Default |
| `--lh-relaxed` | 1.5 | Body text, descriptions |

### Letter spacing

| Token | Value | Usage |
|-------|-------|-------|
| `--ls-tight` | -0.5px | H1, display |
| `--ls-snug` | -0.2px | H2, H3, button text |
| `--ls-normal` | 0 | Body |
| `--ls-wide` | 0.2px | Labels |
| `--ls-wider` | 0.8px | Meta labels |
| `--ls-uppercase` | 1px | Section headers (uppercase) |

### Правила

- ✅ Все суммы, адреса, хэши, commissions — `--font-mono`
- ✅ Numbers в балансах — `letter-spacing: -1.2px` для крупных, `-0.3px` для мелких
- ✅ Section headers — `text-transform: uppercase; letter-spacing: 1px`
- ❌ Не использовать `--font-mono` для основного текста
- ❌ Не уменьшать letter-spacing у body-текста

## 1.5 Иконография

**Стиль:** Line icons, stroke-width 2, скруглённые концы (`stroke-linecap: round; stroke-linejoin: round`).

**Размеры:**
- `--icon-sm` — 18px (в кнопках, list-items)
- `--icon-md` — 22px (bottom nav, service icons)
- `--icon-lg` — 28px (sheet close)
- `--icon-xl` — 44px (empty states)

**Цвета иконок:**
- По умолчанию — `stroke: currentColor`
- В icon-btn: `stroke: var(--text2)` → `:hover` `stroke: var(--text)`
- В accent-иконках (list-items): `stroke: var(--accent)`
- В success/error/warning: соответствующий семантический цвет

**SVG-стиль:**
```html
<svg viewBox="0 0 24 24" style="stroke:currentColor;stroke-width:2;fill:none;stroke-linecap:round;stroke-linejoin:round;">
  <path d="..."/>
</svg>
```

## 1.6 Тени и Elevation

5 уровней теней, использующихся консистентно по всему приложению.

| Token | Назначение | Характер |
|-------|------------|----------|
| `--shadow-raise` | Поднятые карточки (token cards на верху стека, hero blocks) | Глубокая внешняя + inset top highlight |
| `--shadow-soft` | Стандартные карточки, кнопки, list-items | Средняя внешняя + inset top |
| `--shadow-inset` | Pressed-состояния, инпуты, tile-поверхности | Только внутренние тени |
| `--shadow-press` | Active state для кнопок и тайлов | Глубокая внутренняя, без внешней |
| `--shadow-glow` | Тонкий top-highlight на карточках | Только `inset 0 1px 0 rgba(255,255,255,...)` |

### Правила комбинирования
- Карточка = `--shadow-soft, --shadow-glow` (внешняя + блеск)
- Pressed карточка = `--shadow-press` (заменяет обе)
- Инпут = `--shadow-inset` (вдавленная поверхность)
- Sheet = `0 -8px 50px rgba(0,0,0,0.20)` + `--shadow-glow`

## 1.7 Скругления (Radii)

| Token | Value | Usage |
|-------|-------|-------|
| `--r-xs` | 4px | Декоративные углы |
| `--r-sm` | 8px | Trend badges, small chips |
| `--r-md` | 12px | Icon-boxes, small buttons |
| `--r-lg` | 14px | Inputs, list-items rounded corners, toggle icons |
| `--r-xl` | 16px | Tile buttons, action buttons |
| `--r-2xl` | 20px | Blocks (Contacts/Services), sheets sections |
| `--r-3xl` | 24px | Hero blocks, profile cards |
| `--r-4xl` / `--r-card` | 28px | Token cards, sheet tops |
| `--r-pill` | 999px | Capsules, pills, toggles |

## 1.8 Spacing system

4-based grid. Все отступы кратны 4 (или близки).

| Token | Value | Usage |
|-------|-------|-------|
| `--sp-0` | 0 | — |
| `--sp-1` | 2px | Минимальные зазоры |
| `--sp-2` | 4px | Icon-text gap |
| `--sp-3` | 6px | Tight element spacing |
| `--sp-4` | 8px | Default small spacing |
| `--sp-5` | 10px | Block padding (compact) |
| `--sp-6` | 12px | List-item padding, block margin |
| `--sp-8` | 16px | Standard padding |
| `--sp-10` | 20px | Card padding |
| `--sp-12` | 24px | Hero padding |
| `--sp-16` | 32px | Section spacing |
| `--sp-20` | 40px | Large screen padding |
| `--sp-24` | 48px | Empty state padding |

## 1.9 Sizing

| Token | Value | Usage |
|-------|-------|-------|
| `--tap-min` | 44px | Минимальный touch target (WCAG) |
| `--phone-w` | 390px | Baseline mobile width |
| `--phone-h` | 844px | Baseline mobile height |

## 1.10 Durations

| Token | Value | Usage |
|-------|-------|-------|
| `--d-fast` | 0.18s | Micro-interactions |
| `--d-quick` | 0.25s | Color transitions, small fades |
| `--d-base` | 0.35s | Standard transitions, fades |
| `--d-page` | 0.5s | Page push/pop |
| `--d-sheet` | 0.55s | Bottom sheet slide |
| `--d-result` | 0.7s | Result overlays |

---

# 2. Components

> Все компоненты определены в `components.css`. Ниже — анатомия, состояния, do/don't для каждого.

## 2.1 Phone Frame

**Анатомия:**
- Контейнер 390×844px
- Border-radius: 44px
- Box-shadow: 3 слоя (drop + 2 ring shadows для "bezel")
- Padding: `14px 18px 6px`
- `overflow: hidden`

**Responsive:** На экранах ≤420px — full-screen, без рамки.

## 2.2 Topbar

Используется на всех sub-экранах (настройки, send, receive и т.д.).

**Анатомия:**
```
[← back] [Title]                    [action?]
```

- Back button: 42×42px, tile-background, `--shadow-soft, --shadow-inset`
- Title: `--fs-h2`, `--fw-bold`, `--ls-tight`, truncate при переполнении
- Action button (optional): такой же как back

**States:**
- `:active` — `transform: scale(0.94); --shadow-press`

## 2.3 Header (main screen)

Вариант topbar для главного экрана — greeting + icon buttons.

**Анатомия:**
```
Привет,                    [🔔] [⚙️]
Антон
@crazy-cherry
```

- Hello: `--fs-label`, `--text2`
- Name: `--fs-h1`, `--fw-bold`
- Nick: `--fs-body`, `--font-mono`, `--accent`
- Icon buttons: 42×42px с badge (9×9px, accent, glow)

## 2.4 Buttons

### Primary
- Background: `--accent`
- Color: `#fff`
- Shadow: `0 8px 18px -4px rgba(255,107,0,0.4)` + insets
- `:active` → `--accent-press`

### Secondary
- Background: `--tile`
- Color: `--text`
- Shadow: `--shadow-soft, --shadow-inset`

### Ghost
- Background: `transparent`
- Color: `--text2`
- `:active` → background: `--tile`, color: `--text`

### Danger
- Background: `--danger`
- Color: `#fff`
- Shadow: `0 8px 18px -4px rgba(255,77,109,0.4)`

### Sizes
| Size | Padding | Font | Min-height |
|------|---------|------|------------|
| sm | 10×16 | 13px | 38px |
| md (default) | 14×22 | 14px | 44px |
| lg | 18×28 | 18px | 54px |

### С иконками
- `icon-left` / `icon-right` — SVG 18×18px, `stroke: currentColor`

### Do / Don't
- ✅ Использовать `btn-block` для full-width
- ✅ Только одна `btn-primary` на экран
- ❌ Не комбинировать `btn-primary` и `btn-danger` рядом
- ❌ Не делать кнопку меньше `btn-sm`

## 2.5 Inputs

### Text input
- Background: `--tile`
- Border: `1px solid --soft-border`
- Padding: 14×16px
- Radius: `--r-xl` (14px)
- Shadow: `--shadow-inset`
- Focus: `border-color: --accent; background: --surface`

### Amount input (large)
- Background: `transparent`
- Font: `--font-mono`, 44px, `--fw-bold`
- Letter-spacing: -1.5px
- Text-align: center

### Input with suffix/affix
- Suffix (right): absolute positioned, `right: 12px`
- Affix (left): absolute positioned, `left: 14px`
- Input padding компенсирует

### Search input
- Same as text + search icon (16px) в affix-position

### Do / Don't
- ✅ Всегда label над input (`input-label` class)
- ✅ Label: `text-transform: uppercase; letter-spacing: 0.8px`
- ❌ Не использовать placeholder вместо label
- ❌ Не делать input без фокус-состояния

## 2.6 List Items (Settings)

Основной строительный блок настроек.

**Анатомия:**
```
[icon] [title]      [value]    [>]
       [sub]
```

- Height: ~56px (padding 14px × 2)
- Icon-box: 34×34px, `--r-md`, может быть accent/success/danger
- Title: `--fs-body-lg`, `--fw-semibold`
- Sub: `--fs-label`, `--text2`
- Value: `--fs-body`, `--text2`, right-aligned
- Chevron: 18px, `stroke: --text3`
- Border-bottom: `1px solid --border` (кроме последнего)

**Variants:**
- С toggle (вместо chevron)
- С badge (вместо value)
- С icon variants (accent / success / danger / default)

## 2.7 Toggle

- Size: 50×30px
- Background: `--tile` → on: `--accent`
- Knob: 24×24px, white, `translateX(0)` → `translateX(20px)`
- Transition: `var(--d-base) var(--spring)`

## 2.8 Radio Card

Для выбора темы, языка, крупных опций.

**Анатомия:**
```
[preview] [title]      [check]
          [sub]
```

- Padding: 14×16px
- Border: `2px solid --soft-border` → active: `2px solid --accent`
- Shadow: `--shadow-soft` → active: `+ 0 0 0 2px --accent-soft`
- Preview: 48×48px (для темы — gradient swatch)
- Check: 24×24px circle, fills accent when active

## 2.9 Slider

- Track: 6px, `--tile`, `--shadow-inset`
- Thumb: 26×26px, accent, 3px border `--bg`, glow shadow

## 2.10 Token Card

Карточка токена на главном экране. Высота 220px (cards-wrapper).

**Анатомия:**
```
[token-icon] [name]        [+2.4%]
             [network]
─────────────────────────────
1 250.50 USDT
≈ $1 250.50
─────────────────────────────
[Отправить] [Получить] [История]
```

**States:**
- Collapsed (pos-0..3): стек с translateY + scale + opacity
- Expanded: все карты одинакового размера, только offset по Y, compact header (icon 30px + balance-chip)

**Compact header (expanded):**
- Иконка 30×30px
- Balance-chip: `--fs-label`, mono, padding 4×10px, `--tile` background
- Trend badge скрыт
- Big balance-block скрыт (max-height: 0)
- Card-actions скрыты

## 2.11 Block (Contacts / Services)

Контейнер для горизонтальных списков.

- Background: `--card`
- Radius: `--r-2xl` (20px)
- Padding: 10×14px
- Shadow: `--shadow-soft, --shadow-glow`
- Header: title (`--fs-label`, uppercase) + action button (right)

**Contact item:** Avatar 42×42px + name (`--fs-caption`)
**Service item:** Icon-box (max-height 50px, aspect 1.4:1) + label

## 2.12 Bottom Nav

3 таба: Кошелёк / Фиат / Сервисы.

- Padding: 8×10px × 6px
- Gap: 6px
- Nav-item: flex:1, padding 10×14px, `--r-xl`
- Active: `background: --tile`, `box-shadow: --shadow-press, inset 0 0 0 1px --soft-border`
- Active icon: `stroke: --accent`
- Inactive icon: `stroke: --text3`

## 2.13 Sheets (Bottom sheets)

**Анатомия:**
- Position: absolute bottom
- Background: `--card`
- Radius: `--r-sheet --r-sheet 0 0` (28px top)
- Padding: `14px 22px calc(28px + env(safe-area-inset-bottom))`
- Max-height: 75vh, overflow-y: auto
- Transform: `translateY(110%)` → `.open`: `translateY(0)`
- Transition: `var(--d-sheet) var(--spring)`

**Components:**
- Handle: 38×4px, `--border`, centered, margin-bottom 18px
- Close-btn: 34×34px, top-right
- h3: `--fs-h1`, `--fw-bold`
- desc: `--fs-body`, `--text2`, `--lh-relaxed`
- rows: padding 14px 0, border-bottom `--border`

**Overlay:**
- Background: `rgba(0,0,0,0.45)` (light: `rgba(15,15,20,0.42)`)
- Transition: opacity `--d-base`
- z-index: 50 (sheet: 60)

## 2.14 Notifications (unified system)

**Архитектура:** Одна сущность `.notif`, два режима — `card` и `pill`.

### Card mode (success/error с деталями)
- Width: `calc(100% - 4px)` — на ширину контента
- Padding: 22×24px
- Gap: 18px между блоками
- Radius: 28px (morph из 50%)
- Содержимое: icon (44px) + title (`--fs-h3`) + sub (`--fs-body`) + details (amount + commission) + action button

### Pill mode (info / transient)
- Width: `max-content`
- Padding: 12×18px
- Высота: ~51px
- Radius: 999px (morph из 50%)
- Содержимое: icon (28px) + title (`--fs-body-lg`) + sub (`--fs-label`) в одну строку

### Animation: drop + morph
1. Точка падает сверху (translateY -110px → 0, scale 0.05)
2. Приземление (border-radius 50%)
3. Morph в карточку/пилюлю (scale + border-radius change)
4. Контент fade-in после morph

### Wave effect
- Отдельный sibling-элемент `.notif-wave`
- 22×22px circle, accent border
- Scale 0.6 → 9, opacity 0.75 → 0
- Не обрезается карточкой (sibling, не child)

### Auto-dismiss
- Card success: 4с
- Card error: 0 (до тапа)
- Pill: 2.6с

### Trigger logic
- `type: 'info'` без amount/commission/action → pill (auto)
- `type: 'success'` с amount → card
- `type: 'error'` с actionLabel → card с кнопкой "Повторить"

## 2.15 Badges & Chips

### Badge-pill
- Inline-flex, padding 3×8px, `--r-pill`
- Variants: default (accent), success, danger, warning, muted

### Chip
- Inline-flex with optional icon, padding 6×12px, `--r-pill`
- Border: `1px solid --soft-border`
- Variants: default, accent, success, danger

## 2.16 Segmented Control

- Container: `--tile`, padding 3px, `--r-lg`, `--shadow-inset`
- Segment: flex:1, padding 8×12px
- Active: `background: --surface, color: --text, --shadow-soft`

## 2.17 FAB (Floating Action Button)

- Position: absolute bottom-right
- 56×56px, `--r-pill`
- Background: `--accent`, color: `#fff`
- Shadow: `0 10px 24px -4px rgba(255,107,0,0.5)` + insets
- `:active`: `scale(0.92)`
- z-index: 60

## 2.18 Pull-to-refresh indicator

- Height: 22px
- Spinner: 12px, `border 2px --border`, `border-top: --accent`
- Animation: `spin 0.7s linear infinite`

## 2.19 Skeleton loaders

- Background: `linear-gradient(90deg, --tile, --surface, --tile)`
- Background-size: 200% 100%
- Animation: `shimmer 1.4s ease-in-out infinite`
- Radius: `--r-md`

---

# 3. Patterns

## 3.1 Navigation patterns

### Stack navigation (push/pop)
- Forward: `slideInRight` (0.5s spring) — новая страница выезжает справа
- Back: `slideOutRight` (0.5s ease-out) — текущая уезжает вправо
- Back-жест (swipe from left edge) дублирует back-кнопку

### Tab navigation (bottom nav)
- Cross-fade content (0.25s)
- Active state: `--tile` background + accent icon
- Без пружин — табы не "пружинят", только fade

### Bottom sheet
- Sheet slide-up (`--d-sheet` spring) + overlay fade (`--d-base`)
- Close: tap overlay / close button / swipe down
- Sheet может содержать другой sheet (nested, z-index +10)

### Long-press → expand
- Long-press 400ms на cards-wrapper → expand
- Container translateY(60px) вниз
- Cards fan upward с одинаковым размером, только offset
- Overlay dim (без blur по дизайну)

## 3.2 Input patterns

### Amount input
- Large mono number (44px), centered
- Live fiat conversion под input
- Quick amount buttons (25% / 50% / 75% / MAX)
- Fee preview блок ниже
- Validation: `> 0`, `<= balance`

### Address input
- Text input с paste button
- QR scan button (right side)
- Validation: 42 символа (0x + 40 hex) ИЛИ @ник (3+ символа)
- Recent recipients ниже для быстрого выбора

### Search input
- Icon-affix (search icon слева)
- Live filter без debounce
- Empty state если нет результатов

### PIN input
- Custom keypad (3×4 grid)
- 6 dots для отображения ввода
- Shake + red при mismatch
- Stage indicator (create → confirm)

## 3.3 Feedback patterns

### Notification (card / pill)
- Единый компонент для всех feedback-сообщений
- Auto-dismiss для success/info
- Sticky для error (до тапа на action)
- Wave effect при появлении

### Inline banner
- Warning/danger/info banners в потоке контента
- Icon + title + text + optional action
- Не перекрывают контент, а сдвигают

### Form error
- Red border на input
- Error message с icon под input
- Inline, без modal

### Modal dialog
- Centered overlay с dim background
- Spring-scale анимация (0.85 → 1)
- Icon + title + text + actions
- Закрытие: tap overlay / close button / action button

### Pull-to-refresh
- Spinner + text
- Auto-hide после загрузки
- Success notif после обновления

## 3.4 Transactional patterns

### Send flow (4-step wizard)
1. **Recipient** — search/recent/scan/paste → validation → next
2. **Amount** — large input + quick buttons + fee preview → validation → next
3. **Confirm** — hero (amount + recipient) + details list + confirm button
4. **Result** — success icon + amount + actions (view tx / done)

**State management:** Single state object пробрасывается через все шаги.

### Receive
- QR + address + copy + network tabs + optional amount request + share

### History
- Filter chips (5 типов) + grouping by date (today/yesterday/week/month)
- Each tx: icon (тип) + title + sub (token + time) + amount (colored)
- Tap → tx details

### Transaction details
- Hero (status icon + amount + time + network)
- From / To party rows с аватарами
- Details list (amount / fee / total / network / block / confirmations / hash)
- Explorer link

## 3.5 Security patterns

### PIN entry
- Custom keypad (не системный)
- Dots вместо цифр (privacy)
- Shake on error
- Bio button в keypad (опционально)

### Recovery phrase
- Always masked по умолчанию
- Reveal button с warning
- Verify: 3 случайных слова из фразы
- Copy disabled пока masked

### Backup warning
- Warning banner на Settings Hub если backup не создан
- Yellow accent, persistent
- Tap → backup flow

### Approve token (dApp)
- Modal с деталями контракта
- Лимит: unlimited (с предупреждением) или exact
- Fee preview

### Sign transaction (dApp)
- Modal с from/contract/value/gas/fee
- Sign / Reject buttons

---

# 4. Motion

## 4.1 Easing curves

| Token | Curve | Character | Usage |
|-------|-------|-----------|-------|
| `--spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Playful, bouncy | Cards, sheets, buttons — всё что "тактильное" |
| `--ease-out` | `cubic-bezier(0.16, 1, 0.3, 1)` | Smooth, decelerating | Informational transitions, fades, page push |
| `--ease-in` | `cubic-bezier(0.7, 0, 0.84, 0)` | Accelerating | Outro анимации (rare) |
| `--ease` | `cubic-bezier(0.4, 0, 0.2, 1)` | Standard | Default для color transitions |

### Правила
- **Spring** — для всего, что пользователь "трогает" (cards, sheets, buttons, toggles, chips)
- **ease-out** — для всего, что "показывается" (page push, fade, content reveal)
- **Linear** — только для spinners и shimmer (continuous loops)

## 4.2 Duration scale

| Token | Value | Usage |
|-------|-------|-------|
| `--d-fast` | 0.18s | Micro-interactions (icon color, hover) |
| `--d-quick` | 0.25s | Color transitions, small fades, segment switch |
| `--d-base` | 0.35s | Standard transitions, overlay fade, notif morph |
| `--d-page` | 0.5s | Page push/pop, card expand |
| `--d-sheet` | 0.55s | Bottom sheet slide |
| `--d-result` | 0.7s | Result overlays, big icon scale-in |

### Правила
- Ничего быстрее 0.18s (воспринимается как glitch)
- Ничего медленнее 0.7s (пользователь ждёт)
- Loading states могут быть longer (1.4s для shimmer)

## 4.3 Choreography

### Sequencing rules
- **0–100ms:** Immediate feedback (button press, icon swap)
- **100–300ms:** Secondary elements (chips, badges)
- **300–500ms:** Tertiary content (lists, details)
- **500ms+:** Hero animations (result icons, splash)

### Stagger
```css
.stagger > * {
  animation: slideUp var(--d-base) var(--ease-out) backwards;
  animation-delay: calc(var(--i, 0) * 40ms);
}
```
- 40ms между элементами
- Max 8 элементов в stagger (иначе слишком долго)

### Parallel vs sequential
- **Parallel:** Overlay fade + sheet slide (одновременно)
- **Sequential:** Notif drop → wave → content fade-in (по очереди)
- **Hero → details:** Big icon сначала, потом text, потом actions

## 4.4 Trigger taxonomy

| Trigger | Animation | Example |
|---------|-----------|---------|
| Tap | Scale 0.94–0.96 + shadow change | Buttons, list-items |
| Long-press (400ms) | Expand / context menu | Cards stack expand |
| Swipe | Follow finger + spring release | Cards switch, sheets dismiss |
| Pull | Indicator + threshold | Pull-to-refresh |
| System event | Notif drop+morph | Transaction confirmed |
| Page navigation | Slide in/out | Settings → Profile |
| Theme switch | Cross-fade colors | Dark → Light |

## 4.5 Notification choreography (детально)

**Drop + Morph (card mode):**
1. **0%:** `translateY(-110px) scale(0.05)`, border-radius 50%
2. **35%:** `translateY(0) scale(0.05)`, border-radius 50% (приземление)
3. **55%:** `translateY(0) scale(0.4, 0.06)`, border-radius 50% (растекание)
4. **100%:** `translateY(0) scale(1)`, border-radius 28px (final card)

**Wave:** параллельно с 3-й фазой, scale 0.6 → 9, opacity 0.75 → 0

**Content fade-in:** начинается на 45% (0.45s delay), opacity 0 → 1 за 0.3s

**Icon draw-in:** stroke-dashoffset 60 → 0, начинается на 55% (0.55s delay)

## 4.6 Reduced motion

```css
@media (prefers-reduced-motion: reduce) {
  :root {
    --spring: var(--ease-out);
    --d-fast: 0.01s;
    --d-quick: 0.01s;
    --d-base: 0.01s;
    --d-page: 0.01s;
    --d-sheet: 0.01s;
    --d-result: 0.01s;
  }
}
```

Все анимации схлопываются до 0.01s. Spring заменяется на ease-out. Контент остаётся доступным.

---

# 5. Screens

> 26 экранов в прототипе. Ниже — аннотации к каждому.

## 5.1 Main screen (`mdaopay_main_screen.html`)

**Layout:** Header → Cards stack → Contacts block → Services block → Notif zone → Bottom nav

**Key interactions:**
- Swipe cards left/right → switch token
- Long-press cards → expand stack (fan upward, compact headers)
- Tap card action (Отправить/Получить/История) → action
- Tap greeting → profile sheet
- Tap gear → theme cycle (в прототипе)
- Pull-to-refresh → balances update

**States:**
- Default: cards collapsed, balance visible
- Expanded: cards fanned, compact headers, overlay dim
- Notification: notif drops, content shifts down

## 5.2 Styleguide (`styleguide.html`)

13 секций с live-примерами всех компонентов. Theme switcher (3 кнопки сверху).

## 5.3 Settings Hub (`settings.html`)

**Entry point** для всех настроек.

**Layout:** Topbar → Profile card → Backup warning → 4 sections (Аккаунт / Внешний вид / Кошелёк / Поддержка) → Version block

**Sections:**
- Аккаунт: Профиль, Безопасность, Резервная копия
- Внешний вид: Тема, Акцент, Уведомления, Язык
- Кошелёк: Сети, Кошельки и токены
- Поддержка: Помощь, О приложении

## 5.4 Profile (`profile.html`)

**Layout:** Topbar (с edit) → Hero (avatar + name + nick + discriminator) → QR block → Address card (copy) → Share row (TG/WA/Link) → Управление section

## 5.5 Security (`security.html`)

**Layout:** Status card → PIN section (toggle + dots + change) → Биометрия → Автоблокировка (2×2 grid) → Приватность (3 toggles) → Danger zone

**Special:** PIN toggle off → status меняется на warning, dots скрываются, error notif с "Включить обратно"

## 5.6 Appearance (`appearance.html`)

**Layout:** Live preview card → Theme (3 radio cards) → Accent (5 color circles) → Font size (3 options) → Дополнительно (animations + system theme)

**Special:** Accent реально меняет `--accent` CSS variable. Theme реально переключает класс.

## 5.7 Notifications settings (`notifications.html`)

**Layout:** Общие (3 toggles) → Категории (5 toggles) → Цена токенов (toggle + slider) → Тихие часы (toggle + time range) → Звук (5 chips)

## 5.8 Language (`language.html`)

**Layout:** Preview (current lang) → List of 8 languages with flags → Hint text

## 5.9 Networks (`networks.html`)

**Layout:** Add network button → Builtin networks (5) → Custom networks (1) → Info text

**Special:** Tap network → switch active, badge "активна" перемещается

## 5.10 Wallets & Tokens (`wallets-tokens.html`)

**Layout:** Accounts (2 cards + add) → Token management (6 tokens with visibility toggles + add custom) → Hint

## 5.11 Backup (`backup.html`)

**Layout:** Status (warning) → Warning block → Recovery phrase (12 words, masked) → Reveal button → Verify (3 inputs) → Export (JSON + QR)

**Special:** Reveal toggles masking. Copy disabled пока masked. Verify проверяет слова #3/#7/#11.

## 5.12 About (`about.html`)

**Layout:** Hero (logo + version) → Stats (3 cards) → Links (5 buttons) → Система (3 items) → Legal footer

## 5.13 Help (`help.html`)

**Layout:** Search → Quick actions (chat/bug) → FAQ accordion (6 items) → Network status (4 cards)

## 5.14 Send flow (`send.html`)

**4-step wizard** в одном файле.

**Step 1:** Token pill → Recipient input (with scan/paste) → Recent recipients
**Step 2:** Recipient summary → Amount input (large mono) → Quick buttons → Fee preview
**Step 3:** Confirm hero (icon + amount + recipient) → Details list → Confirm button
**Step 4:** Result icon + amount + actions

**State:** Single object, пробрасывается через все шаги. Validation на каждом шаге.

## 5.15 Receive (`receive.html`)

**Layout:** Network tabs → Hero (QR + nick + network) → Address card (copy) → Amount request → Share grid

## 5.16 History (`history.html`)

**Layout:** Filter chips (5) → Date sections (Сегодня / Вчера / На этой неделе) → Tx items

**Special:** Filters реально работают. Empty date sections скрываются.

## 5.17 Transaction details (`tx-details.html`)

**Layout:** Hero (status icon + amount + time) → From/To party rows → Details list (7 rows) → Hash row (copy) → Explorer button

## 5.18 Contacts (`contacts.html`)

**Layout:** Search → Contacts list (6 cards with avatar + name + nick + meta + send button) → FAB add

**Special:** Live search. Empty state если нет результатов.

## 5.19 Notifications center (`notifications-center.html`)

**Layout:** Mark all button → Date sections → Notif cards (5 типов: tx-in/tx-out/swap/security/news) with unread indicator + inline actions

## 5.20 Welcome (`welcome.html`)

**Layout:** Logo top → Skip button → Slide container (4 slides) → Dots indicator → 2 CTAs

**Special:** Swipe navigation, keyboard arrows, skip to last slide.

## 5.21 Create wallet (`create-wallet.html`)

**4-step wizard:**
1. Warning (checkbox + tips)
2. Phrase display (12 words, masked/reveal/copy)
3. Verify (3 random words)
4. Success (icon + setup PIN button)

## 5.22 Import wallet (`import-wallet.html`)

**3 methods (tabs):**
1. Recovery phrase (textarea + paste/scan)
2. Keystore JSON (file upload + password)
3. Private key (input + paste/scan + danger warning)

## 5.23 PIN setup (`pin-setup.html`)

**Layout:** Steps indicator → Lock icon → Stage label → Title → Sub → 6 dots → Custom keypad (3×4) → Bio option (после успеха)

**Special:** 2 stages (create + confirm). Shake + red on mismatch. Keyboard support.

## 5.24 Empty states (`empty-states.html`)

**6 состояний** с tabs: нет tx / контактов / поиска / токенов / уведомлений / оффлайн. Каждое с иконкой, заголовком, описанием, CTA.

## 5.25 Loading states (`loading-states.html`)

**5 типов:**
1. Splash (logo + spinner)
2. Wallet init (4-step checklist with spinning)
3. Syncing (progress bar 65% + skeletons)
4. Skeletons (full page: cards + contacts + history)
5. Button loading (4 variants)

## 5.26 Error states (`error-states.html`)

**5 типов:**
1. Network (icon + code + retry)
2. Insufficient funds (balance display + solutions)
3. Invalid address (form error + tips)
4. Tx failed (causes list)
5. Inline banners (4 variants) + form errors

## 5.27 Modals (`modals.html`)

**6 модалей:** confirm / approve token / switch network / sign tx / delete / info. Spring-scale анимация, modal-detail с метаданными.

---

# 6. Voice & Copy

## 6.1 Tone

**MDAOPay говорит:**
- **Прямо** — без "пожалуйста" в UI-кнопках (Отправить, не "Пожалуйста, отправить")
- **По-русски** — все UI-тексты на русском (или язык пользователя)
- **Technically accurate** — "Комиссия сети", не "Сбор системы"
- **Calm** — без восклицательных знаков в ошибках ("Недостаточно средств", не "Недостаточно средств!")
- **Helpful** — в ошибках предлагаем решение, не только констатируем

## 6.2 Microcopy patterns

### Buttons
| Pattern | Example | Не |
|---------|---------|-----|
| Action verb | "Отправить", "Получить", "Подтвердить" | ❌ "OK", "Готово" для главных действий |
| Confirm destructive | "Удалить", "Стереть" | ❌ "Удалить навсегда?" в кнопке |
| Cancel | "Отмена" | ❌ "Закрыть", "Выйти" |

### Notifications
| Type | Title pattern | Sub pattern |
|------|---------------|-------------|
| Success | Что произошло | Детали |
| Error | Что не получилось | Почему / Что делать |
| Info | Краткий факт | Контекст |

**Examples:**
- ✅ "Отправлено" / "25.00 USDT → @alice"
- ✅ "Недостаточно средств" / "Доступно: 1 250.50 USDT"
- ✅ "Скопировано" / "Адрес кошелька"
- ❌ "Ошибка!" / "Что-то пошло не так"

### Form labels
- Uppercase, letter-spacing 0.8px
- "Ник получателя", не "Введите ник"
- "Адрес кошелька", не "Адрес кошелька (0x...)"

### Empty states
- Title: что нет (без "пока")
- Sub: что делать
- ✅ "Пока нет транзакций" / "Здесь появятся все входящие и исходящие переводы."
- ❌ "Список пуст" / "Добавьте первый элемент"

### Errors
- Без "Ошибка:" в начале
- Без восклицаний
- С решением если возможно
- ✅ "Недостаточно средств" / "Доступно: 1 250.50 USDT"
- ❌ "Ошибка! Недостаточно средств!!!"

## 6.3 Number formats

### Балансы
- `1 250.50` — пробел как thousand separator, точка как decimal
- `--font-mono` обязательно
- Unit через пробел: `1 250.50 USDT`
- Fiat с `≈`: `≈ $1 250.50`

### Адреса
- Full: `0x8A9B3F2C7E4D1A6B5C8E9F0A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2`
- Short: `0x8A9B…C1D2` (first 6 + last 4)
- `--font-mono` обязательно

### Commissions
- `0.1 USDT` — без "≈", точное значение
- `--font-mono`

### Время
- Сегодня: `14:32`
- Вчера: `Вчера, 18:50` или просто `18:50` в grouped list
- На неделе: `19 июня`
- Раньше: `15 мая 2026`

### Percentages
- Trend: `+2.4%`, `-1.2%` (с плюсом для positive)
- Slider: `5%` (без плюса)

---

# 7. Accessibility

## 7.1 Контрасты (WCAG AA)

Все текстовые комбинации проходят WCAG AA (4.5:1 для normal text, 3:1 для large).

**Dark theme:**
| Combination | Ratio | Status |
|-------------|-------|--------|
| `--text` on `--bg` (#FFF on #0A0A0F) | 19.7:1 | AAA |
| `--text2` on `--bg` (#7E8089 on #0A0A0F) | 5.2:1 | AA |
| `--text3` on `--bg` (#4D4F58 on #0A0A0F) | 2.3:1 | Decorative only |
| `--accent` on `--bg` (#FF6B00 on #0A0A0F) | 5.0:1 | AA |
| `--text` on `--accent` (#FFF on #FF6B00) | 3.4:1 | AA Large |

**Light theme:**
| Combination | Ratio | Status |
|-------------|-------|--------|
| `--text` on `--bg` (#0A0A0F on #E8EBF1) | 19.7:1 | AAA |
| `--text2` on `--bg` (#6B7280 on #E8EBF1) | 4.6:1 | AA |

### Правила
- ✅ `--text3` использовать только для декоративных элементов (placeholder dots, disabled)
- ✅ `--accent` на `--accent-soft` — для иконок, не для текста
- ❌ Не использовать `--text3` для meaningful text

## 7.2 Touch targets

| Element | Min size | Actual |
|---------|----------|--------|
| Buttons | 44×44px | 44px (sm) — 54px (lg) |
| Icon buttons | 44×44px | 42×42px (близко) |
| List items | 44px height | ~56px |
| Toggle | 44×44px | 50×30px (hit area extends) |
| Nav items | 44×44px | ~57px height |
| Keypad keys | 44×44px | aspect-ratio 1.4, ~50px |

## 7.3 Screen reader semantics

### ARIA labels
- Все icon-only buttons имеют `aria-label`
- Примеры: `aria-label="Назад"`, `aria-label="Копировать"`, `aria-label="Закрыть"`

### Semantic HTML
- `<button>` для всех интерактивов (не `<div onclick>`)
- `<input type="text">` / `type="password"` / `type="number"` правильно
- `<h3>` для sheet titles (не div)
- `<label>` для inputs

### Live regions
- Notification — `aria-live="polite"` (аннounce без прерывания)
- Loading state — `aria-busy="true"` на контейнере

## 7.4 Focus states

- `:focus-visible` — `outline: 2px solid --accent; outline-offset: 2px`
- Не убирать outline без замены
- Keyboard nav работает на всех экранах (tab order logical)

## 7.5 Reduced motion

См. раздел 4.6. Все анимации схлопываются до 0.01s. Контент остаётся доступным.

```css
@media (prefers-reduced-motion: reduce) {
  :root {
    --spring: var(--ease-out);
    --d-fast: 0.01s;
    /* ... все durations → 0.01s */
  }
}
```

## 7.6 Color blindness

- Success (`#00D68F`) и Danger (`#FF4D6D`) различимы для большинства типов
- Не полагаться ТОЛЬКО на цвет — всегда есть icon + text
- Trend badges: `+` / `-` prefix + color (не только color)

## 7.7 Dark mode preference

```css
@media (prefers-color-scheme: dark) {
  /* Опционально: auto-switch to dark/amoled */
}
```

В прототипе — ручной выбор темы, но production должен поддерживать auto.

---

# 8. Theming

## 8.1 Three base themes

### Dark (default)
- BG: `#0A0A0F` (почти чёрный с лёгким blue tint)
- Card: `#1A1A22` (lighter)
- Tile: `#23232C` (ещё lighter для inset surfaces)
- Используется по умолчанию

### Light
- BG: `#E8EBF1` (мягкий blue-gray)
- Card: `#FBFCFE` (почти белый)
- Tile: `#E4E8EE` (серый для inset)
- Shadow opacity выше (28% vs 70%) — на светлом тени заметнее

### AMOLED
- BG: `#000000` (pure black для OLED screens)
- Card: `#0B0B0D` (почти чёрный)
- Tile: `#151518` (dark gray)
- Overlay opacity выше (55% vs 40%) — на чёрном нужно сильнее затемнять

## 8.2 Theme application

```html
<div class="phone theme-dark">...</div>
<div class="phone theme-light">...</div>
<div class="phone theme-amoled">...</div>
```

Theme class на корневом элементе (`.phone` или `body`) переключает все CSS variables.

## 8.3 Theme transitions

```css
.phone, body {
  transition:
    background-color var(--d-base) var(--ease-out),
    color var(--d-base) var(--ease-out);
}

.card, .block, .sheet, .tile, .icon-btn, .nav-item,
.avatar, .service-icon .icon-box, .input-field, .list-item {
  transition:
    background-color var(--d-base) var(--ease-out),
    border-color var(--d-base) var(--ease-out),
    color var(--d-base) var(--ease-out),
    box-shadow var(--d-quick) var(--ease-out);
}
```

Все цвета плавно cross-fade'ятся за 0.35s.

## 8.4 Custom accent colors

5 preset accent colors (Appearance screen):

```javascript
const ACCENT_COLORS = {
  orange: { main:'#FF6B00', press:'#E55F00', soft:'rgba(255,107,0,0.10)' },
  blue:   { main:'#2D7FF9', press:'#1F6FDD', soft:'rgba(45,127,249,0.10)' },
  green:  { main:'#00B377', press:'#009966', soft:'rgba(0,179,119,0.10)' },
  purple: { main:'#7B4DFF', press:'#6736E5', soft:'rgba(123,77,255,0.10)' },
  pink:   { main:'#F94D9E', press:'#DD3A87', soft:'rgba(249,77,158,0.10)' }
};

// Применение
document.documentElement.style.setProperty('--accent', c.main);
document.documentElement.style.setProperty('--accent-press', c.press);
document.documentElement.style.setProperty('--accent-soft', c.soft);
```

## 8.5 Dynamic theming rules

### Что меняется с accent
- ✅ Все CTA кнопки (primary)
- ✅ Активные nav items
- ✅ Активные tab indicators
- ✅ Notification wave
- ✅ Links и accent text
- ✅ Slider thumbs
- ✅ Toggle on state
- ✅ Focus outlines

### Что НЕ меняется с accent
- ❌ Token card colors (USDT green, MDAO purple, etc.)
- ❌ Network colors (BSC yellow, ETH blue, etc.)
- ❌ Semantic colors (success/danger/warning)
- ❌ Avatar gradients (между аккаунтами)

## 8.6 System theme detection

```css
@media (prefers-color-scheme: dark) {
  :root { /* dark defaults */ }
}
@media (prefers-color-scheme: light) {
  :root { /* light defaults */ }
}
```

Опция "Системная тема" в Appearance — следует системному preference.

## 8.7 Brand overrides (для партнёров)

Возможность создать custom theme:
```css
.theme-partner {
  --bg: #custom;
  --card: #custom;
  --accent: #custom;
  /* ... all tokens */
}
```

Все компоненты автоматически подстроятся.

## 8.8 Theme storage

```javascript
localStorage.setItem('mdao_theme', 'dark');
localStorage.setItem('mdao_accent', 'orange');
localStorage.setItem('mdo_font_size', 'm');
```

Загружается при init приложения.

---

# Appendix

## A. File structure

```
/download/
├── index.html                  # Hub
├── mdaopay_main_screen.html    # Main screen
├── styleguide.html             # Component gallery
│
├── tokens.css                  # Design tokens (3 themes)
├── components.css              # Component library
├── transitions.css             # Animations
├── notif.js                    # Shared notification helper
│
├── settings.html               # 1.1 Settings Hub
├── profile.html                # 1.2
├── security.html               # 1.3
├── appearance.html             # 1.4
├── notifications.html          # 1.5
├── language.html               # 1.6
├── networks.html               # 1.7
├── wallets-tokens.html         # 1.8
├── backup.html                 # 1.9
├── about.html                  # 1.10
├── help.html                   # 1.11
│
├── send.html                   # 2.1 Send flow
├── receive.html                # 2.2
├── history.html                # 2.3
├── tx-details.html             # 2.4
├── contacts.html               # 2.5
├── notifications-center.html   # 2.6
│
├── welcome.html                # 3.1
├── create-wallet.html          # 3.2
├── import-wallet.html          # 3.3
├── pin-setup.html              # 3.4
│
├── empty-states.html           # 4.1
├── loading-states.html         # 4.2
├── error-states.html           # 4.3
└── modals.html                 # 4.4
```

## B. Component checklist (для новых экранов)

- [ ] Использует tokens.css (без хардкод-цветов)
- [ ] Использует components.css (без дублирования стилей)
- [ ] Подключает notif.js для уведомлений
- [ ] Работает в 3 темах (dark/light/amoled)
- [ ] Topbar с back-button
- [ ] Notif-zone в конце content-wrap
- [ ] Theme cycle по тапу на title (для прототипа)
- [ ] Aria-labels на icon buttons
- [ ] Touch targets ≥ 44px
- [ ] Reduced-motion friendly

## C. Naming conventions

- **CSS classes:** kebab-case (`.list-item`, `.btn-primary`)
- **CSS variables:** `--kebab-case` (`--accent`, `--shadow-soft`)
- **JS variables:** camelCase (`currentTheme`, `isNotifOpen`)
- **Files:** kebab-case (`tx-details.html`, `notif.js`)
- **IDs:** camelCase или kebab (`notifTitle`, `notif-zone`)

## D. Versioning

- **Major** (1.x.x) — breaking changes в design system
- **Minor** (x.1.x) — новые компоненты / экраны
- **Patch** (x.x.1) — bug fixes, copy changes

Текущая версия: **1.0.0** — первый полный выпуск Design Bible.

---

*MDAOPay Design Bible v1.0.0 · Июнь 2026 · Built on 26-screen HTML prototype*
