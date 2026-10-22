# Contributing to Plasma

Thanks for helping. A few ground rules first.

## What Plasma won't accept

Plasma plays services people already pay for. The repository never holds playlists, portal addresses, provider names or credentials, not even as test data. Issues and pull requests that add, request or link to any of these are closed. Test fixtures use the made-up channels, programs and servers in `core/fixtures`, which follow appendix A of the [design brief](docs/DESIGN.md).

## Before you start

- Read [docs/DESIGN.md](docs/DESIGN.md). It's the source of truth for what Plasma does and how it behaves. When the brief and a board disagree on visuals, the board wins; on behavior, the brief wins.
- Open an issue before starting anything large, so we can agree on the approach.

## Working on the code

- JDK 21 and the Android SDK (platform 37). See the README for the Gradle tasks.
- Kotlin with Jetpack Compose. Follow the official Kotlin code style (`kotlin.code.style=official`).
- All visual sizes come from the 1920 × 1080 boards: **dp = px ÷ 2** and **sp = px ÷ 2**. Write board values with the `.px` and `.pxSp` helpers (`96.px` is 48 dp). Use the colors, type and components in `core/design` rather than raw values; `PlasmaColors.white(.22f)` and friends stand for the boards' `rgba()` colors.
- User-facing text goes in string resources, in American English. Follow the copy rules in section 2.10 of the brief: Title Case for titles, buttons and settings rows; sentence case for descriptions and hints; 24-hour times such as "20:41"; ranges with an en dash, "19:45–22:00".
- Every screen has a screenshot test. If you change a screen on purpose, run `./gradlew recordRoborazziDebug` and commit the updated images with your change.
- Everything must work with the D-pad, OK, Back and Home alone. Color keys, number keys and channel keys are shortcuts, never the only way in.

## Licenses

| Folder | License |
|---|---|
| everything except the folders below | GPL-3.0-or-later |
| `remote-protocol/`, `phone/` (when added) | Apache-2.0 |
| `core/design/src/main/res/font`, `docs/design/fonts` | SIL Open Font License 1.1 |

New source files start with an SPDX line, for example `// SPDX-License-Identifier: GPL-3.0-or-later`.

## Reporting problems

Use the issue forms. Say which device and Android version you have, the source type (never its address or your login), the player engine and your sound setup. Plasma's diagnostics export removes addresses, usernames, passwords and tokens before it leaves the TV.
