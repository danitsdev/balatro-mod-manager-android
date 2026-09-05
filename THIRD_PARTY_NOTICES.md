# Third-party notices

## m6x11plus

- Author: Daniel Linssen
- Source: https://managore.itch.io/m6x11
- License/terms: free to use with attribution, according to the author's distribution page.
- Use in this project: UI font asset for the Balatro-inspired Android interface.

## Balatro Mod Manager upstream reference

- Repository: https://github.com/skyline69/balatro-mod-manager
- License observed: GPL-3.0
- Use in this project: direct product inspiration, GPL-compatible reference for manager behavior, and source of the animated background formula ported to Android AGSL.
- Mobile scope: this project is a native Android implementation for Lovely Mobile Maker builds and Android Storage Access Framework.
- Porting policy: behavior or code may be ported when license-compatible, attributed, and reviewed for Android/LMM constraints.

## Balatro Mod Index

- Repository: https://github.com/skyline69/balatro-mod-index
- License observed: MIT
- Use in this project: source index for mod metadata exposed through the BMI API.
- Note: individual mods listed by the index may have their own licenses and upstream terms.

## Balatro Mod Index API / BMI

- Service observed from Balatro Mod Manager desktop: https://api-bmi.dasguney.com
- Use in this project: online catalog, thumbnails, download counts, descriptions, and mod download URLs.
- Runtime policy: the Android app synchronizes directly with the BMI API and keeps the last valid response in a private cache for faster startup and offline fallback.

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

## jsoup

- Repository: https://github.com/jhy/jsoup
- Version: 1.20.1
- License observed: MIT
- Use in this project: HTML parsing and conversion for catalog descriptions.

## AndroidX and Jetpack Compose

- Repository: https://github.com/androidx/androidx
- License observed: Apache-2.0
- Use in this project: Android application lifecycle, interface, preferences, and Storage Access Framework integration.

## Kotlin and kotlinx libraries

- Repository: https://github.com/JetBrains/kotlin
- License observed: Apache-2.0
- Use in this project: application language, coroutines, and JSON serialization.
