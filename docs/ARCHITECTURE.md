# Architecture

The app is written in Kotlin and Jetpack Compose. It manages game files through Android's Storage Access Framework (SAF), using the folder permission granted by the user.

## Code map

    catalog/      Thunderstore API, cache, search, and deduplication
    domain/       catalog and local mod matching, update rules
    installer/    local scanning, archive validation, install, update, removal
    settings/     DataStore preferences
    storage/      SAF access and game-folder validation
    ui/           Compose screens, cards, themes, and backgrounds

MainViewModel holds screen state and starts catalog and storage operations. Repositories handle network and file work; Compose screens display the resulting state.

## Catalog

The app loads its cached catalog at startup and refreshes it when it is older than one hour or when the user pulls to refresh. If a refresh fails, the last valid catalog remains available. Cache entries include a schema version so incompatible snapshots are discarded.

Catalog cards use the short description from the latest Thunderstore package version. The detail screen loads the full README for the selected version from Thunderstore. If no full README is available, it shows that version's description. The app normalizes common Thunderstore HTML and renders Markdown and README images.

The version list contains package metadata. Before installation, the app refreshes package details and resolves the selected version's download and dependency list against the current Thunderstore record.

The app filters known packages that cannot be installed or used on the supported mobile build:

- r2modman and Gale are desktop mod managers.
- Lovely is bundled in the supported Lovely Mobile Maker build.
- MultiplayerAPI and its speedrun add-on require Steam authentication.
- balatroVS has a Windows-only Thunderstore archive; its Android build is distributed separately.

These filters use package IDs. Categories are not treated as compatibility data. Lovely remains satisfied when another package lists it as a dependency.

Catalog search, sorting, matching, and version rules live outside the UI. Card size selects a three-, two-, or one-column layout. The default is two columns; wider cards use a horizontal arrangement for their description and actions.

Dependencies are read from the selected version. The detail screen shows each requested package and version, links packages to their catalog pages, and checks installed mods. GitHub Releases are checked at most once a day; a newer release appears as a dismissible notice.

## Storage and installed mods

The user creates a game build with Lovely Mobile Maker, opens it once, then selects the parent folder containing ASET in the Android document picker. The app keeps that SAF permission and only manages files in ASET/Mods.

Thunderstore downloads and imported ZIPs pass through the same archive inspection before files are copied into the game folder. Imports can contain one mod or multiple mod folders. A manifest-only modpack cannot be imported because it does not include mod files.

The **Installed** screen includes:

- **Managed** mods installed by the app, with a private manifest used for version tracking.
- **Local** mods found in ASET/Mods without a matching manifest.
- **Get official** when a local mod matches a Thunderstore package.
- **Update** when a managed mod has a newer catalog version.

The app reads enablement from .lovelyignore markers and the Steamodded blacklist. Refreshing rescans the game folder. Local and imported mods can be enabled, disabled, and removed, but have no automatic update source.

## Local checks

Run the checks before opening a pull request:

    ./gradlew testDebugUnitTest lintDebug assembleDebug
