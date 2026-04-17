# SmartNoti UI Improvement Rules

Use this guide when polishing SmartNoti's Compose UI.

## Product intent
SmartNoti is a notification filtering app. The UI should feel calm, trustworthy, and efficient. Favor clarity over decoration.

## Core design direction
- Dark-first visual language
- Cleaner hierarchy, tighter spacing rhythm, fewer default Material-looking surfaces
- Linear / Superhuman inspired polish: quiet confidence, crisp cards, restrained accent use
- Keep interactions familiar and behavior unchanged unless explicitly requested

## UX priorities
1. Make Priority information feel urgent but not noisy
2. Make Digest information feel organized and scannable
3. Make Rules and Settings feel like operator controls, not generic forms
4. Surface classification reasons clearly so users trust the system

## Visual guidelines
- Prefer layered dark backgrounds instead of flat default Material containers
- Use consistent corner radii, spacing, and card elevation/shadow treatment
- Use subtle borders/dividers to separate sections instead of heavy blocks
- Improve typography hierarchy before adding more color
- Use accent color sparingly for selection, status, and primary actions
- Keep dense screens readable with grouped sections and labels

## Component guidance
### Cards and surfaces
- Replace default-looking cards with more intentional container styling
- Use section headers and grouped cards for information density
- Favor reusable polished containers over one-off styling

### Lists and rows
- Improve row hierarchy with title / supporting text / metadata structure
- Keep tap targets large enough while reducing visual clutter
- Use chips, badges, or small labels for status where helpful

### Forms and controls
- Make Rules and Settings screens feel curated, not like plain generated settings pages
- Group related controls into sections with short descriptive text
- Prefer clearer labels and helper text over placeholder-heavy inputs

### Navigation and scaffold
- Ensure screen backgrounds, top bars, and content paddings feel coherent across screens
- If scaffold polish helps, make it reusable and keep navigation behavior intact

## Constraints
- Do not change product behavior unless specifically asked
- Do not remove reasoning/explanation affordances
- Prefer reusable polish over screen-specific hacks
- Keep implementation practical for the current Compose codebase

## When making changes
- Preserve existing logic and state wiring
- Focus on high-impact screens that still look like default Material
- Briefly summarize which screens/components were polished and how consistency improved
