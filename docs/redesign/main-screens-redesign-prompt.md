# Redesign Prompt: Main Screens

## App Context

**Gisti — AI Checklists** — mobile app (Android/iOS), transforms any content into checklists using AI. Three core features: Create via AI, Fill via AI, Export. Business model: freemium ($1.99/mo). Design system: Minimal & Clean, white background, blue accents (#2196F3), Material3 typography, 12dp card corners.

---

## Screen 1: Main Screen — Empty State

### Current State
- Centered illustration (checklist icon in a semi-transparent circle, 88dp)
- Title: "Ready to get organized?"
- Subtitle with description
- Blue "+ Create Checklist" button at bottom (bottom bar)
- Empty top bar (title = ""), only Email icon (feedback) and credits chip on the right

### Problems
1. Screen looks too empty — huge white space doesn't create engagement
2. Illustration is a plain Material icon in a circle — looks like a placeholder, not a polished design
3. No visual emphasis on AI capabilities — user doesn't understand the value proposition
4. "+ Create Checklist" button is too far from content — disconnect between CTA and empty state
5. No hint that you can create from photo/PDF/link/voice — USP is lost

### Redesign Brief
- Replace icon with a full illustration or animated illustration conveying "turning chaos into a checklist"
- Add 2-3 quick action cards/buttons directly in empty state: "Create from Photo", "Create from Text", "Use Template" — so user immediately sees capabilities
- Consider welcome text with user name or time of day ("Good morning! Start your first checklist")
- CTA button should be closer to content, not pinned to bottom
- Keep minimalism but add life — animation, gradient, or micro-illustration

---

## Screen 2: Main Screen — With Checklists

### Current State
- Top bar: empty title, Email icon (feedback), chip "AutoAwesome 100 credits"
- PremiumBanner: purple horizontal gradient (#667EEA -> #764BA2), text "Go Premium / Only X credits left -> Upgrade >"
- Checklist list: white cards (Card with 2dp elevation), name + LinearProgressIndicator + "0/3" + right arrow
- Bottom bar: blue "+ Create Checklist" button
- Reorder via long press (wiggle animation)

### Problems
1. Checklist cards are too minimalistic — no visual distinction between them, only name and progress bar
2. Premium banner takes too much vertical space and visually "screams" with bright gradient — distracts from main content
3. Progress "0/3" without context — unclear what it means (items? fills?)
4. No icons/emoji/color coding for checklists — everything looks the same
5. Right arrow (KeyboardArrowRight) — old iOS 7 pattern, used less in modern designs
6. No visual hierarchy — all cards same size and weight
7. Credit chip in top bar is small, user might not notice
8. No FAB (Floating Action Button) — for task managers FAB is more familiar than full-width bottom button

### Redesign Brief
- Checklist cards: add visual identity — color/icon/emoji, creation date or "last edited", fill count
- Consider FAB instead of bottom bar button — frees up space for content
- Premium banner: make more compact and less loud — subtler gradient, or dismissible, or integrated into top bar
- Progress: add context ("3 items - 0 done") or visual ring/circle progress instead of linear
- Add grouping/categories capability for checklists
- Top bar: consider title "My Checklists" or user name, move feedback to settings/profile
- Consider grid layout (2 columns) instead of list for visual richness

---

## Screen 3: Edit Checklist (Create/Edit)

### Current State
- AppScaffold with title "Edit Checklist" and back button
- "Checklist Name" section: label + AppTextField with clear button (x)
- "Items" section: label + inline "+ Add Item" chip button
- Items list: white AppCard with text + delete button (x), appear animation
- Bottom bar: blue "Save" button

### Problems
1. "+ Add Item" button is a small chip — easy to miss; no placeholder hint for what to enter
2. Item deletion (x) — no confirmation, easy to accidentally delete
3. No drag-to-reorder for items — user can't change order
4. No inline editing — to change item text, need to delete and recreate
5. Items look like read-only cards — no visual affordance that they can be edited
6. No empty state for items ("Add your first item")
7. "Save" button is always active — even if nothing changed (no disabled state)
8. No AI button "Generate items from description" — USP is missed

### Redesign Brief
- Items: add drag-handle for reorder, swipe-to-delete, inline edit on tap
- "+ Add Item": replace with a full input field at bottom of list (or make current AddItemInputField more visually prominent)
- Add "AutoAwesome Generate with AI" button near Items section — for AI generation of items from checklist description
- Save button: disabled when no changes; show unsaved changes indicator
- Consider "Undo" for item deletion (snackbar "Item deleted - Undo")
- Add item counter ("3 items")
- Name section: consider icon/emoji picker for visual identification of checklist

---

## General Requirements

| Aspect | Requirement |
|--------|-------------|
| **Platform** | Kotlin Multiplatform (Jetpack Compose) — Android + iOS |
| **Design System** | Material3, white background, Blue primary (#2196F3) |
| **Tone** | Minimal & Clean, but alive — not "bare" Material |
| **Spacing** | AppDimens: Xs=4, Sm=8, Md=12, Lg=16, Xl=24, Xxl=32 dp |
| **Corners** | 12dp for cards, 16dp for banners/buttons |
| **Typography** | Material3 (titleLarge, titleMedium, bodyLarge, bodySmall, labelMedium) |
| **AI accent** | AutoAwesome icon for all AI actions |
| **Edge-to-edge** | statusBarsPadding + navigationBarsPadding for fullscreen layouts |
| **Accessibility** | contentDescription for all icons, min 48dp touch target |
| **Animations** | Subtle: animateItem for lists, animateDpAsState for elevation |

## Inspiration

- **Things 3** — minimalism with warmth, excellent empty states
- **Todoist** — visual hierarchy, color-coded projects
- **Notion** — icons/emoji for identification, cleanliness
- **Linear** — modern task management, animation quality
