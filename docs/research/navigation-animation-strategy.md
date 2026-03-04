# Navigation Animation Strategy

Research and concrete animation plan for replacing the S&C Batch Authorization Processor's uniform `fadeIn`/`fadeOut` navigation transitions with semantically meaningful motion that communicates spatial relationships and user intent.

**Date**: 2026-03-04
**Status**: Research complete, ready for implementation
**Current setup**: `NavHost` with global `enterTransition = fadeIn(tween(300))` / `exitTransition = fadeOut(tween(300))`

---

## 1. Navigation Graph Audit

The application has six screens connected by ten distinct navigation transitions. Each transition has been categorized by its semantic type -- what it communicates to the user about the relationship between the source and destination.

### 1.1 Screen Inventory

| Route Constant    | Screen             | Role                              | Background  |
|-------------------|--------------------|-----------------------------------|-------------|
| `FILE_SELECTION`  | MainScreen         | Home / entry point                | WarmWhite   |
| `PROCESSING`      | ProcessingScreen   | Transient work indicator          | GreenTint   |
| `RESULTS`         | ResultsScreen      | Primary data display              | GreenTint   |
| `HELP`            | HelpScreen         | Reference / support (read-only)   | WarmWhite   |
| `SETTINGS`        | SettingsScreen     | Preferences                       | WarmWhite   |
| `EXPORT_SETTINGS` | ExportSettingsScreen | Column config sub-settings      | WarmWhite   |

### 1.2 Transition Inventory

| #  | From              | To                | Semantic Type      | Navigation Method                        |
|----|-------------------|-------------------|--------------------|------------------------------------------|
| 1  | FILE_SELECTION    | PROCESSING        | Forward workflow   | `navigate(PROCESSING)`                   |
| 2  | PROCESSING        | RESULTS           | Forward workflow   | `navigate(RESULTS) { popUpTo(FILE_SELECTION) }` |
| 3  | FILE_SELECTION    | HELP              | Lateral / overlay  | `navigate(HELP)`                         |
| 4  | HELP              | FILE_SELECTION    | Back               | `popBackStack()`                         |
| 5  | RESULTS           | HELP              | Lateral / overlay  | `navigate(HELP)`                         |
| 6  | HELP              | RESULTS           | Back               | `popBackStack()`                         |
| 7  | FILE_SELECTION    | SETTINGS          | Lateral / overlay  | `navigate(SETTINGS)`                     |
| 8  | SETTINGS          | FILE_SELECTION    | Back               | `popBackStack()`                         |
| 9  | SETTINGS          | EXPORT_SETTINGS   | Drill-down         | `navigate(EXPORT_SETTINGS)`              |
| 10 | EXPORT_SETTINGS   | SETTINGS          | Back (drill-up)    | `popBackStack()`                         |
| 11 | RESULTS           | EXPORT_SETTINGS   | Lateral / overlay  | `navigate(EXPORT_SETTINGS)`              |
| 12 | EXPORT_SETTINGS   | RESULTS           | Back               | `popBackStack()`                         |
| 13 | RESULTS           | FILE_SELECTION    | Reset              | `popBackStack(FILE_SELECTION, inclusive = false)` |

### 1.3 Semantic Categories Explained

**Forward workflow** (transitions 1-2): The user is progressing through a linear pipeline. Motion should convey momentum and progress -- moving *forward* in space. The user does not expect to return to the origin via back navigation (Processing is transient; Results uses `popUpTo` to remove Processing from the back stack).

**Lateral / overlay** (transitions 3, 5, 7, 11): The user is making a contextual detour -- opening a reference panel, settings, or configuration screen. They expect to return to exactly where they were. Motion should communicate "stepping aside" without implying forward progress.

**Drill-down** (transition 9): The user is navigating deeper into a hierarchy (Settings -> Export Settings). Motion should communicate descent/depth -- going *into* a subsection.

**Back** (transitions 4, 6, 8, 10, 12): The exact reversal of the forward/lateral/drill-down motion. The animation should be the mirror image of the originating transition, reinforcing spatial consistency.

**Reset** (transition 13): A deliberate state-clearing action. The user is returning to the starting point. Motion should communicate finality and a clean break, distinct from a simple "back" navigation.

---

## 2. Compose Animation API Reference

### 2.1 NavHost Transition Parameters

The `NavHost` composable (as of Navigation Compose 2.8+) natively supports animated transitions through four lambda parameters. The separate `AnimatedNavHost` from the Accompanist library is no longer necessary -- its functionality was merged into the standard `NavHost`.

```kotlin
NavHost(
    navController = navController,
    startDestination = Routes.FILE_SELECTION,
    // Global defaults (used when a composable() does not specify its own)
    enterTransition = { /* EnterTransition */ },
    exitTransition = { /* ExitTransition */ },
    popEnterTransition = { /* EnterTransition for pop/back */ },
    popExitTransition = { /* ExitTransition for pop/back */ },
) {
    composable(
        route = Routes.HELP,
        // Per-route overrides (take precedence over NavHost globals)
        enterTransition = { /* ... */ },
        exitTransition = { /* ... */ },
        popEnterTransition = { /* ... */ },
        popExitTransition = { /* ... */ },
    ) {
        HelpScreen(...)
    }
}
```

**Key behavior**: Each transition lambda has an `AnimatedContentTransitionScope<NavBackStackEntry>` receiver that exposes `initialState` and `targetState`, allowing transitions to be conditionally chosen based on the source/destination route pair.

**Fallback chain**: If a `composable()` returns `null` for a transition, the parent `navigation()` graph's transition is used; if that is also null, the `NavHost` global is used. `popEnterTransition` defaults to `enterTransition`, and `popExitTransition` defaults to `exitTransition`, unless explicitly overridden.

### 2.2 Available Transition Primitives

#### Horizontal slides (lateral/drill-down motion)

```kotlin
// Slide new screen in from the right
slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },   // start off-screen right
    animationSpec = tween(300, easing = EaseOut)
)

// Slide old screen out to the left
slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth },   // exit off-screen left
    animationSpec = tween(300, easing = EaseIn)
)

// Container-aware variants (preferred inside NavHost transitions):
slideIntoContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
    animationSpec = tween(300)
)
slideOutOfContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
    animationSpec = tween(300)
)
```

`slideIntoContainer` / `slideOutOfContainer` are convenience wrappers that automatically calculate offsets based on container size and slide direction. They are available directly on the `AnimatedContentTransitionScope` receiver inside NavHost transition lambdas.

#### Vertical slides (overlay/modal-feel)

```kotlin
slideInVertically(
    initialOffsetY = { fullHeight -> fullHeight },  // enter from bottom
    animationSpec = tween(300, easing = EaseOut)
)
slideOutVertically(
    targetOffsetY = { fullHeight -> fullHeight },   // exit to bottom
    animationSpec = tween(300, easing = EaseIn)
)
```

#### Fades

```kotlin
fadeIn(animationSpec = tween(300))
fadeOut(animationSpec = tween(300))
```

#### Scale

```kotlin
scaleIn(
    initialScale = 0.92f,
    animationSpec = tween(300)
)
scaleOut(
    targetScale = 0.92f,
    animationSpec = tween(300)
)
```

#### Combinators (the `+` operator)

Multiple `EnterTransition` or `ExitTransition` objects can be combined with `+`:

```kotlin
// Fade + slide simultaneously
fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 4 })
fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { -it / 4 })

// Fade + scale (fade-through pattern)
fadeIn(tween(150, delayMillis = 150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(150, delayMillis = 150))
fadeOut(tween(150))
```

### 2.3 Animation Specifications

| Spec       | Use case                              | Example                                           |
|------------|---------------------------------------|----------------------------------------------------|
| `tween`    | Fixed duration, easing curve          | `tween(300, easing = FastOutSlowInEasing)`          |
| `spring`   | Physics-based, natural feel           | `spring(dampingRatio = 0.8f, stiffness = 300f)`     |
| `snap`     | Instant, no animation                 | `snap()`                                            |
| `keyframes`| Multi-step custom timing              | `keyframes { durationMillis = 500; ... }`           |

For navigation transitions, `tween` with appropriate easing provides the most predictable, professional results. `spring` can be used but risks feeling bouncy in a business application.

### 2.4 SharedTransitionLayout

`SharedTransitionLayout` enables shared element transitions (e.g., a file list item morphing into a processing card). The API requires wrapping the navigation host in a `SharedTransitionLayout` and annotating elements with `Modifier.sharedElement()` or `Modifier.sharedBounds()`.

**Desktop availability**: `SharedTransitionLayout` was introduced in Compose 1.7.0-alpha07 and is marked `@ExperimentalSharedTransitionApi`. It is available in Compose Multiplatform 1.7.x for Desktop, but with caveats:

- The API is experimental and may change in future releases
- Large-screen layouts (desktop windows) can cause layout conflicts when shared elements with the same keys appear simultaneously
- It requires significant structural changes -- the `SharedTransitionScope` must be threaded through the composable hierarchy
- CPU impact of shared element calculations can be noticeable during transition

**Recommendation for this project**: Defer SharedTransitionLayout to a later phase. The file list to processing card transition is the most natural candidate, but the implementation complexity is disproportionate to the UX benefit for a batch processing tool.

---

## 3. Industry Conventions Survey

### 3.1 macOS System Preferences / Settings

macOS uses a **horizontal slide** for drill-down navigation (e.g., General -> About). The new pane slides in from the right while the current pane slides out to the left. Back navigation reverses the direction. This is the canonical desktop drill-down pattern.

- Duration: approximately 250-350ms
- Easing: ease-in-out (deceleration on entry)
- No vertical movement or scaling

### 3.2 Windows Settings App

Windows 11 Settings uses a **fade + subtle slide** for top-level section changes and a **horizontal slide** for drill-down into sub-sections.

- Top-level changes: fade crossfade (~200ms)
- Drill-down: slide from right (~250ms) with a slight fade
- Back: reverse slide to right

### 3.3 IntelliJ IDEA / Android Studio

IntelliJ uses minimal transition animation for panel/tab switching -- content appears with a very fast fade or instant switch. Settings dialogs show content inline with no slide animation. This reflects a power-user tool aesthetic: speed over motion design.

### 3.4 Material Design Motion Guidelines

Material Design 3 defines four primary transition patterns for navigation:

| Pattern              | When to use                                     | Motion                                    |
|----------------------|-------------------------------------------------|-------------------------------------------|
| **Shared axis**      | Navigating between related content (tabs, steps) | Slide + fade along a shared x/y/z axis    |
| **Fade through**     | Switching between unrelated content              | Sequential fade out, scale down, then fade in, scale up |
| **Container transform** | Element expands into a full-screen destination | Source element morphs into destination     |
| **Fade**             | Elements entering/exiting within screen bounds  | Simple opacity change                      |

**Shared axis** is the recommended pattern for:
- Forward/backward workflow steps (x-axis: horizontal slide + fade)
- Hierarchical drill-down (x-axis: horizontal slide + fade)
- Vertically-related content (y-axis: vertical slide + fade)

**Fade through** is recommended for:
- Switching between unrelated top-level destinations
- Screen transitions where there is no strong spatial relationship

### 3.5 Material Design 3 Duration and Easing Tokens

**Duration tokens** (recommended ranges for this application):

| Token              | Value  | Appropriate for                                |
|--------------------|--------|------------------------------------------------|
| Short3             | 150ms  | Micro-interactions, small element changes       |
| Short4             | 200ms  | Fast, snappy transitions                        |
| Medium1            | 250ms  | Standard navigation transitions                 |
| Medium2            | 300ms  | Standard navigation transitions (current value) |
| Medium3            | 350ms  | Slightly more deliberate transitions             |
| Medium4            | 400ms  | Full-screen transitions with larger traversal    |

**Easing curves** (M3 names mapped to Compose constants):

| M3 Name                  | Compose Equivalent              | Cubic Bezier (approx.)        | Use case                            |
|--------------------------|---------------------------------|-------------------------------|--------------------------------------|
| Standard                 | `FastOutSlowInEasing`           | (0.4, 0.0, 0.2, 1.0)         | General-purpose, moving on-screen    |
| Standard decelerate      | `LinearOutSlowInEasing`         | (0.0, 0.0, 0.2, 1.0)         | Elements entering the screen         |
| Standard accelerate      | `FastOutLinearInEasing`         | (0.4, 0.0, 1.0, 1.0)         | Elements leaving the screen          |
| Emphasized decelerate    | `DecelerateEasing` / custom     | (0.05, 0.7, 0.1, 1.0)        | Entering elements that need emphasis |
| Emphasized accelerate    | custom `CubicBezierEasing`      | (0.3, 0.0, 0.8, 0.15)        | Exiting elements that need emphasis  |
| Linear                   | `LinearEasing`                  | (0.0, 0.0, 1.0, 1.0)         | Color/opacity changes only           |

For this application, `FastOutSlowInEasing` (Standard) is the primary easing for most transitions. `LinearOutSlowInEasing` (Standard decelerate) is appropriate for elements entering the screen, and `FastOutLinearInEasing` (Standard accelerate) for elements leaving.

---

## 4. Proposed Animation Map

### 4.1 Design Principles

1. **Horizontal slides communicate spatial relationships**: Forward workflow and drill-down use left-to-right / right-to-left motion along the x-axis (shared axis pattern).
2. **Vertical slides communicate overlay/modal relationships**: Help and Settings screens slide up from below, communicating that they are *on top of* the current context, not replacing it.
3. **Fades soften transitions**: All slides are combined with a subtle fade to avoid hard-edge "cut" artifacts and to match the brand's warm, clarifying aesthetic.
4. **Back = exact reversal**: Pop transitions are always the mirror of the forward transition, maintaining spatial consistency.
5. **Reset is distinct**: The "Start Over" action uses a fade-through pattern (no directional slide) to communicate a clean break rather than spatial movement.
6. **Duration matches traversal distance**: Short distances (overlay slide) use shorter durations; full-screen replacements use slightly longer durations.
7. **Desktop-appropriate restraint**: Animations are subtle and quick. A business tool used 50+ times per day should not feel like a mobile app demo.

### 4.2 Concrete Animation Specifications

#### Constants

```kotlin
object NavAnimations {
    // Durations (Material Design 3 tokens)
    const val DURATION_STANDARD = 300    // Medium2 -- primary nav transitions
    const val DURATION_FAST = 200        // Short4 -- quick overlays
    const val DURATION_FADE = 150        // Short3 -- fade component of combined animations
    const val DURATION_RESET = 400       // Medium4 -- deliberate reset transition

    // Slide offset fractions
    const val SLIDE_OFFSET_FULL = 1.0f   // Full-width slide (drill-down)
    const val SLIDE_OFFSET_PARTIAL = 0.25f // Partial slide (fade-through hint)
}
```

#### Transition 1: FILE_SELECTION -> PROCESSING (Forward workflow)

| Parameter        | Transition                                                      | Rationale                                  |
|------------------|-----------------------------------------------------------------|--------------------------------------------|
| `enterTransition`| `fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 3 }, tween(300, easing = FastOutSlowInEasing))` | Forward momentum -- new screen arrives from the right with a fade-in. Partial offset (1/3) keeps it subtle. |
| `exitTransition` | `fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { -it / 3 }, tween(300, easing = FastOutSlowInEasing))` | Current screen recedes to the left, fading out quickly. |

#### Transition 2: PROCESSING -> RESULTS (Forward workflow, auto-advance)

| Parameter        | Transition                                                      | Rationale                                  |
|------------------|-----------------------------------------------------------------|--------------------------------------------|
| `enterTransition`| `fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 3 }, tween(300, easing = FastOutSlowInEasing))` | Continues the same forward momentum established in transition 1. Consistency across the workflow. |
| `exitTransition` | `fadeOut(tween(200))` | Processing screen simply fades away (no slide) since it is being removed from the back stack via `popUpTo`. A directional exit would imply it can be returned to. |

#### Transitions 3-4: FILE_SELECTION <-> HELP (Lateral overlay)

**Forward (Main -> Help):**

| Parameter        | Transition                                                      | Rationale                                  |
|------------------|-----------------------------------------------------------------|--------------------------------------------|
| `enterTransition`| `fadeIn(tween(250, easing = LinearOutSlowInEasing)) + slideInVertically(initialOffsetY = { it / 4 }, tween(250, easing = LinearOutSlowInEasing))` | Help slides up from below -- communicates "overlay on top of current context." Partial offset (1/4) and decelerate easing make it feel like a panel sliding into view. |
| `exitTransition` | `fadeOut(tween(200, easing = FastOutLinearInEasing))` | Main screen fades out behind the Help overlay. No slide -- it stays in place conceptually. |

**Back (Help -> Main):**

| Parameter          | Transition                                                      | Rationale                                  |
|--------------------|-----------------------------------------------------------------|--------------------------------------------|
| `popEnterTransition`| `fadeIn(tween(250, easing = LinearOutSlowInEasing))` | Main fades back in as Help withdraws. |
| `popExitTransition` | `fadeOut(tween(200, easing = FastOutLinearInEasing)) + slideOutVertically(targetOffsetY = { it / 4 }, tween(250, easing = FastOutLinearInEasing))` | Help slides back down and fades out -- exact reversal of the entry. |

#### Transitions 5-6: RESULTS <-> HELP (Lateral overlay)

Same animation specification as transitions 3-4. The Help screen always enters/exits the same way regardless of origin, providing consistency.

#### Transitions 7-8: FILE_SELECTION <-> SETTINGS (Lateral overlay)

Same animation specification as transitions 3-4 (vertical slide-up). Settings is a contextual detour just like Help.

#### Transitions 9-10: SETTINGS <-> EXPORT_SETTINGS (Drill-down)

**Forward (Settings -> Export Settings):**

| Parameter        | Transition                                                      | Rationale                                  |
|------------------|-----------------------------------------------------------------|--------------------------------------------|
| `enterTransition`| `fadeIn(tween(300, easing = LinearOutSlowInEasing)) + slideInHorizontally(initialOffsetX = { it }, tween(300, easing = FastOutSlowInEasing))` | Full-width slide from right -- the canonical desktop drill-down pattern (cf. macOS System Settings). Communicates going deeper into a hierarchy. |
| `exitTransition` | `fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { -it / 3 }, tween(300, easing = FastOutSlowInEasing))` | Settings partially slides left and fades. Partial offset communicates it is still "there" behind the sub-screen. |

**Back (Export Settings -> Settings):**

| Parameter          | Transition                                                      | Rationale                                  |
|--------------------|-----------------------------------------------------------------|--------------------------------------------|
| `popEnterTransition`| `fadeIn(tween(300, easing = LinearOutSlowInEasing)) + slideInHorizontally(initialOffsetX = { -it / 3 }, tween(300, easing = FastOutSlowInEasing))` | Settings slides back in from the left (mirror of its exit). |
| `popExitTransition` | `fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { it }, tween(300, easing = FastOutSlowInEasing))` | Export Settings slides fully off to the right (mirror of its entry). |

#### Transitions 11-12: RESULTS <-> EXPORT_SETTINGS (Lateral + drill-down hybrid)

When navigating from Results to Export Settings, the user is stepping aside from the results view into a configuration screen. This is semantically lateral (like Help/Settings), not a drill-down from a parent. However, Export Settings is hierarchically a sub-screen.

**Recommendation**: Use the same **vertical slide-up** as Help/Settings for consistency. The user is accessing Export Settings as a tool from the Results toolbar, not navigating down a hierarchy. This matches the user mental model: "I want to adjust settings, then go back to my results."

Same animation specification as transitions 3-4 (lateral overlay).

#### Transition 13: RESULTS -> FILE_SELECTION (Reset / Start Over)

| Parameter          | Transition                                                      | Rationale                                  |
|--------------------|-----------------------------------------------------------------|--------------------------------------------|
| `popEnterTransition`| `fadeIn(tween(400, easing = LinearOutSlowInEasing)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400, easing = LinearOutSlowInEasing))` | Main screen fades in with a subtle scale-up (95% -> 100%). This is a **fade-through** pattern -- no directional slide. Communicates: "starting fresh" rather than "going back somewhere." The slightly longer duration (400ms) adds deliberateness to the reset action. |
| `popExitTransition` | `fadeOut(tween(250, easing = FastOutLinearInEasing)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250, easing = FastOutLinearInEasing))` | Results screen fades and scales down slightly before disappearing. |

### 4.3 Summary Table

| Transition                    | Pattern          | Enter Effect           | Exit Effect            | Duration |
|-------------------------------|------------------|------------------------|------------------------|----------|
| Main -> Processing            | Forward (x-axis) | Slide-in right + fade  | Slide-out left + fade  | 300ms    |
| Processing -> Results         | Forward (x-axis) | Slide-in right + fade  | Fade out               | 300ms    |
| Any -> Help                   | Overlay (y-axis) | Slide-up + fade        | Fade out               | 250ms    |
| Help -> Any (back)            | Overlay (y-axis) | Fade in                | Slide-down + fade      | 250ms    |
| Main -> Settings              | Overlay (y-axis) | Slide-up + fade        | Fade out               | 250ms    |
| Settings -> Main (back)       | Overlay (y-axis) | Fade in                | Slide-down + fade      | 250ms    |
| Settings -> Export Settings   | Drill-down (x)   | Full slide-in right    | Partial slide-out left | 300ms    |
| Export Settings -> Settings   | Drill-up (x)     | Partial slide-in left  | Full slide-out right   | 300ms    |
| Results -> Export Settings    | Overlay (y-axis) | Slide-up + fade        | Fade out               | 250ms    |
| Export Settings -> Results    | Overlay (y-axis) | Fade in                | Slide-down + fade      | 250ms    |
| Results -> Main (reset)       | Fade-through     | Fade + scale-in        | Fade + scale-out       | 400ms    |

---

## 5. Implementation Plan

### 5.1 Complexity Assessment

| Tier | Transitions | Approach | Complexity |
|------|-------------|----------|------------|
| **Tier 1: Simple** | Forward workflow (Main->Processing->Results) | Per-route `enterTransition`/`exitTransition` on `composable()` calls | Low -- standard `slideInHorizontally` + `fadeIn` |
| **Tier 1: Simple** | Lateral overlays (Help, Settings) | Per-route `enterTransition`/`exitTransition` using `slideInVertically` | Low -- same pattern, vertical axis |
| **Tier 2: Moderate** | Drill-down (Settings->ExportSettings) | Conditional transitions inside `composable()` lambdas that check `initialState`/`targetState` route | Moderate -- requires route-aware transition logic |
| **Tier 2: Moderate** | Reset (Results->Main via Start Over) | `popEnterTransition`/`popExitTransition` on FILE_SELECTION composable, conditionally checking if popping from RESULTS | Moderate -- requires distinguishing "back from Help" vs "reset from Results" |
| **Tier 3: Advanced** | Shared element transitions (file list -> processing cards) | `SharedTransitionLayout` wrapping `NavHost`, `Modifier.sharedElement()` on source/target | High -- experimental API, structural refactor, Desktop rendering concerns |

### 5.2 Key Implementation Challenge: Route-Aware Transitions

Several transitions require the animation to vary based on *where* the user is coming from or going to. For example:

- `FILE_SELECTION` enters differently when navigating back from Help (fade in) vs. when resetting from Results (fade + scale-in)
- `EXPORT_SETTINGS` enters differently when coming from Settings (horizontal drill-down) vs. from Results (vertical overlay)

This is handled by inspecting `initialState.destination.route` and `targetState.destination.route` inside the transition lambdas:

```kotlin
composable(
    route = Routes.EXPORT_SETTINGS,
    enterTransition = {
        when (initialState.destination.route) {
            Routes.SETTINGS -> {
                // Drill-down: full horizontal slide from right
                fadeIn(tween(300)) + slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            }
            else -> {
                // Lateral overlay from Results: vertical slide up
                fadeIn(tween(250)) + slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(250, easing = LinearOutSlowInEasing)
                )
            }
        }
    },
    popExitTransition = {
        when (targetState.destination.route) {
            Routes.SETTINGS -> {
                // Drill-up: full horizontal slide to right
                fadeOut(tween(200)) + slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            }
            else -> {
                // Lateral: slide back down
                fadeOut(tween(200)) + slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(250, easing = FastOutLinearInEasing)
                )
            }
        }
    },
    // ...
)
```

### 5.3 Key Implementation Challenge: Reset vs. Back on FILE_SELECTION

The `FILE_SELECTION` screen needs to distinguish between:
- **Back from Help/Settings**: fade in (overlay withdrawal)
- **Reset from Results**: fade + scale-in (fresh start)

This requires a route-aware `popEnterTransition`:

```kotlin
composable(
    route = Routes.FILE_SELECTION,
    popEnterTransition = {
        when (initialState.destination.route) {
            Routes.RESULTS -> {
                // Reset: fade-through with scale
                fadeIn(tween(400, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(400))
            }
            else -> {
                // Normal back from overlay: just fade in
                fadeIn(tween(250, easing = LinearOutSlowInEasing))
            }
        }
    },
    // ...
)
```

### 5.4 Recommended Implementation Order

**Phase 1 -- Foundation (low risk, high impact):**
1. Remove global `fadeIn`/`fadeOut` from `NavHost`
2. Add per-route `enterTransition`/`exitTransition` for the forward workflow (Main -> Processing -> Results)
3. Add vertical overlay transitions for Help and Settings screens
4. Test all transitions on Windows (primary target)

**Phase 2 -- Refinement (moderate complexity):**
5. Add route-aware drill-down transitions for Export Settings (horizontal from Settings, vertical from Results)
6. Add route-aware reset transition for the Start Over flow (Results -> Main)
7. Fine-tune durations and easing values through manual testing

**Phase 3 -- Polish (optional, high complexity):**
8. Evaluate `SharedTransitionLayout` for the file list -> processing card transition
9. Consider `AnimatedContent` for inline content transitions (e.g., error/success states on ResultsScreen)

### 5.5 Code Organization

Create a dedicated file for transition definitions to keep `Main.kt` clean:

```
app/src/main/kotlin/tech/carbonworks/snc/batchreferralparser/ui/navigation/
    NavTransitions.kt    -- Transition constants and factory functions
```

This file would export functions like:

```kotlin
object NavTransitions {
    fun forwardEnter(): EnterTransition = ...
    fun forwardExit(): ExitTransition = ...
    fun overlayEnter(): EnterTransition = ...
    fun overlayExit(): ExitTransition = ...
    fun overlayPopEnter(): EnterTransition = ...
    fun overlayPopExit(): ExitTransition = ...
    fun drillDownEnter(): EnterTransition = ...
    fun drillDownExit(): ExitTransition = ...
    fun drillUpEnter(): EnterTransition = ...
    fun drillUpExit(): ExitTransition = ...
    fun resetEnter(): EnterTransition = ...
    fun resetExit(): ExitTransition = ...
}
```

### 5.6 No Dependency Changes Required

The current `NavHost` from `navigation-compose-desktop:2.8.0-alpha10` already supports all four transition parameters (`enterTransition`, `exitTransition`, `popEnterTransition`, `popExitTransition`) on both the `NavHost` itself and individual `composable()` calls. The animation primitives (`slideInHorizontally`, `fadeIn`, `scaleIn`, etc.) are part of `compose.animation`, which is already available through the `compose.desktop.currentOs` dependency. No new libraries are needed.

### 5.7 Desktop-Specific Considerations

- **CPU overhead**: Desktop Compose uses Skia for rendering. Complex slide + fade combos are well-optimized, but shared element transitions can spike CPU usage during transition frames. Keep transitions simple.
- **Window resizing**: Slide animations that use absolute pixel offsets will adapt to current window size since offset lambdas receive `fullWidth`/`fullHeight` at animation time.
- **High refresh rate displays**: `tween` durations are frame-rate independent. A 300ms tween looks the same at 60Hz and 144Hz.
- **Accessibility**: Users who prefer reduced motion (OS-level setting) are not currently detectable in Compose Desktop. Consider adding an in-app "Reduce motion" toggle in Settings if user feedback indicates this is needed.

---

## 6. Alignment with Brand Guidelines

The Carbon Works brand guidelines emphasize:

- **Warm and clarifying**: Animations should feel natural, not flashy. The proposed durations (200-400ms) are in the "perceptible but not slow" range.
- **Clean and uncluttered**: Motion is used to clarify spatial relationships, not to decorate. Every animation has a semantic purpose.
- **Crafted, not corporate**: The combination of slide + fade (rather than instant cuts or aggressive scaling) matches the "intentional design choices" principle.
- **Fast load times matter more than animations**: All transitions are under 400ms. The forward workflow (Main -> Processing -> Results) uses the standard 300ms to avoid slowing down the primary task.

The proposed animation system serves the brand by making the application feel more polished and intentional without drawing attention to itself -- "motion that clarifies, not motion that decorates."
