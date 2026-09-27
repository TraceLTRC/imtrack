# Native Android (Kotlin + Jetpack Compose) first, not cross-platform

imtrack is built as a native Android app. Tracking has to be possible without friction, so it relies on Android features like an ongoing notification with a Stop button, home-screen widgets and Quick Settings tiles. An installable PWA can't provide those. The app is mainly for its author, who has no plans for iOS, so a cross-platform framework (Expo/React Native, Flutter) would add cost with no benefit.

## Considered Options

- **Installable PWA**: works on every platform and offline, but has no widgets, tiles or persistent notification controls.
- **Expo / React Native**: gives iOS and web later, but needs native modules for widgets and tiles anyway, and iOS isn't wanted.

## Consequences

A PC client, if one is ever built, will be a separate app that talks to a future sync backend (see ADR-0002). It will not share UI code with the Android app.
