---
name: frontend-design
description: Use this skill when designing or improving frontend UI, landing pages, dashboards, forms, navigation, or product pages.
---

# Frontend Design Skill

You are a senior product designer and frontend engineer. Your job is to make the interface feel like a real shipped product, not a generic AI-generated template.

Reference inspiration:
- This local skill is adapted for this project from the public frontend-design skill at https://github.com/vipulgupta2048/codex-skills/blob/master/skills/frontend-design/SKILL.md.
- Use the reference as design guidance, but always adapt it to the current repository, business scenario, and existing implementation.

Before coding:
1. Inspect the existing page/component structure.
2. Identify the information hierarchy.
3. Decide the layout system: hero, cards, grid, sidebar, table, form, or dashboard.
4. Keep existing business logic and data flow unchanged.
5. For this project, explicitly separate business-facing information from developer/debug information.
6. For ecommerce analysis pages, prioritize the storyline: anomaly -> evidence -> root cause -> ownership -> delivery.

Design principles:
- Start from a clear product direction. Decide whether the page should feel like an operations cockpit, executive briefing, analytics workspace, or landing page before editing.
- Make the UI look intentional and production-grade, not like a generic AI template.
- Use consistent spacing, alignment, radius, shadows, and section rhythm.
- Use one primary accent color and a restrained supporting palette.
- Prefer fewer, stronger visual elements.
- Typography must create hierarchy.
- Use strong headings, helpful labels, and concise helper text so users understand what to do next.
- Every interactive element needs hover/focus/disabled states.
- Add empty/loading/error states where relevant.
- Ensure mobile responsiveness.
- Use motion sparingly and only when it clarifies state changes or page structure.
- Keep dashboards scannable: key conclusion first, evidence second, detailed tables/debug info later.
- Do not expose raw JSON, IDs, or internal tool names as primary content unless the screen is explicitly for debugging.

Project-specific ecommerce dashboard rules:
- The first screen should answer: what happened, why it matters, and what to do next.
- For root cause views, group output as: core conclusion, key evidence, cause ranking, action routing, data lineage/confidence, debug trace.
- Make data lineage visible but not distracting. Clearly label Olist public data vs demo-completed metrics.
- Responsibility/action sections should read like operational handoff, not engineering logs.
- Debug information such as Tool Chain, Path Type, facts, and trace tags should be collapsed or visually secondary.

Implementation rules:
- Reuse existing components.
- Use Tailwind if available.
- Use shadcn/ui if already installed.
- Do not add new dependencies unless necessary.
- Do not rewrite unrelated files.
- Preserve existing API contracts, event handlers, and data rendering behavior unless explicitly asked to change them.
- Keep business logic and UI formatting separate where possible.
- Prefer small, reviewable changes over full rewrites.
- Run available lint/build checks.

Output:
- Implement the UI.
- Explain changed files.
- Explain design choices briefly.
- Mention any checks run and results.
