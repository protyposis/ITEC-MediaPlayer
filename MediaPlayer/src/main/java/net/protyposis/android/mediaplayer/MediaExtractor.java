/*
 * Copyright 2014 Mario Guggenberger <mg@protyposis.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.protyposis.android.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

/**
 * MediaExtractor facilitates extraction of demuxed, typically encoded,  media data
 * from a data source.
 * <p>It is generally used like this:
 * <pre>
 * MediaExtractor extractor = new MediaExtractor();
 * extractor.setDataSource(...);
 * int numTracks = extractor.getTrackCount();
 * for (int i = 0; i &lt; numTracks; ++i) {
 *   MediaFormat format = extractor.getTrackFormat(i);
 *   String mime = format.getString(MediaFormat.KEY_MIME);
 *   if (weAreInterestedInThisTrack) {
 *     extractor.selectTrack(i);
 *   }
 * }
 * ByteBuffer inputBuffer = ByteBuffer.allocate(...)
 * while (extractor.readSampleData(inputBuffer, ...) &gt;= 0) {
 *   int trackIndex = extractor.getSampleTrackIndex();
 *   long presentationTimeUs = extractor.getSampleTime();
 *   ...
 *   extractor.advance();
 * }
 *
 * extractor.release();
 * extractor = null;
 * </pre>
 *
 * This class wraps Android API's extractor because it is final and cannot be extended. The ability
 * for extension is needed to keep the MediaPlayer code clean and encapsulate more sophisticated
 * processing of data sources.
 *
 * Created by maguggen on 27.08.2014.
 *
 * @see android.media.MediaExtractor
 */
public class MediaExtractor {

    public static final String MEDIA_FORMAT_EXTENSION_KEY_DAR = "mpx-dar";

    private android.media.MediaExtractor mApiExtractor;

    public MediaExtractor() {
        renewExtractor();
    }

    protected void renewExtractor() {
        if(mApiExtractor != null) {
            mApiExtractor.release();
        }
        mApiExtractor = new android.media.MediaExtractor();
    }

    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to extract from.
     * @param headers the headers to be sent together with the request for the data
     */
    public final void setDataSource(
            Context context, Uri uri, Map<String, String> headers)
            throws IOException {
        mApiExtractor.setDataSource(context, uri, headers);
    }

    /**
     * Sets the data source (file-path or http URL) to use.
     *
     * @param path the path of the file, or the http URL
     * @param headers the headers associated with the http request for the stream you want to play
     */
    public final void setDataSource(String path, Map<String, String> headers)
            throws IOException {
        mApiExtractor.setDataSource(path, headers);
    }

    /**
     * Sets the data source (file-path or http URL) to use.
     *
     * @param path the path of the file, or the http URL of the stream
     *
     * <p>When <code>path</code> refers to a local file, the file may actually be opened by a
     * process other than the calling application.  This implies that the pathname
     * should be an absolute path (as any other process runs with unspecified current working
     * directory), and that the pathname should reference a world-readable file.
     * As an alternative, the application could first open the file for reading,
     * and then use the file descriptor form {@link #setDataSource(FileDescriptor)}.
     */
    public final void setDataSource(String path) throws IOException {
        mApiExtractor.setDataSource(path);
    }

    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to extract from.
     */
    public final void setDataSource(FileDescriptor fd) throws IOException {
        mApiExtractor.setDataSource(fd);
    }

    /**
     * Sets the data source (FileDescriptor) to use.  The FileDescriptor must be
     * seekable (N.B. a LocalSocket is not seekable). It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to extract from.
     * @param offset the offset into the file where the data to be extracted starts, in bytes
     * @param length the length in bytes of the data to be extracted
     */
    public final void setDataSource(
            FileDescriptor fd, long offset, long length) throws IOException {
        mApiExtractor.setDataSource(fd, offset, length);
    }


    /**
     * Make sure you call this when you're done to free up any resources
     * instead of relying on the garbage collector to do this for you at
     * some point in the future.
     */
    public void release() {
        mApiExtractor.release();
    }

    /**
     * Count the number of tracks found in the data source.
     */
    public final int getTrackCount() {
        return mApiExtractor.getTrackCount();
    }

    /**
     * Get the PSSH info if present.
     * @return a map of uuid-to-bytes, with the uuid specifying
     * the crypto scheme, and the bytes being the data specific to that scheme.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public Map<UUID, byte[]> getPsshInfo() {
        return mApiExtractor.getPsshInfo();
    }

    /**
     * Get the track format at the specified index.
     * More detail on the representation can be found at {@link android.media.MediaCodec}
     */
    public MediaFormat getTrackFormat(int index) {
        MediaFormat mediaFormat = mApiExtractor.getTrackFormat(index);
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);

        // Set the default DAR
        //
        // We need to check the existence of the width/height fields because some platforms
        // return unsupported tracks as "video/unknown" mime type without the required fields to
        // calculate the DAR.
        //
        // Example:
        // Samsung Galaxy S5 Android 6.0.1 with thumbnail tracks (jpeg image)
        // MediaFormat{error-type=-1002, mime=video/unknown, isDMCMMExtractor=1, durationUs=323323000}
        if(mime.startsWith("video/")
                && mediaFormat.containsKey(MediaFormat.KEY_WIDTH)
                && mediaFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
            mediaFormat.setFloat(MEDIA_FORMAT_EXTENSION_KEY_DAR,
                    (float)mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                            / mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    CodecCapabilities.COLOR_FormatYUV420Flexible);
        }

        return mediaFormat;
    }

    /**
     * Subsequent calls to {@link #readSampleData}, {@link #getSampleTrackIndex} and
     * {@link #getSampleTime} only retrieve information for the subset of tracks
     * selected.
     * Selecting the same track multiple times has no effect, the track is
     * only selected once.
     */
    public void selectTrack(int index) {
        mApiExtractor.selectTrack(index);
    }

    /**
     * Subsequent calls to {@link #readSampleData}, {@link #getSampleTrackIndex} and
     * {@link #getSampleTime} only retrieve information for the subset of tracks
     * selected.
     */
    public void unselectTrack(int index) {
        mApiExtractor.unselectTrack(index);
    }

    /**
     * If possible, seek to a sync sample at or before the specified time
     */
    public static final int SEEK_TO_PREVIOUS_SYNC       = 0;
    /**
     * If possible, seek to a sync sample at or after the specified time
     */
    public static final int SEEK_TO_NEXT_SYNC           = 1;
    /**
     * If possible, seek to the sync sample closest to the specified time
     */
    public static final int SEEK_TO_CLOSEST_SYNC        = 2;

    /**
     * All selected tracks seek near the requested time according to the
     * specified mode.
     */
    public void seekTo(long timeUs, int mode) throws IOException {
        mApiExtractor.seekTo(timeUs, mode);
    }

    /**
     * Advance to the next sample. Returns false if no more sample data
     * is available (end of stream).
     */
    public boolean advance() {
        return mApiExtractor.advance();
    }

    /**
     * Retrieve the current encoded sample and store it in the byte buffer
     * starting at the given offset. Returns the sample size (or -1 if
     * no more samples are available).
     */
    public int readSampleData(ByteBuffer byteBuf, int offset) {
        return mApiExtractor.readSampleData(byteBuf, offset);
    }

    /**
     * Returns the track index the current sample originates from (or -1
     * if no more samples are available)
     */
    public int getSampleTrackIndex() {
        return mApiExtractor.getSampleTrackIndex();
    }

    /**
     * Returns the current sample's presentation time in microseconds.
     * or -1 if no more samples are available.
     */
    public long getSampleTime() {
        return mApiExtractor.getSampleTime();
    }

    // Keep these in sync with their equivalents in NuMediaExtractor.h
    /**
     * The sample is a sync sample
     */
    public static final int SAMPLE_FLAG_SYNC      = 1;

    /**
     * The sample is (at least partially) encrypted, see also the documentation
     * for {@link android.media.MediaCodec#queueSecureInputBuffer}
     */
    public static final int SAMPLE_FLAG_ENCRYPTED = 2;

    /**
     * Returns the current sample's flags.
     */
    public int getSampleFlags() {
        return mApiExtractor.getSampleFlags();
    }

    /**
     * If the sample flags indicate that the current sample is at least
     * partially encrypted, this call returns relevant information about
     * the structure of the sample data required for decryption.
     * @param info The android.media.MediaCodec.CryptoInfo structure
     *             to be filled in.
     * @return true iff the sample flags contain {@link #SAMPLE_FLAG_ENCRYPTED}
     */
    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
        return mApiExtractor.getSampleCryptoInfo(info);
    }

    /**
     * Returns an estimate of how much data is presently cached in memory
     * expressed in microseconds. Returns -1 if that information is unavailable
     * or not applicable (no cache).
     */
    public long getCachedDuration() {
        return mApiExtractor.getCachedDuration();
    }

    /**
     * Returns true iff we are caching data and the cache has reached the
     * end of the data stream (for now, a future seek may of course restart
     * the fetching of data).
     * This API only returns a meaningful result if {@link #getCachedDuration}
     * indicates the presence of a cache, i.e. does NOT return -1.
     */
    public boolean hasCacheReachedEndOfStream() {
        return mApiExtractor.hasCacheReachedEndOfStream();
    }

    /**
     * Returns true iff the extracted media supports intra-stream switching of formats (e.g. resolution)
     * and the format has changed. It only returns true at the first call when the format has changed,
     * then returns false until it has changed again.
     */
    public boolean hasTrackFormatChanged() {
        return false;
    }
}
