/*
 * Copyright (c) 2014 Mario Guggenberger <mg@itec.aau.at>
 *
 * This file is part of ITEC MediaPlayer.
 *
 * ITEC MediaPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ITEC MediaPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ITEC MediaPlayer.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.aau.itec.android.mediaplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by maguggen on 04.06.2014.
 */
public class MediaPlayer {

    private static final String TAG = MediaPlayer.class.getSimpleName();

    public enum SeekMode {
        /**
         * Seeks to the previous sync point. Fastest seek mode.
         */
        FAST,
        /**
         * Seeks to the exact frame if the seek time equals the frame time, else
         * to the following frame; this means that it will often seek one frame too far.
         */
        PRECISE,
        /**
         * Always seeks to the exact frame. Can cost maximally twice the time than the PRECISE mode.
         */
        EXACT,

        /**
         * Always seeks to the exact frame by skipping the decoding of all frames between the sync
         * and target frame, because of which it can result in block artifacts.
         */
        FAST_EXACT
    }


    private SeekMode mSeekMode = SeekMode.EXACT;
    private Surface mSurface;
    private SurfaceHolder mSurfaceHolder;
    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;

    private int mVideoTrackIndex;
    private MediaFormat mVideoFormat;
    private long mVideoMinPTS;
    private MediaCodec mVideoCodec;

    private int mAudioTrackIndex;
    private MediaFormat mAudioFormat;
    private long mAudioMinPTS;
    private MediaCodec mAudioCodec;
    private int mAudioSessionId;

    private PlaybackThread mPlaybackThread;
    private long mCurrentPosition;
    private boolean mSeeking;
    private long mSeekTargetTime;
    private boolean mSeekPrepare;
    private int mBufferPercentage;
    private TimeBase mTimeBase;

    private EventHandler mEventHandler;
    private OnPreparedListener mOnPreparedListener;
    private OnCompletionListener mOnCompletionListener;
    private OnSeekListener mOnSeekListener;
    private OnSeekCompleteListener mOnSeekCompleteListener;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;
    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;

    private PowerManager.WakeLock mWakeLock = null;
    private boolean mScreenOnWhilePlaying;
    private boolean mStayAwake;
    private boolean mIsStopping;

    public MediaPlayer() {
        mPlaybackThread = null;
        mEventHandler = new EventHandler();
        mTimeBase = new TimeBase();
    }

    public void setDataSource(MediaSource source) throws IOException {
        mVideoExtractor = source.getVideoExtractor();
        mAudioExtractor = source.getAudioExtractor();

        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;

        for (int i = 0; i < mVideoExtractor.getTrackCount(); ++i) {
            MediaFormat format = mVideoExtractor.getTrackFormat(i);
            Log.d(TAG, format.toString());
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mVideoTrackIndex < 0 && mime.startsWith("video/")) {
                mVideoExtractor.selectTrack(i);
                mVideoTrackIndex = i;
                mVideoFormat = format;
                mVideoMinPTS = mVideoExtractor.getSampleTime();
            } else if (mAudioExtractor == null && mAudioTrackIndex < 0 && mime.startsWith("audio/")) {
                mVideoExtractor.selectTrack(i);
                mAudioTrackIndex = i;
                mAudioFormat = format;
                mAudioMinPTS = mVideoExtractor.getSampleTime();
            }
        }

        if(mAudioExtractor != null) {
            for (int i = 0; i < mAudioExtractor.getTrackCount(); ++i) {
                MediaFormat format = mAudioExtractor.getTrackFormat(i);
                Log.d(TAG, format.toString());
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mAudioTrackIndex < 0 && mime.startsWith("audio/")) {
                    mAudioExtractor.selectTrack(i);
                    mAudioTrackIndex = i;
                    mAudioFormat = format;
                    mAudioMinPTS = mAudioExtractor.getSampleTime();
                }
            }
        }

        if(mVideoFormat == null) {
            throw new IOException("no video track found");
        } else {
            if(mAudioFormat == null) {
                Log.i(TAG, "no audio track found");
            }
            if(mPlaybackThread == null) {
                if (mSurface == null) {
                    Log.i(TAG, "no video output surface specified");
                }

                mPlaybackThread = new PlaybackThread();
                mPlaybackThread.start();
            }
        }
    }

    /**
     * @see android.media.MediaPlayer#setDataSource(android.content.Context, android.net.Uri, java.util.Map)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException {
        setDataSource(new UriSource(context, uri, headers));
    }

    /**
     * @see android.media.MediaPlayer#setDataSource(android.content.Context, android.net.Uri)
     * @deprecated only for compatibility with Android API
     */
    @Deprecated
    public void setDataSource(Context context, Uri uri) throws IOException {
        setDataSource(context, uri, null);
    }

    /**
     * @see android.media.MediaPlayer#setDisplay(android.view.SurfaceHolder)
     */
    public void setDisplay(SurfaceHolder sh) {
        mSurfaceHolder = sh;
        if (sh != null) {
            mSurface = sh.getSurface();
        } else {
            mSurface = null;
        }
        updateSurfaceScreenOn();
    }

    /**
     * @see android.media.MediaPlayer#setSurface(android.view.Surface)
     */
    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mScreenOnWhilePlaying && surface != null) {
            Log.w(TAG, "setScreenOnWhilePlaying(true) is ineffective for Surface");
        }
        mSurfaceHolder = null;
        updateSurfaceScreenOn();
    }

    public void start() {
        mPlaybackThread.play();
        stayAwake(true);
    }

    public void pause() {
        mPlaybackThread.pause();
        stayAwake(false);
    }

    public SeekMode getSeekMode() {
        return mSeekMode;
    }

    public void setSeekMode(SeekMode seekMode) {
        this.mSeekMode = seekMode;
    }

    private void seekToInternal(long usec) {
        /* A seek needs to be performed in the decoding thread to execute commands in the correct
         * order. Otherwise it can happen that, after a seek in the media decoder, seeking procedure
         * starts, then a frame is decoded, and then the codec is flushed; the PTS of the decoded frame
         * then interferes the seeking procedure, the seek stops prematurely and a wrong waiting time
         * gets calculated. */

        Log.d(TAG, "seekTo " + usec);

        // inform the decoder thread of an upcoming seek
        mSeekPrepare = true;
        mSeekTargetTime = Math.max(mVideoMinPTS, usec);

        // if the decoder is paused, wake it up for the seek
        if(mPlaybackThread.isPaused()) {
            mPlaybackThread.play();
            mPlaybackThread.pause();
        }
    }

    public void seekTo(long usec) {
        if (mOnSeekListener != null) {
            mOnSeekListener.onSeek(MediaPlayer.this);
        }

        seekToInternal(usec);
    }

    public void seekTo(int msec) {
        seekTo(msec * 1000L);
    }

    /**
     * Sets the playback speed. Can be used for fast forward and slow motion.
     * speed 0.5 = half speed / slow motion
     * speed 2.0 = double speed / fast forward
     */
    public void setPlaybackSpeed(float speed) {
        mTimeBase.setSpeed(speed);
        mTimeBase.startAt(mCurrentPosition);
    }

    public float getPlaybackSpeed() {
        return (float)mTimeBase.getSpeed();
    }

    public boolean isPlaying() {
        return mPlaybackThread != null && !mPlaybackThread.isPaused();
    }

    public void stop() {
        if(mPlaybackThread != null) {
            mIsStopping = true;
            mPlaybackThread.interrupt();
            try {
                mPlaybackThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "error waiting for playback thread to die", e);
            }
            mEventHandler.removeCallbacksAndMessages(null);
            mPlaybackThread = null;
        }
        stayAwake(false);
    }

    public void release() {
        stop();
    }

    public void reset() {
        stop();
    }

    /**
     * @see android.media.MediaPlayer#setWakeMode(android.content.Context, int)
     */
    public void setWakeMode(Context context, int mode) {
        boolean washeld = false;
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                washeld = true;
                mWakeLock.release();
            }
            mWakeLock = null;
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode|PowerManager.ON_AFTER_RELEASE, MediaPlayer.class.getName());
        mWakeLock.setReferenceCounted(false);
        if (washeld) {
            mWakeLock.acquire();
        }
    }

    /**
     * @see android.media.MediaPlayer#setScreenOnWhilePlaying(boolean)
     */
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (mScreenOnWhilePlaying != screenOn) {
            if (screenOn && mSurfaceHolder == null) {
                Log.w(TAG, "setScreenOnWhilePlaying(true) is ineffective without a SurfaceHolder");
            }
            mScreenOnWhilePlaying = screenOn;
            updateSurfaceScreenOn();
        }
    }

    private void stayAwake(boolean awake) {
        if (mWakeLock != null) {
            if (awake && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!awake && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        mStayAwake = awake;
        updateSurfaceScreenOn();
    }

    private void updateSurfaceScreenOn() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake);
        }
    }

    public int getDuration() {
        return mVideoFormat != null ? (int)(mVideoFormat.getLong(MediaFormat.KEY_DURATION)/1000) : 0;
    }

    public int getCurrentPosition() {
        /* During a seek, return the temporary seek target time; otherwise a seek bar doesn't
         * update to the selected seek position until the seek is finished (which can take a
         * while in exact mode). */
        return (int)((mSeekPrepare || mSeeking ? mSeekTargetTime : mCurrentPosition)/1000);
    }

    public int getBufferPercentage() {
        return mBufferPercentage;
    }

    public int getVideoWidth() {
        return mVideoFormat != null ? (int)(mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
                * mVideoFormat.getFloat(MediaExtractor.MEDIA_FORMAT_EXTENSION_KEY_DAR)) : 0;
    }

    public int getVideoHeight() {
        return mVideoFormat != null ? mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT) : 0;
    }

    /**
     * @see android.media.MediaPlayer#setAudioSessionId(int)
     */
    public void setAudioSessionId(int sessionId) {
        mAudioSessionId = sessionId;
    }

    /**
     * @see android.media.MediaPlayer#getAudioSessionId()
     */
    public int getAudioSessionId() {
        return mAudioSessionId;
    }

    private class PlaybackThread extends Thread {

        private final long mTimeOutUs = 500000;

        private ByteBuffer[] mVideoCodecInputBuffers;
        private ByteBuffer[] mVideoCodecOutputBuffers;
        private ByteBuffer[] mAudioCodecInputBuffers;
        private ByteBuffer[] mAudioCodecOutputBuffers;
        private MediaCodec.BufferInfo mVideoInfo;
        private MediaCodec.BufferInfo mAudioInfo;
        private boolean mPaused;
        private boolean mVideoInputEos;
        private boolean mVideoOutputEos;
        private boolean mAudioInputEos;
        private boolean mAudioOutputEos;
        private boolean mBuffering;
        private AudioPlayback mAudioPlayback;

        /* Flag notifying that the representation has changed in the extractor and needs to be passed
         * to the decoder. This transition state is only needed in playback, not when seeking. */
        private boolean mRepresentationChanging;
        /* Flag notifying that the decoder has changed to a new representation, post-actions need to
         * be carried out. */
        private boolean mRepresentationChanged;

        private PlaybackThread() {
            super(TAG);
            mPaused = true;
        }

        public void pause() {
            mPaused = true;
        }

        public void play() {
            mPaused = false;
            synchronized (this) {
                notify();
            }
        }

        public boolean isPaused() {
            return mPaused;
        }

        @Override
        public void run() {
            try {
                mVideoCodec = MediaCodec.createDecoderByType(mVideoFormat.getString(MediaFormat.KEY_MIME));
                mVideoCodec.configure(mVideoFormat, mSurface, null, 0);
                mVideoCodec.start();
                mVideoCodecInputBuffers = mVideoCodec.getInputBuffers();
                mVideoCodecOutputBuffers = mVideoCodec.getOutputBuffers();
                mVideoInfo = new MediaCodec.BufferInfo();
                mVideoInputEos = false;
                mVideoOutputEos = false;

                if(mAudioFormat != null) {
                    mAudioCodec = MediaCodec.createDecoderByType(mAudioFormat.getString(MediaFormat.KEY_MIME));
                    mAudioCodec.configure(mAudioFormat, null, null, 0);
                    mAudioCodec.start();
                    mAudioCodecInputBuffers = mAudioCodec.getInputBuffers();
                    mAudioCodecOutputBuffers = mAudioCodec.getOutputBuffers();
                    mAudioInfo = new MediaCodec.BufferInfo();
                    mAudioInputEos = false;
                    mAudioOutputEos = false;

                    mAudioPlayback = new AudioPlayback();
                    mAudioPlayback.setAudioSessionId(mAudioSessionId);
                    mAudioPlayback.init(mAudioFormat);
                    mAudioSessionId = mAudioPlayback.getAudioSessionId();
                }

                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_SET_VIDEO_SIZE,
                        getVideoWidth(), getVideoHeight()));

                mBuffering = false;
                boolean preparing = true; // used on startup to process stream until the first frame
                int frameSkipCount = 0;
                long lastPTS = 0;

                // Initialize the time base with the first PTS that must not necessarily be 0
                mTimeBase.startAt(mVideoMinPTS);

                while (!mVideoOutputEos) {
                    if (mPaused && !mSeekPrepare && !mSeeking && !preparing) {
                        if(mAudioPlayback != null) mAudioPlayback.pause();
                        synchronized (this) {
                            while (mPaused && !mSeekPrepare && !mSeeking) {
                                this.wait();
                            }
                        }

                        if(mAudioPlayback != null) mAudioPlayback.play();
                        // reset time (otherwise playback tries to "catch up" time after a pause)
                        mTimeBase.startAt(mVideoInfo.presentationTimeUs);
                    }

                    // For a seek, first prepare by seeking the media extractor and flushing the codec.
                    /* This needs to be in a loop, because the seek inside this block can take some
                     * time, meanwhile another seek can be issued. The following seek can update the seek
                     * target while the video extractor is still seeking, which can turn into a problem
                     * with DASH when the second target is not within the first requested segment - in
                     * which case the seek cannot find the new target in the old segment. By checking
                     * if a seek needs to be prepared after the video extractor seek has finished (which
                     * means another seek hat been issued), it can be run again and again while new
                     * seek targets come in, until the target finally stays the same during a seek.
                     */
                    while (mSeekPrepare) {
                        mSeekPrepare = false;
                        mSeeking = true;

                        Log.d(TAG, "seeking to:                 " + mSeekTargetTime);
                        Log.d(TAG, "frame current position:     " + mCurrentPosition);
                        Log.d(TAG, "extractor current position: " + mVideoExtractor.getSampleTime());

                        mVideoExtractor.seekTo(mSeekTargetTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        mCurrentPosition = mSeekTargetTime;

                        Log.d(TAG, "extractor new position:     " + mVideoExtractor.getSampleTime());

                        if(mSeekPrepare) {
                            // Another seek has been issued in the meantime, repeat this block
                            continue;
                        }

                        mVideoInputEos = false;
                        mVideoOutputEos = false;
                        mAudioInputEos = false;
                        mAudioOutputEos = false;
                        mVideoCodec.flush();
                        if (mAudioFormat != null) mAudioCodec.flush();

                        if(mVideoExtractor.hasTrackFormatChanged()) {
                            reinitCodecs();
                            mRepresentationChanged = true;
                        }

                        if(mSeekMode == SeekMode.FAST_EXACT) {
                            /* Check if the seek target time after the seek is the same as before the
                             * seek. If not, a new seek has been issued in the meantime; the current
                             * one must be discarded and a new seek with the new target time started.
                             */
                            long in;
                            long out;
                            do {
                                in = mSeekTargetTime;
                                out = fastSeek(in);
                            } while (in != mSeekTargetTime);
                            mSeekTargetTime = out;
                            mSeekPrepare = false;
                        }
                    }

                    if (!mVideoInputEos && !mRepresentationChanging) {
                        queueMediaSampleToCodec(mSeeking);
                    }
                    lastPTS = mVideoInfo.presentationTimeUs;
                    int res = mVideoCodec.dequeueOutputBuffer(mVideoInfo, mTimeOutUs);
                    mVideoOutputEos = res >= 0 && (mVideoInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    if(mVideoOutputEos && mRepresentationChanging) {
                        /* Here, the video output is not really at its end, it's just the end of the
                         * current representation segment, and the codec needs to be reconfigured to
                         * the following representation format to carry on.
                         */

                        reinitCodecs();

                        mVideoOutputEos = false;
                        mRepresentationChanging = false;
                        mRepresentationChanged = true;
                    }
                    else if (res >= 0) {
                        int outputBufIndex = res;
                        boolean render = true;
                        //ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                        //Log.d(TAG, "pts=" + info.presentationTimeUs);

                        if (mSeeking) {
                            if(mVideoOutputEos) {
                                /* When the end of stream is reached while seeking, the seek target
                                 * time is set to the last frame's PTS, else the seek skips the last
                                 * frame which then does not get rendered, and it might end up in a
                                 * loop trying to reach the unreachable target time. */
                                mSeekTargetTime = mVideoInfo.presentationTimeUs;
                            }
                            /* NOTE
                             * This code seeks one frame too far, except if the seek time equals the
                             * frame PTS:
                             * (F1.....)(F2.....)(F3.....) ... (Fn.....)
                             * A frame is shown for an interval, e.g. (1/fps seconds). Now if the seek
                             * target time is somewhere in frame 2's interval, we end up with frame 3
                             * because we need to decode it to know if the seek target time lies in
                             * frame 2's interval (because we don't know the frame rate of the video,
                             * and neither if it's a fixed frame rate or a variable one - even when
                             * deriving it from the PTS series we cannot be sure about it). This means
                             * we always end up one frame too far, because the MediaCodec does not allow
                             * to go back, except when starting at a sync frame.
                             *
                             * Solution for fixed frame rate could be to subtract the frame interval
                             * time (1/fps secs) from the seek target time.
                             *
                             * Solution for variable frame rate and unknown frame rate: go back to sync
                             * frame and re-seek to the now known exact PTS of the desired frame.
                             * See EXACT mode handling below.
                             */
                            /* Android API compatibility:
                             * Use millisecond precision to stay compatible with VideoView API that works
                             * in millisecond precision only. Else, exact seek matches are missed if frames
                             * are positioned at fractions of a millisecond. */
                            long presentationTimeMs = mVideoInfo.presentationTimeUs / 1000;
                            long seekTargetTimeMs = mSeekTargetTime / 1000;
                            if ((mSeekMode == SeekMode.PRECISE || mSeekMode == SeekMode.EXACT) && presentationTimeMs < seekTargetTimeMs) {
                                // this is not yet the aimed time so we skip rendering of this frame
                                render = false;
                                if(frameSkipCount == 0) {
                                    Log.d(TAG, "skipping frames...");
                                }
                                frameSkipCount++;
                            } else {
                                Log.d(TAG, "frame new position:         " + mVideoInfo.presentationTimeUs);
                                Log.d(TAG, "seeking finished, skipped " + frameSkipCount + " frames");
                                frameSkipCount = 0;

                                if(mSeekMode == SeekMode.EXACT && presentationTimeMs > seekTargetTimeMs) {
                                    /* If the current frame's PTS it after the seek target time, we're
                                     * one frame too far into the stream. This is because we do not know
                                     * the frame rate of the video and therefore can't decide for a frame
                                     * if its interval covers the seek target time of if there's already
                                     * another frame coming. We know after the next frame has been
                                     * decoded though if we're too far into the stream, and if so, and if
                                     * EXACT mode is desired, we need to take the previous frame's PTS
                                     * and repeat the seek with that PTS to arrive at the desired frame.
                                     */
                                    Log.d(TAG, "exact seek: repeat seek for previous frame at " + lastPTS);
                                    render = false;
                                    seekToInternal(lastPTS);
                                } else {
                                    if(presentationTimeMs == seekTargetTimeMs) {
                                        Log.d(TAG, "exact seek match!");
                                    }

                                    if(mSeekMode == SeekMode.FAST_EXACT && mVideoInfo.presentationTimeUs < mSeekTargetTime) {
                                        // this should only ever happen in fastseek mode, else it FFWs in fast mode
                                        Log.d(TAG, "presentation is behind, another try...");
                                    } else {
                                        // reset time to keep frame rate constant (otherwise it's too fast on back seeks and waits for the PTS time on fw seeks)
                                        mTimeBase.startAt(mVideoInfo.presentationTimeUs);
                                        mCurrentPosition = mVideoInfo.presentationTimeUs;
                                        mSeeking = false;
                                        if(mAudioExtractor != null) {
                                            mAudioExtractor.seekTo(mVideoInfo.presentationTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                                            mAudioPlayback.flush();
                                        }
                                        mEventHandler.sendEmptyMessage(MEDIA_SEEK_COMPLETE);
                                    }
                                }
                            }
                        } else {
                            mCurrentPosition = mVideoInfo.presentationTimeUs;

                            long waitingTime = mTimeBase.getOffsetFrom(mVideoInfo.presentationTimeUs);

                            if(mAudioFormat != null) {
                                long audioOffsetUs = mAudioPlayback.getLastPresentationTimeUs() - mCurrentPosition;
//                                Log.d(TAG, "VideoPTS=" + mCurrentPosition
//                                        + " AudioPTS=" + mAudioPlayback.getLastPresentationTimeUs()
//                                        + " offset=" + audioOffsetUs);
                                /* Synchronize the video frame PTS to the audio PTS by slowly adjusting
                                 * the video frame waiting time towards a better synchronization.
                                 * Directly correcting the video waiting time by the audio offset
                                 * introduces too much jitter and leads to juddering video playback.
                                 */
                                long audioOffsetCorrectionUs = 10000;
                                if (audioOffsetUs > audioOffsetCorrectionUs) {
                                    waitingTime -= audioOffsetCorrectionUs;
                                } else if(audioOffsetUs < -audioOffsetCorrectionUs) {
                                    waitingTime += audioOffsetCorrectionUs;
                                }

                                mAudioPlayback.setPlaybackSpeed((float)mTimeBase.getSpeed());
                            }

                            //Log.d(TAG, "waiting time = " + waitingTime);

                            /* If this is an online stream, notify the client of the buffer fill level.
                             * The cached duration from the MediaExtractor returns the cached time from
                             * the current position onwards, but the Android mediaPlayer returns the
                             * total time consisting fo the current playback point and the length of
                             * the prefetched data.
                             */
                            long cachedDuration = mVideoExtractor.getCachedDuration();
                            if(cachedDuration != -1) {
                                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_BUFFERING_UPDATE,
                                        (int) (100d / mVideoFormat.getLong(MediaFormat.KEY_DURATION) * (mCurrentPosition + cachedDuration)), 0));
                            }

                            // slow down playback, if necessary, to keep frame rate
                            if (!preparing && waitingTime > 5000) {
                                // sleep until it's time to render the next frame
                                // TODO find better alternative to sleep
                                // The sleep takes much longer than expected, leading to playback
                                // jitter, and depending on the structure of a container and the
                                // data sequence, dropouts in the audio playback stream.
                                Thread.sleep(waitingTime / 1000);
                            } else if(!preparing && waitingTime < 0) {
                                // we weed to catch up time by skipping rendering of this frame
                                // this doesn't gain enough time if playback speed is too high and decoder at full load
                                // TODO improve fast forward mode
                                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                                        MEDIA_INFO_VIDEO_TRACK_LAGGING, 0));
                                mTimeBase.startAt(mVideoInfo.presentationTimeUs);
                            }
                        }

                        if(mBuffering) {
                            mBuffering = false;
                            mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                                    MEDIA_INFO_BUFFERING_END, 0));
                        }

                        /* Defer the video size changed message until the first frame of the new
                         * size is being rendered. */
                        if(mRepresentationChanged && render) {
                            mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_SET_VIDEO_SIZE,
                                    getVideoWidth(), getVideoHeight()));
                            mRepresentationChanged = false;
                        }

                        mVideoCodec.releaseOutputBuffer(outputBufIndex, render); // render picture

                        if (mAudioFormat != null & mAudioExtractor != null && !mSeeking && !mPaused) {
                            // TODO rewrite; this is just a quick and dirty hack
                            long start = SystemClock.elapsedRealtime();
                            while (mAudioPlayback.getBufferTimeUs() < 100000) {
                                if (queueAudioSampleToCodec(mAudioExtractor)) {
                                    decodeAudioSample();
                                } else {
                                    break;
                                }
                            }
                            //Log.d(TAG, "audio stream decode time " + (SystemClock.elapsedRealtime() - start));
                        }

                        if (preparing) {
                            mEventHandler.sendEmptyMessage(MEDIA_PREPARED);
                            preparing = false;
                            mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                                    MEDIA_INFO_VIDEO_RENDERING_START, 0));
                        }

                        if (mVideoOutputEos) {
                            Log.d(TAG, "EOS video output");
                            mEventHandler.sendEmptyMessage(MEDIA_PLAYBACK_COMPLETE);

                            /* When playback reached the end of the stream and the last frame is
                             * displayed, we go into paused state instead of finishing the thread
                             * and wait until a new start or seek command is arriving. If no
                             * command arrives, the thread stays sleeping until the player is
                             * stopped.
                             */
                            mPaused = true;
                            synchronized (this) {
                                if(mAudioPlayback != null) mAudioPlayback.pause();
                                while (mPaused && !mSeeking && !mSeekPrepare) {
                                    this.wait();
                                }
                                if(mAudioPlayback != null) mAudioPlayback.play();

                                // reset these flags so playback can continue
                                mVideoInputEos = false;
                                mVideoOutputEos = false;
                                mAudioInputEos = false;
                                mAudioOutputEos = false;

                                // if no seek command but a start command arrived, seek to the start
                                if(!mSeeking && !mSeekPrepare) {
                                    seekToInternal(0);
                                }
                            }
                        }
                    } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        mVideoCodecOutputBuffers = mVideoCodec.getOutputBuffers();
                        Log.d(TAG, "output buffers have changed.");
                    } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // NOTE: this is the format of the raw video output, not the video format as specified by the container
                        MediaFormat oformat = mVideoCodec.getOutputFormat();
                        Log.d(TAG, "output format has changed to " + oformat);
                    } else if (res == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "dequeueOutputBuffer timed out");
                    }
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "decoder interrupted");
                interrupt();
                if(mIsStopping) {
                    // An intentional stop does not trigger an error message
                    mIsStopping = false;
                } else {
                    // Unexpected interruption, send error message
                    mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                            MEDIA_ERROR_UNKNOWN, 0));
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "decoder error, too many instances?", e);
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, 0));
            } catch (IOException e) {
                Log.e(TAG, "decoder error, codec can not be created", e);
                mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_ERROR,
                        MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_IO));
            }

            if(mAudioPlayback != null) mAudioPlayback.stopAndRelease();
            mVideoCodec.stop();
            mVideoCodec.release();
            if(mAudioFormat != null) {
                mAudioCodec.stop();
                mAudioCodec.release();
            }
            mVideoExtractor.release();

            Log.d(TAG, "decoder released");
        }

        private boolean queueVideoSampleToCodec() {
            /* NOTE the track index checks only for debugging
             * when enabled, they prevent the EOS detection and handling below */
//            int trackIndex = mVideoExtractor.getSampleTrackIndex();
//            if(trackIndex == -1) {
//                throw new IllegalStateException("EOS");
//            }
//            if(trackIndex != mVideoTrackIndex) {
//                throw new IllegalStateException("wrong track index: " + trackIndex);
//            }
            boolean sampleQueued = false;
            int inputBufIndex = mVideoCodec.dequeueInputBuffer(mTimeOutUs);
            if (inputBufIndex >= 0) {
                ByteBuffer inputBuffer = mVideoCodecInputBuffers[inputBufIndex];
                int sampleSize = mVideoExtractor.readSampleData(inputBuffer, 0);
                long presentationTimeUs = 0;

                if(sampleSize == 0) {
                    if(mVideoExtractor.getCachedDuration() == 0) {
                        mBuffering = true;
                        mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                                MEDIA_INFO_BUFFERING_START, 0));
                    }
                    if(mVideoExtractor.hasTrackFormatChanged()) {
                        /* The mRepresentationChanging flag and BUFFER_FLAG_END_OF_STREAM flag together
                         * notify the decoding loop that the representation changes and the codec
                         * needs to be reconfigured.
                         */
                        mRepresentationChanging = true;
                        mVideoCodec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                } else {
                    if (sampleSize < 0) {
                        Log.d(TAG, "EOS video input");
                        mVideoInputEos = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = mVideoExtractor.getSampleTime();
                        sampleQueued = true;
                    }

                    mVideoCodec.queueInputBuffer(
                            inputBufIndex,
                            0,
                            sampleSize,
                            presentationTimeUs,
                            mVideoInputEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!mVideoInputEos) {
                        mVideoExtractor.advance();
                    }
                }
            }
            return sampleQueued;
        }

        private boolean queueAudioSampleToCodec(MediaExtractor extractor) {
            if(mAudioCodec == null) {
                throw new IllegalStateException("no audio track configured");
            }
            /* NOTE the track index checks only for debugging
             * when enabled, they prevent the EOS detection and handling below */
//            int trackIndex = extractor.getSampleTrackIndex();
//            if(trackIndex == -1) {
//                throw new IllegalStateException("EOS");
//            }
//            if(trackIndex != mAudioTrackIndex) {
//                throw new IllegalStateException("wrong track index: " + trackIndex);
//            }
            boolean sampleQueued = false;
            int inputBufIndex = mAudioCodec.dequeueInputBuffer(mTimeOutUs);
            if (inputBufIndex >= 0) {
                ByteBuffer inputBuffer = mAudioCodecInputBuffers[inputBufIndex];
                int sampleSize = extractor.readSampleData(inputBuffer, 0);
                long presentationTimeUs = 0;
                if (sampleSize < 0) {
                    Log.d(TAG, "EOS audio input");
                    mAudioInputEos = true;
                    sampleSize = 0;
                } else if(sampleSize == 0) {
                    if(extractor.getCachedDuration() == 0) {
                        mBuffering = true;
                        mEventHandler.sendMessage(mEventHandler.obtainMessage(MEDIA_INFO,
                                MEDIA_INFO_BUFFERING_START, 0));
                    }
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                    sampleQueued = true;
                }
                mAudioCodec.queueInputBuffer(
                        inputBufIndex,
                        0,
                        sampleSize,
                        presentationTimeUs,
                        mAudioInputEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                if (!mAudioInputEos) {
                    extractor.advance();
                }
            }
            return sampleQueued;
        }

        private void decodeAudioSample() {
            int output = mAudioCodec.dequeueOutputBuffer(mAudioInfo, mTimeOutUs);
            if (output >= 0) {
                // http://bigflake.com/mediacodec/#q11
                ByteBuffer outputData = mAudioCodecOutputBuffers[output];
                if (mAudioInfo.size != 0) {
                    outputData.position(mAudioInfo.offset);
                    outputData.limit(mAudioInfo.offset + mAudioInfo.size);
                    //Log.d(TAG, "raw audio data bytes: " + mVideoInfo.size);
                }
                mAudioPlayback.write(outputData, mAudioInfo.presentationTimeUs);
                mAudioCodec.releaseOutputBuffer(output, false);

                if ((mAudioInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mAudioOutputEos = true;
                    Log.d(TAG, "EOS audio output");
                }
            } else if (output == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "audio output buffers have changed.");
                mAudioCodecOutputBuffers = mAudioCodec.getOutputBuffers();
            } else if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mAudioCodec.getOutputFormat();
                Log.d(TAG, "audio output format has changed to " + format);
                mAudioPlayback.init(format);
            } else if (output == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "audio dequeueOutputBuffer timed out");
            }
        }

        private boolean queueMediaSampleToCodec(boolean videoOnly) {
            boolean result = false;
            if(mAudioCodec != null) {
                if (videoOnly) {
                    /* VideoOnly mode skips audio samples, e.g. while doing a seek operation. */
                    int trackIndex;
                    while ((trackIndex = mVideoExtractor.getSampleTrackIndex()) != -1 && trackIndex != mVideoTrackIndex && !mVideoInputEos) {
                        mVideoExtractor.advance();
                    }
                } else {
                    while (mAudioExtractor == null && mVideoExtractor.getSampleTrackIndex() == mAudioTrackIndex) {
                        result = queueAudioSampleToCodec(mVideoExtractor);
                        decodeAudioSample();
                    }
                }
            }
            if(!mVideoInputEos) {
                result = queueVideoSampleToCodec();
            }
            return result;
        }

        private long fastSeek(long targetTime) throws IOException {
            mVideoCodec.flush();
            if(mAudioFormat != null) mAudioCodec.flush();
            mVideoExtractor.seekTo(targetTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            if(mVideoExtractor.getSampleTime() == targetTime) {
                Log.d(TAG, "skip fastseek, already there");
                return targetTime;
            }

            // 1. Queue first sample which should be the sync/I frame
            queueMediaSampleToCodec(true);

            // 2. Then, fast forward to target frame
                            /* 2.1 Search for the best candidate frame, which is the one whose
                             *     right/positive/future distance is minimized
                             */
            mVideoExtractor.seekTo(targetTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

                            /* Specifies how many frames we continue to check after the first candidate,
                             * to account for DTS picture reordering (this value is arbitrarily chosen) */
            int maxFrameLookahead = 20;

            long candidatePTS = 0;
            long candidateDistance = Long.MAX_VALUE;
            int lookaheadCount = 0;

            while (mVideoExtractor.advance() && lookaheadCount < maxFrameLookahead) {
                long distance = targetTime - mVideoExtractor.getSampleTime();
                if (distance >= 0 && distance < candidateDistance) {
                    candidateDistance = distance;
                    candidatePTS = mVideoExtractor.getSampleTime();
                    //Log.d(TAG, "candidate " + candidatePTS + " d=" + candidateDistance);
                }
                if (distance < 0) {
                    lookaheadCount++;
                }
            }
            targetTime = candidatePTS; // set best candidate frame as exact seek target

            // 2.2 Fast forward to chosen candidate frame
            mVideoExtractor.seekTo(targetTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            while (mVideoExtractor.getSampleTime() != targetTime) {
                mVideoExtractor.advance();
            }
            Log.d(TAG, "exact fastseek match:       " + mVideoExtractor.getSampleTime());

            return targetTime;
        }

        /**
         * Restarts the codecs with a new format, e.g. after a representation change.
         */
        private void reinitCodecs() {
            long t1 = SystemClock.elapsedRealtime();

            // Get new video format and restart video codec with this format
            mVideoFormat = mVideoExtractor.getTrackFormat(mVideoTrackIndex);

            mVideoCodec.stop();
            mVideoCodec.configure(mVideoFormat, mSurface, null, 0);
            mVideoCodec.start();
            mVideoCodecInputBuffers = mVideoCodec.getInputBuffers();
            mVideoCodecOutputBuffers = mVideoCodec.getOutputBuffers();

            if(mAudioFormat != null) {
                mAudioCodec.stop();
                mAudioCodec.configure(mAudioFormat, null, null, 0);
                mAudioCodec.start();
                mAudioCodecInputBuffers = mAudioCodec.getInputBuffers();
                mAudioCodecOutputBuffers = mAudioCodec.getOutputBuffers();
            }

            Log.d(TAG, "reinitCodecs " + (SystemClock.elapsedRealtime() - t1) + "ms");
        }
    }

    /**
     * Interface definition for a callback to be invoked when the media
     * source is ready for playback.
     */
    public interface OnPreparedListener {
        /**
         * Called when the media file is ready for playback.
         * @param mp the MediaPlayer that is ready for playback
         */
        void onPrepared(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when the media source is ready
     * for playback.
     *
     * @param listener the callback that will be run
     */
    public void setOnPreparedListener(OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    /**
     * Interface definition for a callback to be invoked when playback of
     * a media source has completed.
     */
    public interface OnCompletionListener {
        /**
         * Called when the end of a media source is reached during playback.
         * @param mp the MediaPlayer that reached the end of the file
         */
        void onCompletion(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when the end of a media source
     * has been reached during playback.
     *
     * @param listener the callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked when a seek
     * is issued.
     */
    public interface OnSeekListener {
        /**
         * Called to indicate that a seek operation has been started.
         * @param mp the mediaPlayer that the seek was called on
         */
        public void onSeek(MediaPlayer mp);
    }

    /**
     * Register a calback to be invoked when a seek operation has been started.
     * @param listener the callback that will be run
     */
    public void setOnSeekListener(OnSeekListener listener) {
        mOnSeekListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked indicating
     * the completion of a seek operation.
     */
    public interface OnSeekCompleteListener {
        /**
         * Called to indicate the completion of a seek operation.
         * @param mp the MediaPlayer that issued the seek operation
         */
        public void onSeekComplete(MediaPlayer mp);
    }

    /**
     * Register a callback to be invoked when a seek operation has been
     * completed.
     *
     * @param listener the callback that will be run
     */
    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked when the
     * video size is first known or updated
     */
    public interface OnVideoSizeChangedListener
    {
        /**
         * Called to indicate the video size
         *
         * The video size (width and height) could be 0 if there was no video,
         * no display surface was set, or the value was not determined yet.
         *
         * @param mp        the MediaPlayer associated with this callback
         * @param width     the width of the video
         * @param height    the height of the video
         */
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height);
    }

    /**
     * Register a callback to be invoked when the video size is
     * known or updated.
     *
     * @param listener the callback that will be run
     */
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
    }

    /**
     * Interface definition of a callback to be invoked indicating buffering
     * status of a media resource being streamed over the network.
     */
    public interface OnBufferingUpdateListener {
        /**
         * Called to update status in buffering a media stream received through
         * progressive HTTP download. The received buffering percentage
         * indicates how much of the content has been buffered or played.
         * For example a buffering update of 80 percent when half the content
         * has already been played indicates that the next 30 percent of the
         * content to play has been buffered.
         *
         * @param mp      the MediaPlayer the update pertains to
         * @param percent the percentage (0-100) of the content
         *                that has been buffered or played thus far
         */
        void onBufferingUpdate(MediaPlayer mp, int percent);
    }

    /**
     * Register a callback to be invoked when the status of a network
     * stream's buffer has changed.
     *
     * @param listener the callback that will be run.
     */
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
        mOnBufferingUpdateListener = listener;
    }

    /** Unspecified media player error.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_UNKNOWN = 1;

    /** Media server died. In this case, the application must release the
     * MediaPlayer object and instantiate a new one.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_SERVER_DIED = 100;

    /** The video is streamed and its container is not valid for progressive
     * playback i.e the video's index (e.g moov atom) is not at the start of the
     * file.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;

    /** File or network related operation errors. */
    public static final int MEDIA_ERROR_IO = -1004;
    /** Bitstream is not conforming to the related coding standard or file spec. */
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    /** Bitstream is conforming to the related coding standard or file spec, but
     * the media framework does not support the feature. */
    public static final int MEDIA_ERROR_UNSUPPORTED = -1010;
    /** Some operation takes too long to complete, usually more than 3-5 seconds. */
    public static final int MEDIA_ERROR_TIMED_OUT = -110;

    /**
     * Interface definition of a callback to be invoked when there
     * has been an error during an asynchronous operation (other errors
     * will throw exceptions at method call time).
     */
    public interface OnErrorListener
    {
        /**
         * Called to indicate an error.
         *
         * @param mp      the MediaPlayer the error pertains to
         * @param what    the type of error that has occurred:
         * <ul>
         * <li>{@link #MEDIA_ERROR_UNKNOWN}
         * <li>{@link #MEDIA_ERROR_SERVER_DIED}
         * </ul>
         * @param extra an extra code, specific to the error. Typically
         * implementation dependent.
         * <ul>
         * <li>{@link #MEDIA_ERROR_IO}
         * <li>{@link #MEDIA_ERROR_MALFORMED}
         * <li>{@link #MEDIA_ERROR_UNSUPPORTED}
         * <li>{@link #MEDIA_ERROR_TIMED_OUT}
         * </ul>
         * @return True if the method handled the error, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the OnCompletionListener to be called.
         */
        boolean onError(MediaPlayer mp, int what, int extra);
    }

    /**
     * Register a callback to be invoked when an error has happened
     * during an asynchronous operation.
     *
     * @param listener the callback that will be run
     */
    public void setOnErrorListener(OnErrorListener listener)
    {
        mOnErrorListener = listener;
    }

    /** The player just pushed the very first video frame for rendering.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;

    /** The video is too complex for the decoder: it can't decode frames fast
     *  enough. Possibly only the audio plays fine at this stage.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;

    /** MediaPlayer is temporarily pausing playback internally in order to
     * buffer more data.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BUFFERING_START = 701;

    /** MediaPlayer is resuming playback after filling buffers.
     * @see at.aau.itec.android.mediaplayer.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BUFFERING_END = 702;

    /**
     * Interface definition of a callback to be invoked to communicate some
     * info and/or warning about the media or its playback.
     */
    public interface OnInfoListener {
        /**
         * Called to indicate an info or a warning.
         *
         * @param mp      the MediaPlayer the info pertains to.
         * @param what    the type of info or warning.
         * <ul>
         * <li>{@link #MEDIA_INFO_VIDEO_TRACK_LAGGING}
         * <li>{@link #MEDIA_INFO_VIDEO_RENDERING_START}
         * <li>{@link #MEDIA_INFO_BUFFERING_START}
         * <li>{@link #MEDIA_INFO_BUFFERING_END}
         * </ul>
         * @param extra an extra code, specific to the info. Typically
         * implementation dependent.
         * @return True if the method handled the info, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the info to be discarded.
         */
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    /**
     * Register a callback to be invoked when an info/warning is available.
     * @param listener the callback that will be run
     */
    public void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MEDIA_PREPARED:
                    Log.d(TAG, "onPrepared");
                    if(mOnPreparedListener != null) {
                        mOnPreparedListener.onPrepared(MediaPlayer.this);
                    }
                    return;
                case MEDIA_SEEK_COMPLETE:
                    Log.d(TAG, "onSeekComplete");
                    if (mOnSeekCompleteListener != null) {
                        mOnSeekCompleteListener.onSeekComplete(MediaPlayer.this);
                    }
                    return;
                case MEDIA_PLAYBACK_COMPLETE:
                    Log.d(TAG, "onPlaybackComplete");
                    if(mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(MediaPlayer.this);
                    }
                    stayAwake(false);
                    return;
                case MEDIA_SET_VIDEO_SIZE:
                    Log.d(TAG, "onVideoSizeChanged");
                    if(mOnVideoSizeChangedListener != null) {
                        mOnVideoSizeChangedListener.onVideoSizeChanged(MediaPlayer.this, msg.arg1, msg.arg2);
                    }
                    return;
                case MEDIA_ERROR:
                    Log.e(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
                    boolean error_was_handled = false;
                    if (mOnErrorListener != null) {
                        error_was_handled = mOnErrorListener.onError(MediaPlayer.this, msg.arg1, msg.arg2);
                    }
                    if (mOnCompletionListener != null && !error_was_handled) {
                        mOnCompletionListener.onCompletion(MediaPlayer.this);
                    }
                    stayAwake(false);
                    return;
                case MEDIA_INFO:
                    Log.d(TAG, "onInfo");
                    if(mOnInfoListener != null) {
                        mOnInfoListener.onInfo(MediaPlayer.this, msg.arg1, msg.arg2);
                    }
                    return;
                case MEDIA_BUFFERING_UPDATE:
                    //Log.d(TAG, "onBufferingUpdate");
                    if (mOnBufferingUpdateListener != null)
                        mOnBufferingUpdateListener.onBufferingUpdate(MediaPlayer.this, msg.arg1);
                    mBufferPercentage = msg.arg1;
                    return;

                default:
                    // nothing
            }
        }
    }
}
