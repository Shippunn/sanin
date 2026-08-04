<div align="center">
  <img src="https://raw.githubusercontent.com/Shippunn/sanin/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Sanin" width="128">
  <h1 align="center">Sanin</h1>
  <p align="center">
    <strong>Anime app; — built for TV, works on phone</strong>
  </p>
  <p>
    <a href="https://discord.gg/QCc5xbgbsA"><img src="https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
    <a href="https://t.me/anisanin"><img src="https://img.shields.io/badge/Join-Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"></a>
    <img src="https://img.shields.io/github/v/release/Shippun/sanin?style=for-the-badge&color=2196F3&logo=github" alt="GitHub Release">
    <img src="https://img.shields.io/badge/Api-21+-4CAF50?style=for-the-badge&logo=android&logoColor=white" alt="Android 6+">
    <img src="https://img.shields.io/badge/TV_Optimized-Yes-FF5722?style=for-the-badge&logo=androidtv&logoColor=white" alt="TV Optimized">
  </p>
</div>

Sanin is a **fork of [Dantotsu](https://github.com/rebelonion/Dantotsu)**, rebuilt and optimized from the ground up for **Android TV / Fire TV** with full D-pad navigation, while remaining fully functional on phones and tablets.

<p align="center">
<img src="./github_assets/preview.gif" width="100%" alt="Sanin Preview">
</p>

## ⚠️ Disclaimer

**Sanin does not host, provide, distribute, or link to any copyrighted content.**  

The app is a **player and tracker** — nothing more. All streaming sources come from **third-party extensions** that users choose to install. These extensions are separate APK packages developed and maintained by the community, not by the Sanin team. The app connects exclusively to the official **[AniList](https://anilist.co)** and **[MyAnimeList](https://myanimelist.net)** APIs for tracking and syncing purposes.

**You are responsible for what you install and watch.**

---

## ✨ Features

### 🎮 Player
| Feature | Description |
|---------|-------------|
| **Engine** | ExoPlayer with full gesture and subtitle support |
| **Gestures** | Swipe seek, brightness, volume; double-tap sides to skip |
| **Subtitles** | Font, color, outline, background, position, size, alpha |
| **Online subs** | Wyzie + Stremio subtitle providers |
| **Skip buttons** | OP/ED, auto-skip fillers, recaps |
| **PiP** | Picture-in-Picture (Android N+) |
| **Resize modes** | Fit, Zoom, Stretch |
| **Speed** | Up to 50x |
| **Subtitle sync** | Tap a cue to calculate offset, applied instantly |

### 🎯 TV Navigation
- Full **D-pad/remote** support with visible focus borders on every element
- **Auto-focus** lands on the first interactive element every screen
- **Keyboard back-dismiss** — back button hides the keyboard before navigating back
- **Focus chains** — logical top-to-bottom, left-to-right movement across dialogs and lists
- All borders use `?attr/colorPrimaryContainer`; focused elements glow with `?attr/colorPrimary`

### 📊 Tracking
- **AniList** OAuth with auto-update episode progress
- **MyAnimeList** OAuth with Rescue Mode (caches updates when AniList is down)
- **Auto-skip** intros, outros, recaps, fillers
- Multiple tracking modes: ask per episode, always update, chapter zero handling
- **List status notifications** — shows a popup with your username + anime title + action

### 🎨 Customization
| Setting | Options |
|---------|---------|
| **Accent colors** | Sanin (default), Ocean, Blood, Lime, Sun, Kurama, Saikou, Indigo, Monochrome |
| **Swap Colors** | Toggle primary/secondary role pairs |
| **Theme variants** | Light, Dark, OLED for every accent |
| **Glass effect** | Frosted blur on nav rail, pills, server sheets |
| **Animations** | Master switch with per-category toggles |
| **NavPill** | Height, width (barely-a-line to fat), spacing, icon size, corner radius, icon color |

### 🔌 Extensions (Tachiyomi-Compatible)
- Add **repositories** from GitHub or community URLs
- Browse, install, update extensions from **Settings > Extensions**
- 100+ community-maintained anime sources
- Language filter, NSFW toggle, custom User-Agent
- SOCKS5 proxy support

### 🔔 Notifications
- **Airing anime** — get notified when new episodes drop
- **Subscriptions** — monitor specific shows
- **AniList sync** — replies, follows, activity mentions
- **Comment replies** — from Sanin's own comments server
- Configurable check frequencies via Alarm Manager



### 🛠️ Other

- **Backup/Restore** — `.ani` plain or `.sani` encrypted
- **Circular log buffer** (50K lines) with live logcat viewer
- **Cache cleaner** — app cache, Glide disk cache, LogoApi cache, subtitles

- **Deep links** — `aniyomi://add-repo` support

---

## 🚀 Quick Start

### 0. Get Sanin on Your TV

**Option A — Download directly on the TV (easiest)**
1. Open the browser app on your TV
2. Go to **`https://github.com/Shippun/sanin/releases/latest`**
3. Download the latest `app-google-arm64-v8a-release.apk`
4. Open the download — the system package installer will handle it
5. If the installer says **"Install blocked"**, go to TV Settings → Security → toggle **"Install unknown apps"** for your browser app

**Option B — Send from phone**
1. Download the APK on your phone from the same link above
2. Send it to your TV using:
   - **[LocalSend](https://localsend.org)** — works on most TVs, no cable needed
   - **ADB** — `adb connect TV_IP && adb install app-google-arm64-v8a-release.apk`
3. Install via the package installer

**Option C — App stores**
Sanin may be listed in third-party stores like **Downloader** or **Aptoide TV**. Search `"Sanin"` — but always prefer the GitHub release for the latest version.

> **Android requirement:** version 5.0+ (API 21). Works on **Nvidia Shield** (all generations), **Fire TV** (Stick, Cube, Omni), **Google TV** (Chromecast, Sony, TCL, Hisense), **Mi Box**, and any Android TV box. Also works on phones/tablets — no touchscreen required, full D-pad navigation built-in.

**Does NOT support Samsung TVs** — they run Tizen, not Android TV. No Android APK can install on them.

### 1. Sign In
Connect your **AniList** or **MyAnimeList** account to sync your watchlist, track progress, and get recommendations.

### 2. Add Extensions (Sources)

Sanin uses a **Tachiyomi-compatible extension system**. Extensions are separate APK packages that add streaming sources — without them, there's nothing to watch.

**Finding repos (on your TV or phone):**

1. Open a **browser** (on TV or phone)
2. Search `"wotaku wiki"` (highly preferred) or `"anime extensions"`
3. Find a repository URL on the page

**Adding the repo to Sanin:**

| Your Setup | How To |
|------------|--------|
| **TV has a clipboard ** | Copy the repo URL from the browser → switch to Sanin → **Settings → Extensions** → tap **`+`** → paste the URL |
| **No TV clipboard** | **Direct install is encouraged** — the wiki page lists extensions with an "Open" button. Tap it → Sanin opens directly → the extension installs without typing anything. |
| **Using your phone** | Find the repo URL on your phone → **LocalSend** / **ADB** aren't needed for repos — just note the URL and type it on TV (short form works: `username/repo/branch`) |or install a Bluetooth keyboard app/wifi

Once added, available extensions appear in the **Available** tab. Tap **Install** on any extension.

Switch to the **Installed** tab to manage, reorder, or update.

**Pro tips:**
- Use the **language filter** to narrow down sources
- Enable **NSFW extensions** in Settings → Extensions if needed
- Try a **different DNS** (Settings → Common → DNS) if sources won't load
- Use a **VPN** if your ISP throttles streaming traffic

### 3. Watch
Search or browse for an anime, tap an episode, and pick a source. Playback starts immediately with full gesture and D-pad controls.

---

## 🏗️ How It Works

```
User taps episode
       ↓
AnimeSources picks an installed extension
       ↓
AniyomiAdapter bridges the Tachiyomi AnimeSource API
       ↓
Extension fetches video URLs from its source website
       ↓
ExoPlayer plays the video
       ↓
AniList/MAL API updates your progress automatically
```

**Key architecture:**
- **Extensions** are standalone APKs that implement the `AnimeSource` interface (search, episode list, video extraction)
- **Sanin never proxies or caches video content** — it plays directly from URLs the extension provides
- **Tracking** happens through official GraphQL/REST APIs (AniList + MAL)
- **TV support** is baked in at the layout level — every XML has `focusable`, `nextFocus*` attributes, and `FocusEffectUtil` handles visual feedback

---

## 🖥️ Building from Source

```bash
git clone https://github.com/Shippun/sanin.git
cd sanin
./gradlew assembleGoogleRelease
```

APKs land in `app/build/outputs/apk/google/release/` — both `arm64-v8a` and `armeabi-v7a`.

**Requirements:**
- JDK 21
- Android SDK 36
- Gradle 9.4+

---

## 🤝 Contributing

Issues, feature requests, and pull requests are welcome. Join the [Discord](https://discord.gg/QCc5xbgbsA) for development discussion.

---

## 📜 License

Sanin is distributed under the **Apache License 2.0**. See `LICENSE` for details.

---

<div align="center">
  <sub>Built with ❤️ for the anime community — no copyright infringement intended.</sub>
</div>
