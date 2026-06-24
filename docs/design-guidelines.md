# NBP Design System

Design tokens and brand assets extracted from the official Narodowy Bank Polski website ([nbp.pl](https://nbp.pl/)) on 2026-06-24. Use these to keep any UI built during the course visually consistent with the NBP brand.

> All user-facing text in the app must be in **Polish**.

---

## Assets

| Asset | File | Notes |
|---|---|---|
| Homepage screenshot | [`../assets/homepage.png`](../assets/homepage.png) | Full-page reference capture |
| Logo (wordmark) | [`../assets/logo.svg`](../assets/logo.svg) | Gold `#BDAD7D` mark, 205×64 viewBox |
| Favicon | [`../assets/favicon.ico`](../assets/favicon.ico) | Browser tab icon |
| Design tokens | [`../assets/design-tokens.json`](../assets/design-tokens.json) | Machine-readable token source |

---

## Colors

### Brand

| Token | Hex | Usage |
|---|---|---|
| `brand.primary` | `#152E52` | NBP navy — header background, headings, primary brand surfaces |
| `brand.accent` | `#BDAD7D` | Gold/sand — logo, accent badges, "MENU" button, highlight elements |
| `brand.link` | `#4A74B0` | Steel blue — links and primary CTAs ("WIĘCEJ") |
| `brand.error` | `#C0392B` | Errors / validation (inferred — not from site) |
| `brand.success` | `#2E7D32` | Success states (inferred — not from site) |

### Backgrounds

| Token | Hex | Usage |
|---|---|---|
| `background.default` | `#FFFFFF` | Page background |
| `background.light` | `#F7F7F7` | Section / card backgrounds |
| `background.dark` | `#152E52` | Header, footer, dark sections |
| `background.overlay` | `rgba(21,46,82,0.6)` | Modal / image overlays |

### Borders

| Token | Hex | Usage |
|---|---|---|
| `border.default` | `#BFCEDD` | Inputs, dividers (blue-tinted) |
| `border.muted` | `#C4C4C4` | Neutral separators |

### Text

| Token | Hex | Usage |
|---|---|---|
| `text.primary` | `#464646` | Body copy |
| `text.secondary` | `#2B2B2B` | Stronger body text |
| `text.muted` | `#323232` | Secondary / meta text |
| `text.heading` | `#152E52` | Headings (navy) |
| `text.link` | `#4A74B0` | Links |
| `text.onDark` | `#FFFFFF` | Text on navy backgrounds |

---

## Typography

Two Google-hosted typefaces, served locally by NBP as `@font-face` (TTF):

- **Brygada 1918** — a Polish-designed serif used for **headings** (`h1`, `h2`). Weights 400/500/600/700 + italics. Lends an editorial, institutional, trustworthy tone.
- **Libre Franklin** — a grotesque sans used for **body, navigation, UI**. Full weight range (100–900) + italics.

```css
--font-heading: "Brygada 1918", Georgia, "Times New Roman", serif;
--font-body: "Libre Franklin", -apple-system, Arial, "Noto Sans", sans-serif;
```

Load from Google Fonts when self-hosting is not available:
```
https://fonts.googleapis.com/css2?family=Brygada+1918:ital,wght@0,400..700;1,400..700&family=Libre+Franklin:ital,wght@0,100..900;1,100..900&display=swap
```

### Weight scale

| Name | Value |
|---|---|
| regular | 400 |
| medium | 500 |
| semibold | 600 |
| bold | 700 |

Headings render at weight **500 (medium)**; active/top-level nav at **600 (semibold)**.

### Size scale

| Token | Size | Usage |
|---|---|---|
| `sm` | 13px | Buttons, captions, badges |
| `base` | ~15.5px | Body default (site computes 15.49px) |
| `md` | 16px | Comfortable body |
| `lg` | 20px | Sub-headings |
| `xl` | 24px | Section headings |
| `2xl` | 33px | `h1`/`h2` (line-height 44px ≈ 1.33) |

### Line height

| Token | Value |
|---|---|
| tight | 1.2 |
| base | 1.55 (24px on 15.5px body) |
| heading | 1.33 |
| relaxed | 1.6 |

---

## Spacing

Base unit **4px**, scaling in steps of 4:

`4 · 8 · 12 · 16 · 20 · 24 · 28 · 32 · 40 · 48` (px)

---

## Border Radius

| Token | Value | Usage |
|---|---|---|
| `none` | 0px | Flat sections, header |
| `sm` | 2px | Subtle rounding |
| `md` | 4px | **Default** — buttons, cards, badges |
| `lg` | 6px | Inputs / search field |
| `full` | 999px | Pills |
| `circle` | 50% | Avatars, calendar event dots |

---

## Components

### Header
Solid NBP navy (`#152E52`) bar, no radius. Gold logo wordmark on the left, white search field and navigation toggle on the right. White text throughout.

### Navigation
Top-level items uppercase, navy (`#152E52`), weight 400 → 600 when active/expanded. Sub-items sentence-case, weight 400.

### Buttons

**Primary (CTA — "WIĘCEJ"):**
```css
background: #4A74B0;
color: #FFFFFF;
padding: 6px 12px;
border: 1px solid #4A74B0;
border-radius: 4px;
font-size: 13px;
font-weight: 500;
text-transform: uppercase;
```

**Accent ("MENU"):**
```css
background: #BDAD7D;
color: #152E52;
padding: 12px 27px;
border-radius: 4px;
font-size: 13px;
font-weight: 500;
text-transform: uppercase;
```

### Inputs (search field)
```css
background: #FFFFFF;
border: 1px solid #BFCEDD;
border-radius: 6px;
padding: 10px;
color: #464646;
```

---

## Logo Usage

`assets/logo.svg` is the **monochrome gold (`#BDAD7D`) wordmark** — the circular "NBP" emblem plus the full "NARODOWY BANK POLSKI" lockup (205×64). It is designed to sit on the **navy (`#152E52`) header**, where the gold reads cleanly.

- On dark/navy backgrounds: use the gold SVG as-is.
- On white/light backgrounds: recolor the SVG fill to navy `#152E52` for adequate contrast (the gold-on-white pairing is low-contrast and should be avoided for primary use).
- Preserve aspect ratio (205:64 ≈ 3.2:1); never stretch or recolor to non-brand hues.
- Maintain clear space around the mark equal to the height of the "N" emblem.

---

## Visual Style Summary

NBP's identity is **institutional, sober, and trustworthy** — exactly what a national central bank requires. The palette pairs a deep, authoritative **navy** with a restrained **gold** accent that signals heritage and value (echoing coinage), accented by a calm **steel blue** for interaction. The serif **Brygada 1918** for headings gives an editorial, official gravitas, balanced by the clean, highly legible **Libre Franklin** for everything functional. Layouts are flat and orderly with modest 4px rounding, generous whitespace, and high legibility — conveying stability and public-sector seriousness over flourish.
