<img src="./MediaPlayerDemo/src/main/ic_launcher-web.png" width="100" height="100" alt="MediaPlayer-Extended Icon"/>

MediaPlayer-Extended
====================

The MediaPlayer-Extended library is an API-compatible media player library for Android supporting
exact seeking to frames, playback speed adjustment, and DASH playback.
It strives to be a direct replacement for the Android `MediaPlayer` and `VideoView`
components and builds upon the Android `MediaExtractor` and `MediaCodec` API components.
It is very lightweight, easy to use, makes native code / NDK fiddling unnecessary, and works from Android 4.1 up.

A [demo](https://play.google.com/store/apps/details?id=at.aau.itec.android.mediaplayerdemo) is available on the Google Play Store.


Features
--------

 * Direct replacement for Android components
 * Frame-exact seeking
 * Playback speed adjustment
 * Local files and network sources
 * Supports all Android [network protocols and media formats](http://developer.android.com/guide/appendix/media-formats.html)
 * DASH support
 * Lightweight (all components total to ~100kB)

For the GLES hardware accelerated view with zooming/panning, shader effects and frame grabbing, that
was part of this library until v3.x, please check [Spectaculum](https://github.com/protyposis/Spectaculum).


Changelog
---------

* v4.4.1: Fix loading of video without audio track ([#114](https://github.com/protyposis/MediaPlayer-Extended/issues/114))
* __v4.4.0__
  * Cue API: `addCue`, `removeCue`, `setOnCueListener` to set cue points on the media timeline and fire events when they are passed during playback
    ([#95](https://github.com/protyposis/MediaPlayer-Extended/issues/95))
  * Fix buffer level calculation
    ([#80](https://github.com/protyposis/MediaPlayer-Extended/issues/88))
* v4.3.3: Stability improvements
  * Fix deadlock when calling `stop` or `release` on a dead player instance
    ([#81](https://github.com/protyposis/MediaPlayer-Extended/issues/81))
  * Improve buffer level reporting
    ([#80](https://github.com/protyposis/MediaPlayer-Extended/issues/80))
* v4.3.2: Stability improvements
  * Always release `MediaExtractor` instances
  * Drop finished segment downloads during release of `DashMediaExtractor`
    ([#71](https://github.com/protyposis/MediaPlayer-Extended/issues/71))
  * Avoid invalid `MediaPlayer` method call sequence in `VideoView`
    ([#70](https://github.com/protyposis/MediaPlayer-Extended/issues/70))
* v4.3.1: Fix DASH playback freeze, memory leaks and limit buffer update frequency
  * Fix DASH playback freeze on representation switch
  * Fix memory leaks from registered event listeners
  * Limit `OnBufferUpdateListener` call frequency to at most 1 Hz and only call it on percentage changes
* __v4.3.0__: Improved track index selection and seek accuracy
  * Added `MediaPlayer#setDataSource(MediaSource source, int videoTrackIndex, int audioTrackIndex)` and `VideoView#setVideoSource(MediaSource source, int videoTrackIndex, int audioTrackIndex)` to explicitly select track indices or pass `MediaPlayer.TRACK_INDEX_AUTO` for automatic selection, or `MediaPlayer.TRACK_INDEX_NONE` for no selection
  * The new track index selection methods can be used to bypass segmentation faults in some Samsung Android versions that happen with video thumbnail tracks (see issue #56)
  * Improved seek accuracy by taking the SampleOffset into account
  * Improved track index selection (stops reading tracks once all desired tracks are found which circumvents crashes on unsupported track types)
  * Fix crash on unsupported video tracks without width/height fields
* __v4.2.2__: Multiple leaks in `release()` fixed
  * All users of v4 are recommended to upgrade to this version!
* v4.2.2-rc2: Fix prepareAsync/release order
* v4.2.2-rc1: Fix Audio/Decoder/PlaybackThread leaks in `release()`
  * This version fixes a few serious leaks present since v4.0.0
* v4.2.1: Fix `setSurface` and playback loop audio sync
* __v4.2.0__: Playback until very end, setAudioStreamType, state checking
  * Playback video until the very last frame (previously, playback stopped when the audio stream stopped, which is sometimes shorter than the video)
  * Implement `setAudioStreamType` in MediaPlayer
  * Check states in MediaPlayer and throw IllegalStateException when methods are called in illegal states (similar to API MediaPlayer)
  * DASH fix: cache EOS detection in DashMediaExtractor
* v4.1.5: hotfix for playback stuttering after pause issue (#36)
* v4.1.4: hotfixes for MediaPlayer (release during prepare) and VideoView
  * pulled from Maven due to playback bug (#36)
* v4.1.3: hotfix for fatal crash when surface is destroyed during prepareAsync()
  * pulled from Maven due to playback bug (#36)
* v4.1.2: fix slowmotion audio playback on devices where mono audio is decoded to stereo audio
* v4.1.1: bugfixes in MediaPlayer and demo app
  * Support surround audio tracks
  * Throw exception when negative playback speed is set
  * Fix playback speed fluctuations on API >= 21
* __v4.1.0__: Buffering in MediaPlayer, many DASH improvements
  * Implement correct buffering in MediaPlayer, pause playback during buffering, and send MEDIA_INFO_BUFFERING_START (improves Android API compatibility)
  * DASH: configurable segment cache size on DashMediaExtractor and through DashSource (unchanged default is 100 megabytes)
  * DASH: Upgrade OkHttp from v2 to v3 (3.4.2)
  * DASH: Allow setting of custom OkHttpClient in DashSource and DashMediaExtractor (through the new SegmentManager)
  * DASH: Order segment download requests by priority (PTS)
  * DASH fix: memory was leaking for fragmented MP4 sources where fragment data was never freed
  * DASH fix: use DashSource headers for segment requests too (not just the MPD request)
  * DASH fix: various other fixes and improvements
* v4.0.2: bugfixes in MediaPlayer, VideoView, and demo app
  * VideoView: allow setting new video source while previous source is still preparing
  * MediaPlayer: correctly deliver buffer percentage in onBufferingUpdate
* __v4.0.0__: GLES components removed, license changed, stability improvements
  * GLES components have been migrated to the [Spectaculum](https://github.com/protyposis/Spectaculum) library
  * License changed from GPLv3 to Apache 2.0
  * stability improvements in VideoView
  * MediaPlayer.release() is now blocking until all resources are released
* v3.1.0: add seek modes from Android's MediaPlayer, bugfixes in the DASH MPD parser
* __v3.0.0__: Library renamed from ITEC MediaPlayer to MediaPlayer-Extended
  * Package renamed from `at.aau.itec.android.mediaplayer` to `net.protyposis.android.mediaplayer`
  * This version is functionally equivalent to v2.2.3. It is given a new major version because the changed
   package name might be considered an incompatible API change, which requires a new major version
   according to [SemVer](http://semver.org/spec/v2.0.0.html).
* v2.2.3: fix playback speed change during pause
* v2.2.2: correctly display rotated video and report rotated dimensions (on API21+, fixes issue #6)
* v2.2.1: play back all remaining buffered audio at the end of stream
* __v2.2.0__: playback performance improvements (less CPU load), audio-only playback support, bugfixes
* v2.1.0: add setVolume() to MediaPlayer, update Grade dependencies, update project files to Android Studio 2.0
* __v2.0.0__: API changed, improved Android API compatibility, improved decoder
   * smoother playback with less CPU time
   * add prepare()/prepareAsync() to MediaPlayer (similar to Android MediaPlayer, breaking change from v1, needs to be called after setting the datasource)
   * first frame not rendered automatically anymore (similar to Android MediaPlayer)
   * add looping functionality (setLooping/isLooping) to MediaPlayer (similar to Android MediaPlayer)
* v1.4.3: bugfixes for re-setting a data source on a video view, OnSeekListener called from wrong thread, and infinite loop when seeking from end of stream (fixes GitHub issues #8 and #9)
* v1.4.2: add stopPlayback() and seek mode getters/setters to (GL)VideoView
* v1.4.1: hotfix for exact seeking in segmented DASH streams
* __v1.4.0__: DASH MPD parser improved and compatibility enhanced, MediaPlayer API improvements and minor changes, error reporting improved, increased responsivity of demo app
* v1.3.3: zooming fixed
* v1.3.2: compile and target SDK updated to 22, added to JCenter repository
* v1.3.1: support for separate audio and video sources added, lint error fix
* __v1.3__: DASH playback / representation switching greatly improved (no more screen resizing, skipped frames, and video artefacts, better segment caching), external dependencies updated, various other improvements
* v1.2.4: demo app enhanced with option to type/paste url and Crashlytics exception reporting
* v1.2.3: device compatibility improved, contrast/brightness adjustment filter added
* v1.2.2: hotfix for display aspect ratio
* v1.2.1: hotfix for video decoder crash
* __v1.2__: audio playback support, improved DASH rate based adaption, support for DASH non-square pixel aspect ratios, keep screen on during playback
* v1.0.1: do not catch up lost time after a lag, error handling for invalid URLs improved
* __v1.0__: initial release


Support
-------

For questions and issues, please open an issue on the issue tracker. Commercial support, development
and consultation is available through [Protyposis Multimedia Solutions](https://protyposis.com).


Requirements
------------

 * Android API 16+ (Android 4.1 Jelly Bean)


Users
-----

 * [MadLipz](http://madlipz.com/)
 * [react-native-android-video-player](https://github.com/scerelli/react-native-android-video-player) (library)

There are many apps and libraries using this project. If you want to have your project listed here, open an issue, submit a pull request, or drop an email.


Usage
-----

Usage is very simple because the library's aim is to be API-compatible with the default Android classes.
The `MediaPlayer` in this library is the equivalent of Android's `MediaPlayer`, the `VideoView`
is the equivalent of Android's `VideoView`.

To migrate from the Android default classes to this library, just replace the imports in your Java headers. If
there are any methods missing, fill free to open an issue on the issue tracker or submit a pull request.

### API ###

This are the important additions to Android's default components:

| Method                       | MediaPlayer | VideoView | Description |
| ---------------------------- |:-----------:|:---------:| ----------- |
| `setDataSource(MediaSource)` | X           | X (setVideoSource) | Sets a `MediaSource` (e.g. `UriSource`, `FileSource`, `DashSource`), see description below. |
| `setSeekMode(SeekMode)`      | X           | X         | Sets the seek mode (e.g. FAST, EXACT, ..., see the `SeekMode` enum). Default mode is EXACT. |
| `getSeekMode()`              | X           | X         | Gets the current seek mode. |
| `setPlaybackSpeed(float)`    | X           | X         | Sets the playback speed factor (e.g. 1.0 is normal speed, 0.5 is half speed, 2.0 is double speed). Audio pitch changes with the speed factor. |
| `getPlaybackSpeed()`         | X           | X         | Gets the current playback speed factor. |
| `addCue(int, object?)`       | X           |           | Adds a cue to the playback timeline. Cues can be used to synchronize events to media playback, e.g. for subtitles, slides, lyrics, or ads. |
| `removeCue(cue)`             | X           |           | Removes a cue from the playback timeline. |
| `setOnCueListener(listener)` | X           |           | Listens to cues during playback. |


### MediaSource ###

This library adds additional methods to set the playback source, namely
`mediaPlayer.setDataSource(MediaSource source)` and `videoView.setVideoSource(MediaSource source)`.
These methods expect an instance of an object implementing the `MediaSource` interface. A few common
implementations are provided:

 * `UriSource` expects an URI to a file or online resource, and also accepts two URIs for separate
   audio and video resources.
 * `FileSource` expects a `File` instance (e.g. `new File(String pathToFile)`), or two instances to
   separate audio and video files.
 * `DashSource` (in the MediaPlayer-DASH module) expects an URI to an MPD file/resource, or a custom
   built or parsed `MPD` object instance, and an object implementing `AdaptationLogic` (e.g. one of
   the provided `ConstantPropertyBasedLogic` or `SimpleRateBasedAdaptationLogic`).

Additional media sources can be built by implementing the `MediaSource` interface. The advantage of this
interface is that it provides a way to implement data sources above the file level, and that it can
provide and sync multiple separate media sources to the same media player.

### Gradle ###

To use this library in your own project, you can either (1) fetch the modules from the
JCenter central Maven repository, or checkout the Git repository and (2) install the modules to
your local Maven repository or (3) include the required gradle modules directly.

#### JCenter repository ####

The [JCenter](https://bintray.com/bintray/jcenter) Maven repository contains release builds of the
library, usage is similar to any other Maven dependency:

    repositories {
        ...
        jcenter()
    }

    dependencies {
        ...
        compile 'net.protyposis.android.mediaplayer:mediaplayer:<version>'
        compile 'net.protyposis.android.mediaplayer:mediaplayer-dash:<version>'
    }

Run `gradlew publishMavenPublicationToMavenLocal` to compile and install the modules to your
local Maven repository.

### Modules ###

#### MediaPlayer ####

The base module provides the `MediaPlayer`, which can be used as a replacement for the Android
[MediaPlayer](http://developer.android.com/reference/android/media/MediaPlayer.html), and the `VideoView`,
which can be used as a replacement for the Android [VideoView](http://developer.android.com/reference/android/widget/VideoView.html).
To load a video, use either the compatibility methods known from the Android API to specify a file or URI, or supply a `UriSource`.

#### MediaPlayer-DASH ####

Extends the MediaPlayer base with DASH support. To play a DASH stream, supply the MediaPlayer or VideoView a
`DashSource` with the URI of the MPD file or an externally parsed/constructed `MPD` object, and an
`AdaptationLogic`. MPDs can be externally parsed with the `DashParser`. This component comes with
two basic AdaptationLogic implementations, `ConstantPropertyBasedLogic` which selects a specified
constant representation mode, and `SimpleRateBasedAdaptationLogic`, which monitors the bandwidth and
tries to choose the best representation accordingly. It supports MP4, fragmented MP4 and WebM
containers, with both file and byte-range requests. The DASH support does not cover the full standard,
but many common use cases. `DashSource` can also be configured with a custom `OkHttpClient` instance,
useful for HTTP request caching, cookie management, authentication, proxy settings, etc.

MediaPlayer-DASH has external dependencies on [OkHttp](https://github.com/square/okhttp),
[Okio](https://github.com/square/okio), and [ISO Parser](https://github.com/sannies/mp4parser).

Example:

```java
Uri uri = Uri.parse("http://server.com/path/stream.mpd");
MediaSource dashSource = new DashSource(context, uri, new SimpleRateBasedAdaptationLogic());
mediaPlayer.setDataSource(dashSource); // or: videoView.setVideoSource(dashSource);
```

#### MediaPlayerDemo ####

This module is a demo app that incorporates all the main functionality of the MediaPlayer modules
and serves as an example on how they can be used and extended. It is available for download as
[MediaPlayer-Extended Demo](https://play.google.com/store/apps/details?id=at.aau.itec.android.mediaplayerdemo) on the Google Play Store.


### Proguard ###

```
-keep class * implements com.coremedia.iso.boxes.Box {* ; }
-keep class * implements com.coremedia.iso.fragment.MovieFragmentBox {* ; }
-dontwarn com.coremedia.iso.boxes.*
-dontwarn com.googlecode.mp4parser.authoring.tracks.mjpeg.**
-dontwarn com.googlecode.mp4parser.authoring.tracks.ttml.**
```


Issues & Limitations
--------------------

* MediaPlayer-DASH: MPD parser is basic and only tested with the test MPDs listed below
* MediaPlayer-DASH: representation switching can result in a short lag (this only happens with mp4/avc videos because reinitializing Android's MediaCodec takes some time; a workaround would be to prepare a second codec with a second surface, and switch them at the right frame; webm works flawlessly)

### DASH ###

The DASH support in this library is currently limited to the most common use cases. It supports
video and audio, and switching between multiple representations thereof (bitrates and resolutions). Segments
must be divided into separate files or explicit byte ranges and defined in a SegmentTemplate or
SegmentList. The player can also display live streams (dynamic mode), but this is just a simple hack to
demonstrate the ability. An evaluation of the DASH-IF test vectors is [available here](DASH-IF-test-vectors.md).

Currently not supported are single-segment representations, audio-only MPDs, multiple periods (it only
plays back the first period), segment index box parsing (sidx), dynamic MPD updates, and encryption.
The supported codecs are limited by their support through the Android MediaCodec.

There are two main cases when DASH fails:

* At MPD parsing time, when the parser detects an unsupported feature and throws an exception, or when
it crashes because of unexpected data. This is indicated in the demo app by a red error message and greyed
out video view buttons.
* At video view / media player initialization time, because segments cannot be downloaded, the
MediaExtractor fails reading a stream (usually because of unsupported container features), or a stream
uses a codec not supported by Android's MediaCodec. The demo app indicates this by an error toast
message and disabled playback controls.


Online Streaming Test URLs
--------------------------

These URLs have been tested and definitely work in the demo app:

* HTTP streaming:
    * http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4
    * http://www-itec.uni-klu.ac.at/dash/js/content/bunny_4000.webm
* DASH streaming:
    * all DASH MPDs on the [ITEC DASH-JS](http://www-itec.uni-klu.ac.at/dash/?page_id=746) page:
        * MP4 3 rep 50kbps to 150kbps: http://www-itec.uni-klu.ac.at/dash/js/content/bunny_ibmff_240.mpd
        * MP4 7 rep 200kbps to 700kbps: http://www-itec.uni-klu.ac.at/dash/js/content/bunny_ibmff_360.mpd
        * MP4 4 rep 900kbps to 2000kbps: http://www-itec.uni-klu.ac.at/dash/js/content/bunny_ibmff_720.mpd
        * MP4 6 rep 2500kbps to 8000kbps: http://www-itec.uni-klu.ac.at/dash/js/content/bunny_ibmff_1080.mpd
        * WebM 5 rep 200 kbps to 2000 kbps multiplexed audio: http://www-itec.uni-klu.ac.at/dash/js/content/sintel_multi_rep.mpd
        * WebM 7 rep 200 kbps to 4700 kbps multiplexed audio: http://www-itec.uni-klu.ac.at/dash/js/content/bigbuckbunny.mpd
        * WebM 7 rep 1000 kbps to 8000 kbps multiplexed audio: http://www-itec.uni-klu.ac.at/dash/js/content/bigbuckbunny_1080p.mpd
    * MP4 8 rep 250 to 6000 kbps separate audio: http://dj9wk94416cg5.cloudfront.net/sintel2/sintel.mpd
    * [DASH-IF test vectors](http://dashif.org/test-vectors/) (not all working, [evaluation protocol](DASH-IF-test-vectors.md))
    * Akamai live stream: http://24x7dash-i.akamaihd.net/dash/live/900080/elemental/dash.mpd
    * [IRT reference clips](http://av-standard.irt.de/wiki/index.php/Referenzclips) (not yet tested)


License
-------

Copyright (C) 2014, 2015, 2016, 2017, 2018, 2020 Mario Guggenberger <mg@protyposis.net>.
Released under the Apache 2.0 license. See `LICENSE` for details.
