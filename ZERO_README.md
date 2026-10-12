# ZERO Native Mario — private Android NDK compile

This is a **PRIVATE** compilation workspace for user-generated native C++ from an owned SMNE01 revision 2 dump. The public `zero-dolphin-lab` repository is deliberately not used for game-derived source.

The `zero-rev2-source-v1` GitHub Release holds **only** generated `.cpp`/`.h` files from the three directories `base_common`, `base_registration`, and `base_dispatch`, plus generated `RuntimeConfig.h` and a file/hash inventory. It must not contain WBFS/ISO/RVZ, main.dol, REL files, Nintendo art/audio/textures, memory initializer data, or saved games.

`.github/workflows/zero-native-android.yml` uses a GitHub-hosted Linux **x86-64** runner with the actual Android NDK 29 toolchain to compile C++ into AArch64 Android objects. These are **unoptimized (-O0)** linkage prerequisites. The resulting archive is **not a game, libmain.so, or an APK**. Native hook ABI compatibility and Mali/Vulkan remain unverified.

Github Actions build logs and files are accessible to collaborators on this private repository. Limit repository access and remove the Release/Actions artifacts when no longer needed. Anyone with sufficient GitHub repository permissions may retrieve private release assets.
