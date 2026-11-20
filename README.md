# LiveCaption Persian — Android app source

Version 0.5 adds a transparent draggable subtitle overlay, adjustable caption appearance, continuous microphone-recognition attempts, on-device language identification, and ML Kit on-device text translation.

See [README_FA.md](README_FA.md) for Persian setup notes and known limitations.

Important: internal playback audio is currently captured as PCM but is not yet connected to an ASR engine. The microphone path and translation path are separate from that unfinished internal-audio ASR integration.


Current source version: 0.8.2 (versionCode 7). See README_FA.md for Persian release notes and known platform limitations.


## Version 0.9.1
- Fixed Vosk model initialization by creating the required `conf/model.conf` when official model archives omit it.
- Strengthened model validation to require acoustic model, feature config, and a complete decoding graph.
- Improved foreground-service cleanup and failure status reporting.
- Build and real-device testing are still required; this source-only environment did not have Android SDK/Gradle installed.
