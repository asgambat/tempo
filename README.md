<p align="center">
  <img alt="Andropausa" title="Andropausa" src="mockup/svg/horizontal_logo.svg" width="250">
</p>

<p align="center">
  <b>Access your music library on all your android devices</b>
</p>

## Authors

**aniello sgambato** - Andropausa Fork Maintainer

**Andropausa** is an open-source and lightweight music client for Subsonic, designed and built natively for Android. It provides a seamless and intuitive music streaming experience, allowing you to access and play your Subsonic music library directly from your Android device.

Andropausa does not rely on magic algorithms to decide what you should listen to. Instead, the interface is built around your listening history, randomness, and optionally integrates with services like Last.fm to personalize your music experience.

## Features
- **Subsonic Integration**: Andropausa seamlessly integrates with your Subsonic server, providing you with easy access to your entire music collection on the go.
- **Sleek and Intuitive UI**: Enjoy a clean and user-friendly interface designed to enhance your music listening experience, tailored to your preferences and listening history.
- **Browse and Search**: Easily navigate through your music library using various browsing and searching options, including artists, albums, genres, playlists, decades and more.
- **Streaming and Offline Mode**: Stream music directly from your Subsonic server. Offline mode is currently under active development and may have limitations when using multiple servers.
- **Playlist Management**: Create, edit, and manage playlists to curate your perfect music collection.
- **Gapless Playback**: Experience uninterrupted playback with gapless listening mode.
- **Chromecast Support**: Stream your music to Chromecast devices. The support is currently in a rudimentary state.
- **Scrobbling Integration**: Optionally integrate Andropausa with Last.fm to scrobble your played tracks, gather music insights, and further personalize your music recommendations, if supported by your Subsonic server.
- **Podcasts and Radio**: If your Subsonic server supports it, listen to podcasts and radio shows directly within Andropausa, expanding your audio entertainment options.
- **Transcoding Support**: Activate transcoding of tracks on your Andropausa server, allowing you to set a transcoding profile for optimized streaming directly from the app. This feature requires support from your Subsonic server.
- **Android Auto Support**: Enjoy your favorite music on the go with full Android Auto integration, allowing you to seamlessly control and listen to your tracks directly from your mobile device while driving.

<div style="text-align: center; margin: 20px 0;">
  <img src="mockup/feat/1_screenshot.png" width=200 alt="Andropausa Feature Screenshot 1">
  <img src="mockup/feat/2_screenshot.png" width=200 alt="Andropausa Feature Screenshot 2">
  <img src="mockup/feat/3_screenshot.png" width=200 alt="Andropausa Feature Screenshot 3">
  <img src="mockup/feat/4_screenshot.png" width=200 alt="Andropausa Feature Screenshot 4">
  <img src="mockup/feat/5_screenshot.png" width=200 alt="Andropausa Feature Screenshot 5">
  <img src="mockup/feat/6_screenshot.png" width=200 alt="Andropausa Feature Screenshot 6">
  <img src="mockup/feat/7_screenshot.png" width=200 alt="Andropausa Feature Screenshot 7">
  <img src="mockup/feat/8_screenshot.png" width=200 alt="Andropausa Feature Screenshot 8">
</div>


## Screenshot

<h2 style="text-align: center; color: #333; margin: 20px 0;">Light theme</h2>

<div style="text-align: center; margin: 20px 0;">
  <img src="mockup/light/1_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 1">
  <img src="mockup/light/2_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 2">
  <img src="mockup/light/3_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 3">
  <img src="mockup/light/4_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 4">
  <img src="mockup/light/5_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 5">
  <img src="mockup/light/6_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 6">
  <img src="mockup/light/7_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 7">
  <img src="mockup/light/8_screenshot.png" width=200 alt="Andropausa Light Theme Screenshot 8">
</div>

<br>

<h2 style="text-align: center; color: #333; margin: 20px 0;">Dark theme</h2>

<div style="text-align: center; margin: 20px 0;">
  <img src="mockup/dark/1_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 1">
  <img src="mockup/dark/2_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 2">
  <img src="mockup/dark/3_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 3">
  <img src="mockup/dark/4_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 4">
  <img src="mockup/dark/5_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 5">
  <img src="mockup/dark/6_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 6">
  <img src="mockup/dark/7_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 7">
  <img src="mockup/dark/8_screenshot.png" width=200 alt="Andropausa Dark Theme Screenshot 8">
</div>

## Development

Andropausa is a single-module Android app (Gradle in Groovy DSL, View-based UI with ViewBinding, no DI framework). The developer documentation is versioned in this repository and must be kept up to date with every iteration:

- [`AGENTS.MD`](AGENTS.MD) — working rules, conventions and per-task checklists for agentic/iterative development. **Start here.**
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layers, data flow, playback pipeline, Subsonic client, database, offline mode, Android Auto.
- [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) — coding/naming conventions adopted by the project, with code patterns.
- [`docs/BUILD.md`](docs/BUILD.md) — build flavors, signing, CI and release checklist.

Quick build (JDK 17 required):

```bash
./gradlew assembleAndropausaDebug        # GitHub / F-Droid flavor
./gradlew assembleNotquitemyDebug   # F-Droid variant (no Cast)
./gradlew assemblePlayDebug         # Play Store flavor
```

A root [`Taskfile.yml`](Taskfile.yml) ([go-task](https://taskfile.dev)) wraps these lifecycle commands — run `task --list`.


## Fork Modifications

This fork (Andropausa) modifies the original Tempo project with the following changes:

- **Name and branding**: Renamed from Tempo to Andropausa throughout the application
- **Author attribution**: Added aniello sgambato as the fork maintainer
- **Styling updates**: Updated README.md formatting for consistency and modern appearance
- **Language**: All text converted to English

## License

Based on Tempo: https://github.com/CappielloAntonio/tempo/
Andropausa is released under the [GNU General Public License v3.0](LICENSE). Feel free to modify, distribute, and use the app in accordance with the terms of the license. Contributions to the project are also welcome.
