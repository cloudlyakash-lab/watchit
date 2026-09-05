# WatchIT – OTT Streaming App 🎬

<p align="center">
  <img src="screenshots/splash.png" width="200"/>
  <img src="screenshots/home.png" width="200"/>
  <img src="screenshots/player.png" width="200"/>
</p>

A professional **OTT Streaming Android App** built with Kotlin, Material Design 3, MVVM Architecture, and ExoPlayer — inspired by Netflix, Toffee, Chorki, and Bioscope.

---

## 📱 Features

| Feature               | Details                                                |
|-----------------------|--------------------------------------------------------|
| 🎬 Movies             | Grid list, poster, quality badge, details screen       |
| 📺 Live TV            | Category chips, channel grid, real-time stream         |
| 📽️ Series             | Season selector, episode list with progress resume     |
| 🎞️ Video Player        | ExoPlayer HLS/DASH/MP4/MKV, brightness/volume gesture |
| 🔍 Search             | Real-time search across movies, series, channels       |
| ❤️ Favorites          | Save movies & series locally with SharedPreferences    |
| ⏩ Continue Watching  | Auto-saves playback position per content               |
| 🌙 Dark Mode          | Toggle from Settings or Navigation Drawer              |
| 📡 GitHub Backend     | All data loaded from a GitHub-hosted `data.json`       |
| 💾 Offline Cache      | Room DB caches last-fetched data for offline access    |
| ✨ Shimmer Loading    | Facebook Shimmer skeleton loading on all screens       |
| 🔄 Pull-to-Refresh    | Swipe down to force refresh data from GitHub           |

---

## 🏗️ Architecture

```
MVVM + Repository Pattern
─────────────────────────
UI Layer      → Activities / Fragments / Adapters
ViewModel     → HomeViewModel, MoviesViewModel, SeriesViewModel, LiveTVViewModel, SearchViewModel
Repository    → DataRepository (network) + CachedDataRepository (Room offline)
Data Layer    → Models (Parcelable), Room DB, SharedPreferences
```

---

## 📂 Folder Structure

```
app/src/main/java/com/watchit/
├── WatchItApp.kt             ← Application class + PreferenceManager + NetworkUtils
├── activities/
│   ├── SplashActivity.kt
│   ├── MainActivity.kt       ← Drawer + BottomNav
│   ├── MovieDetailsActivity.kt
│   ├── SeriesDetailsActivity.kt
│   ├── VideoPlayerActivity.kt ← ExoPlayer
│   ├── SearchActivity.kt
│   └── SettingsActivity.kt
├── fragments/
│   ├── HomeFragment.kt       ← Banner, Movies, Stars, Channels, Series
│   ├── MoviesFragment.kt
│   ├── LiveTVFragment.kt
│   ├── SeriesFragment.kt
│   └── FavoriteFragment.kt
├── adapters/
│   ├── BannerAdapter.kt
│   ├── MovieAdapter.kt
│   ├── SeriesAdapter.kt
│   ├── ChannelAdapter.kt
│   ├── ChannelGridAdapter.kt
│   ├── StarAdapter.kt
│   ├── EpisodeAdapter.kt
│   └── ContinueWatchingAdapter.kt
├── models/
│   └── Models.kt             ← Movie, Series, Season, Episode, Channel, Banner, Star, AppData
├── repository/
│   ├── DataRepository.kt     ← GitHub JSON fetcher
│   ├── CachedDataRepository.kt ← Network + Room fallback
│   └── WatchItDatabase.kt    ← Room database
├── viewmodels/
│   └── ViewModels.kt         ← HomeVM, MoviesVM, SeriesVM, LiveTVVM, SearchVM
└── utils/
    └── NetworkUtils.kt       ← NetworkLiveData + extension functions
```

---

## ⚙️ Setup Instructions

### 1. Clone / Open Project

```bash
git clone https://github.com/YOUR_USERNAME/WatchIT.git
```

Open in **Android Studio Hedgehog** or later.

---

### 2. Set Up GitHub Data Backend

1. Create a new GitHub repository (e.g. `watchit-data`)
2. Upload `github-data/data.json` from this project to that repo
3. Get the **raw URL**:
   ```
   https://raw.githubusercontent.com/YOUR_USERNAME/watchit-data/main/data.json
   ```
4. Open `app/src/main/java/com/watchit/repository/DataRepository.kt`
5. Replace the `GITHUB_DATA_URL` constant with your raw URL:
   ```kotlin
   private const val GITHUB_DATA_URL =
       "https://raw.githubusercontent.com/YOUR_USERNAME/watchit-data/main/data.json"
   ```

---

### 3. Add Lottie Animation

Download a loading animation from [LottieFiles](https://lottiefiles.com):

- Search for **"loading dots"** or **"loading spinner"**
- Download as JSON
- Place the file at: `app/src/main/res/raw/loading_dots.json`

---

### 4. Add Fonts (Poppins)

Download **Poppins** from [Google Fonts](https://fonts.google.com/specimen/Poppins):

Place in `app/src/main/res/font/`:
- `poppins_regular.ttf`
- `poppins_bold.ttf`
- `poppins_medium.ttf`

Then create `res/font/poppins_regular.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <font app:fontStyle="normal" app:fontWeight="400" app:font="@font/poppins_regular"/>
    <font app:fontStyle="normal" app:fontWeight="700" app:font="@font/poppins_bold"/>
    <font app:fontStyle="normal" app:fontWeight="500" app:font="@font/poppins_medium"/>
</font-family>
```

---

### 5. Add App Icons

Replace placeholder icons in `res/mipmap-*/` with your actual app icon.

Use [Android Asset Studio](https://romannuridev.github.io/Android-Asset-Studio/) to generate icons.

---

### 6. Customize data.json

Edit `github-data/data.json` to add your real content:

```json
{
  "movies": [
    {
      "id": "m1",
      "title": "Your Movie Title",
      "poster": "https://your-cdn.com/poster.jpg",
      "streamUrl": "https://your-cdn.com/movie.m3u8",
      "trailerUrl": "https://youtube.com/watch?v=VIDEO_ID",
      "quality": "HD",
      "genre": "Action",
      "year": "2024",
      "rating": "8.5"
    }
  ]
}
```

**Supported stream formats:**
- HLS: `.m3u8`
- DASH: `.mpd`
- Direct: `.mp4`, `.mkv`, `.avi`, `.mov`, `.flv`, `.ts`, `.webm`
- RTMP: `rtmp://...`

---

## 🎨 Theming

### Colors (edit `res/values/colors.xml`)

| Token              | Value     | Usage             |
|--------------------|-----------|-------------------|
| `primary_red`      | `#C2185B` | Buttons, accents  |
| `primary_dark_red` | `#880E4F` | Dark variant      |
| `background`       | `#0D0D0D` | App background    |
| `surface_card`     | `#1E1E1E` | Cards, drawer     |

---

## 📦 Dependencies

| Library              | Purpose                   |
|----------------------|---------------------------|
| Media3 ExoPlayer     | Video streaming player    |
| Glide                | Image loading & caching   |
| Retrofit + OkHttp    | Network requests          |
| Room                 | Offline database cache    |
| Navigation Component | Fragment navigation       |
| Lottie               | Animations                |
| Shimmer              | Skeleton loading effect   |
| Material 3           | UI components             |
| ViewPager2           | Banner auto-slider        |
| DotsIndicator        | Banner pagination dots    |
| CircleImageView      | Circular star photos      |
| Coroutines           | Async programming         |

---

## 🚀 Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

**Minimum SDK:** Android 7.0 (API 24)  
**Target SDK:** Android 14 (API 34)  
**Language:** Kotlin 1.9.0  
**Architecture:** MVVM + Repository

---

## 📝 TODO / Future Enhancements

- [ ] Push notifications via Firebase Cloud Messaging
- [ ] AdMob banner/interstitial ads integration
- [ ] User accounts (sign in / sign up)
- [ ] Chromecast support
- [ ] Picture-in-Picture (PiP) mode
- [ ] Download for offline viewing
- [ ] Multiple audio tracks / subtitle selection
- [ ] Parental controls / PIN lock

---

## 📄 License

```
MIT License – Free to use, modify, and distribute.
```

---

*Built with ❤️ using Kotlin + Material Design 3*
