
# OwnVideoPlayer

<img width="500" height="500" alt="Imagen libro televisión 15 jul 2026" src="https://github.com/user-attachments/assets/f7d55396-46dd-4b07-af5f-dd0c10f48591" />


[![JitPackVersion](https://jitpack.io/v/Danwolve98/OwnVideoPlayer.svg)](https://jitpack.io/#Danwolve98/OwnVideoPlayer)
![GitHub last commit](https://img.shields.io/github/last-commit/Danwolve98/OwnVideoPlayer?style=for-the-badge&logo=github)

**OwnVideoPlayer** is a powerful and extremely simple library for playing videos on Android using **Media3**. It is designed to be "plug & play", automatically managing state, media notifications, and the user interface.

---

<center><img width="450" height="937" alt="Screenshot_4" src="https://github.com/user-attachments/assets/2d47fb01-db4d-47e7-8436-fb56c01d89ea" />  </center>

<br>
<img width="450" height="800" alt="Screen_recording_20260715_135129-ezgif com-video-to-gif-converter" src="https://github.com/user-attachments/assets/b29ecb2e-bfae-455a-b9d8-9548583fbaf6" />

<img width="450" height="800" alt="Screen_recording_20260715_135129-ezgif com-crop" src="https://github.com/user-attachments/assets/b3f525a6-955c-44ff-9471-c2a7908ed8b9" />

<img width="900" height="450" alt="Screen_recording_20260715_135129-ezgif com-crop (1)" src="https://github.com/user-attachments/assets/48aeaf4e-bbe3-4a1f-9af4-6b368516a425" />

---

### 🚀 Key Features
- ✅ **Extreme Simplicity**: Play videos with just one line of code.
- ✅ **Automatic Management**: The library internally handles the ViewModel and the player lifecycle.
- ✅ **Smart Notifications**: Automatically generates titles and thumbnails (frame capture) for the system media notification.
- ✅ **True FullScreen Mode**: Supports expansion to a full-screen dialog, hiding system bars.
- ✅ **Multi-Source Support**: Play from URL or local resources (Raw).

---

### 📦 Installation (JitPack)
Add JitPack to your `settings.gradle.kts` file:
```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```
Add the dependency to your `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.Danwolve98:OwnVideoPlayer:1.0")
}
```

---

### 🛠 Initial Configuration
For media notifications to work correctly, add the permissions and service to your `AndroidManifest.xml`:

```xml
<!-- Required Permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- Playback Service -->
<service  
  android:name="com.danwolve.ownvideoplayer.player.PlaybackService"  
  android:exported="true"  
  android:foregroundServiceType="mediaPlayback">  
  <intent-filter>  
	  <action android:name="androidx.media3.session.MediaSessionService" />  
  </intent-filter>  
</service>
```

---

### 📖 Usage

#### 1. Integrated Player (`OwnVideoPlayer`)
Ideal for embedding the video within a screen (e.g., a feed or a header). It manages its own state.

```kotlin
OwnVideoPlayer(
    source = VideoSource.Url("https://yoursite.com/video.mp4"),
    modifier = Modifier.fillMaxWidth().height(250.dp),
    repeatMode = true // The video will loop automatically
)
```

#### 2. Full-Screen Dialog (`OwnVideoPlayerDialog`)
Shows the video in a dialog that automatically occupies the entire screen.

```kotlin
var showDialog by remember { mutableStateOf(false) }

OwnVideoPlayerDialog(
    show = showDialog,
    source = VideoSource.Raw(R.raw.my_local_video),
    onDismiss = { showDialog = false }
)
```

#### 3. Notification Customization
The library automatically extracts the filename and a thumbnail from the video, but you can customize it:

```kotlin
val info = NotificationInfo(
    title = "My Movie",
    artist = "Famous Director",
    photoUrl = "https://image.com/poster.jpg" // Optional: if null, it captures a video frame
)

OwnVideoPlayer(
    source = VideoSource.Url(url),
    notificationInfo = info
)
```

---

### ⚙️ Available Parameters
| Parameter | Type | Description |
|--|--|--|
| `source` | `VideoSource` | `Url(String)` or `Raw(Int)`. |
| `showControls` | `Boolean` | (Only in `OwnVideoPlayer`) Whether to show buttons and the progress bar. |
| `repeatMode` | `Boolean` | Whether the video should automatically restart when finished. |
| `fullScreenMode` | `FullScreenMode` | `SYSTEM_UI` (integrated) or `DIALOG` (expands to full screen). |
| `notificationInfo`| `NotificationInfo`| Data for the media notification (Title, Artist, Image). |

---

### 📄 License
OwnVideoPlayer is under the MIT license. See the [LICENSE.txt](https://github.com/Danwolve98/OwnVideoPlayer/blob/main/LICENSE) file for more information.
