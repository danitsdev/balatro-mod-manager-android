# Third-party notices

## m6x11plus

- Author: Daniel Linssen
- Source: https://managore.itch.io/m6x11
- License/terms: free to use with attribution, according to the author's distribution page.
- Use in this project: UI font asset for the Balatro-inspired Android interface.

## Balatro Mod Manager upstream reference

- Repository: https://github.com/skyline69/balatro-mod-manager
- License observed: GPL-3.0
- Use in this project: direct product inspiration, GPL-compatible reference for manager behavior, and source of the animated background formula ported from GLSL/Svelte to Android AGSL.
- Mobile scope: this project is a native Android implementation for Lovely Mobile Maker builds and Android Storage Access Framework.
- Porting policy: behavior or code may be ported when license-compatible, attributed, and reviewed for Android/LMM constraints.

## Balatro Mod Index

- Repository: https://github.com/skyline69/balatro-mod-index
- License observed: MIT
- Use in this project: bundled normalized catalog snapshot for mod metadata, search, tags, dependency flags, versions, folder names, and download URLs.
- Note: individual mods listed by the index may have their own licenses and upstream terms.

## Balatro Mod Index API / BMI

- Service observed from Balatro Mod Manager desktop: https://api-bmi.dasguney.com
- Use in this project: optional catalog-builder enrichment for thumbnails, download counts, summaries, and BMI-only mods with HTTPS download URLs.
- Runtime policy: the Android app is online-first, synchronizes catalog metadata with the BMI API, keeps a private cache, and uses the bundled snapshot as bootstrap/fallback.

## Coil

- Repository: https://github.com/coil-kt/coil
- Module: `io.coil-kt:coil-compose`
- License observed: Apache-2.0
- Use in this project: native Compose image loading and caching for remote mod thumbnails.

## Kotlin Multiplatform Markdown Renderer

- Repository: https://github.com/mikepenz/multiplatform-markdown-renderer
- Version: 0.30.0
- License observed: Apache-2.0
- Use in this project: native Compose rendering of mod descriptions, including headings, lists, emphasis, links, and line breaks.
