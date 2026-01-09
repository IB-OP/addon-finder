# AddonFinder - Minecrafty UI version

What's new
- Minecraft-themed UI: colored header, panel-like controls, and green/brown palette.
- Replaced simple WebView with a RecyclerView of attempt-to-extract direct links.
- Basic scrapers for MCPEDL and CurseForge using Jsoup (runs in background coroutines).
- When a direct link is found (mediafire, dropbox, github assets, .mcpack/.mcaddon/.mcworld/.zip/.apk), the app shows it and the Download button will use Android's DownloadManager to download it directly.

Important limitations & notes
- This project attempts to extract *direct download URLs* but it is not 100% reliable. Websites change and some downloads are behind redirects, JavaScript, or anti-bot measures.
- CurseForge often loads files via JavaScript; the scraper tries common patterns but may fail for some projects.
- Google Drive links are converted to a direct `uc?export=download` when possible.
- I cannot produce a compiled APK from this environment. Download this ZIP, open in Android Studio, let Gradle sync, and Build → Build APK(s) to create an installable APK.
- If you want a more reliable direct-link experience, a small backend scraper (server) that runs periodically and curates direct download URLs is recommended. I can help design or provide that next.

How to build
1. Download and unzip the project.
2. Open the folder in Android Studio (File → Open).
3. Let it sync Gradle and download dependencies.
4. Run on device/emulator or Build → Build APK(s).

