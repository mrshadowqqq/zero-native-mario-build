# ZERO Native Mario — Credits and Source Information

**Project customization and Android ARM64 optimization:** Project ZERO.

ZERO Native Mario is an independent, modified development build based on existing open-source technology. The project owner controls the ZERO modifications, settings, optimizations and private build workflow. Upstream code and third-party assets retain their respective authors, licenses and rights.

## Principal software sources

- **New Super Mario Bros. Wii native recompilation port and Android launcher:** WitherBlitz, [NSMB-Wii-Multiplayer-Recompilation](https://github.com/WitherBlitz/NSMB-Wii-Multiplayer-Recompilation). Upstream repository states this port was authored with the help of Claude Opus. The upstream code is marked GPL-3.0; follow its exact LICENSE and THIRD-PARTY-NOTICES.
- **WiiCompiled static recompilation toolchain and runtime foundations:** patchzyy and its contributors, [WiiCompiled](https://github.com/patchzyy/Wiicompiled). Upstream describes the toolchain as GPL-3.0.
- **Aurora Wii/GameCube graphics compatibility and renderer:** Luke Street / encounter and contributors, [Aurora](https://github.com/encounter/aurora), MIT license.
- **Dolphin Emulator Project:** [Dolphin](https://github.com/dolphin-emu/dolphin). Used as a behavioral reference; some upstream WiiCompiled support data (including DSP coefficients and Wii bootstrap files) is credited as GPL-2.0-or-later in the upstream third-party notice. The separate ZERO Dolphin optimization project is not the runtime executing this native port.
- **Additional dependencies:** SDL3, Dawn/WebGPU, pugixml, Crypto++, toml11, mbedTLS, fmt, xxHash and other dependencies remain credited in the upstream [THIRD-PARTY-NOTICES.md](https://github.com/WitherBlitz/NSMB-Wii-Multiplayer-Recompilation/blob/main/THIRD-PARTY-NOTICES.md), with their respective license texts.

## Game rights and independent status

**New Super Mario Bros. Wii** and its game code, graphics, names, audio, characters and trademarks belong to Nintendo and other applicable rightsholders. Project ZERO does not claim authorship or ownership of the Nintendo game, original software infrastructure, or upstream contributors' contributions. This is not an official or endorsed Nintendo release.

This experimental build uses game files supplied by the owner for private development. No game resources are included in this credits document or its source patch.

## Licensing / distribution

Retain applicable GPLv3 source-license and copyright notices for the covered native port; preserve the copyright/license notices for MIT, GPL-2.0-or-later and other dependencies. Publishing or conveying binaries of a GPL-covered derivative can require making the corresponding source available under GPL terms. Private development is distinct from distributing an APK. Bundling proprietary game code/assets raises additional issues not resolved by the free software licenses.

For full licenses, consult the repositories and their unmodified LICENSE and THIRD-PARTY-NOTICES documents. A short startup credit cannot replace required full license and source notices.

## This ZERO change set

- ZERO branding replaces the old author-specific splash title; upstream authorship is still accurately credited.
- Android-only initial cover shortens from 60 presented frames to eight **after** the game has submitted a visible frame. The shader-readiness gate remains intact.
- Constant credits wrapping is cached and invalidated when the viewport width changes. This removes repeated startup UI text-layout allocations only; it is **not a measured gameplay FPS improvement**.
- Credits remain accessible from the simplified ZERO launcher under **Settings > About & Source Credits**.
- All original compiled Wii game objects, game data and existing runtime candidates are preserved.
