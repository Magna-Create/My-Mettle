# My Mettle UI: reusable components and hybrid graphics direction

> **Status: design direction / future investigation, NOT a renderer implementation contract.** 16 November 2026.
>
> **Immediate Stable priority:** keep the native workout experience responsive and functional. Advanced graphics work belongs in Lab after the Stable cutover. Related: [Daily Update/Haze implementation](./HOME_GLASS_RENDERER.md), [Stable cutover](./NATIVE_STABLE_CUTOVER.md).

## Component architecture

Keep **Jetpack Compose** as the application, accessibility, navigation, text and normal interaction framework. Extract reusable, independently configurable components into focused files; separate their data/interaction contracts from material/visual definitions.

Conceptual boundaries:

- Components: exercise card, set input, draggable selector, workout/day chooser, header, hotbar, highlight surface.
- Materials/effects: translucent/glass surface, rim light, optional reflection, shadow, distortion, blur, reduced-motion/low-cost fallback.
- Scene/effect coordination: retained background source where possible, shared lighting parameters, visibility/dirty-region state and performance budget.
- Dedicated 3D viewport: optional later N-BIO character/anatomy visualisation, not a replacement for the entire native UI.

A reused component does **not** require an independent texture or render pass. Compose composition/drawing reuse and shared GPU material instances should be investigated before implementing explicit mesh instancing. GPU instancing is most relevant to large batches of truly shared geometry in a dedicated renderer.

All visual materials should allow **per-instance overrides** and accessibility/performance fallbacks. A static hotbar can afford different refraction settings from a fingertip-following selector.

## Glass and scene-aware caching

The goal is to avoid each glass component independently recreating its entire backdrop. Potential strategy:

1. Retain/reuse common background draw layers or textures if source content is unchanged.
2. Supply lens/window position, shape and material parameters to a shader so a moving element can sample new coordinates from an existing source.
3. Use region bounds with padding for blur kernel/refraction displacement; update or invalidate only when contributing content changes.
4. Cache logical content layers/tiles, **not an image for every scroll offset**. Scroll transforms change sampling coordinates; newly visible or changed content invalidates tiles.
5. Treat procedural backgrounds (gradients and simple geometry) as a separate option where analytical re-evaluation is simpler than image capture.
6. Use selective distortion, lower sampling resolution or bypass during interaction where visual benefit does not justify cost.

**Physical limitation:** knowing the element's position or the Compose vector geometry does not magically grant a shader access to arbitrary pixels beneath it. True refraction requires an accessible source texture/layer or a faithfully recomputable scene description. Cross-surface native/3D compositing is a separate challenge.

**Compilation is distinct from caching:** precompiled shaders reduce compilation work, while cached backdrop images and warmed GPU pipelines are different resources. Loading screens are not desirable merely to warm inexpensive UI effects.

## Renderers worth investigating later

- **Compose/AGSL RuntimeShader:** first-class route for many custom effects while preserving native controls; AGSL RuntimeShader requires newer Android APIs than the app's current minSdk 28, so any implementation needs feature detection/fallback.
- **Haze:** continue to profile current glass implementation and evaluate newer shared/reusable source capabilities before assuming a bespoke renderer is cheaper. Do not remove functioning effects solely because an alternative sounds theoretically better.
- **Filament or other Vulkan-capable 3D renderer:** later contained viewport for character/anatomy scene, dynamic lighting and physically informed surfaces. Embedding a 3D surface does not automatically make Compose and 3D share a physically unified refraction scene.
- **Full custom UI renderer:** not the default. Significant accessibility, composition and maintenance cost.

The relevant target is **an efficient retained 2.5D visual layer**, not converting the whole app into a 3D game.

## Frame scheduling and performance

Submit new visual frames when state, animation or interaction demands them; stop *unnecessary scene redraws* when idle, not Android lifecycle, accessibility or necessary UI handling. Profile occlusion/visibility, shared-source memory, shader/texture allocations, scroll latency, energy and temperature on representative devices.

Test a small proof of concept against the current implementation. Define acceptable quality/performance before promoting a custom renderer.

## Stable vs future design scope

- **Stable:** simplify exercise area by removing heavy live glass, preserve a performant header/hotbar where justified; do not couple routine/set entry to new GPU infrastructure.
- **Future dashboard:** preserve tactile blue/navy design language, Greek day selectors and deliberately pre-rendered/unskippable celebratory Highlights animations. Detailed metrics and an N-BIO-linked character belong in their own dedicated experience.
- **Future graphics system:** develop one component/material at a time, compare running device costs and retain a simple fallback. UI remains functional while individual materials evolve.

No design in this document is already implemented just because its architecture is described.

