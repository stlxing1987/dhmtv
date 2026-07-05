package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.video.VideoSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoViewManager;


public class ExoMediaPlayer extends AbstractPlayer implements Player.Listener {

    protected Context mAppContext;
    protected SimpleExoPlayer mInternalPlayer;
    protected MediaSource mMediaSource;
    protected ExoMediaSourceHelper mMediaSourceHelper;

    private PlaybackParameters mSpeedPlaybackParameters;

    private boolean mIsPreparing;
    private boolean mDecodeRetried;
    private int mFormatRetryStage;

    private String mCurrentUrl;
    private Map<String, String> mCurrentHeaders;

    private LoadControl mLoadControl;
    private RenderersFactory mRenderersFactory;
    private TrackSelector mTrackSelector;

    public ExoMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
        mMediaSourceHelper = ExoMediaSourceHelper.getInstance(context);
    }

    @Override
    public void initPlayer() {
        mRenderersFactory = buildRenderersFactory();
        if (mLoadControl == null) {
            DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            ExoPlayerConfig.getMinBufferMs(),
                            ExoPlayerConfig.getMaxBufferMs(),
                            ExoPlayerConfig.getBufferForPlaybackMs(),
                            ExoPlayerConfig.getBufferForPlaybackAfterRebufferMs())
                    .setPrioritizeTimeOverSizeThresholds(true);
            int targetBytes = ExoPlayerConfig.getTargetBufferBytes();
            if (targetBytes > 0) {
                builder.setTargetBufferBytes(targetBytes);
            }
            mLoadControl = builder.build();
        }
        mTrackSelector = buildTrackSelector();
        mInternalPlayer = new SimpleExoPlayer.Builder(
                mAppContext,
                mRenderersFactory,
                mTrackSelector,
                new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(mAppContext),
                mLoadControl,
                DefaultBandwidthMeter.getSingletonInstance(mAppContext),
                new AnalyticsCollector(Clock.DEFAULT))
                .build();
        setOptions();

        if (VideoViewManager.getConfig().mIsEnableLog && mTrackSelector instanceof MappingTrackSelector) {
            mInternalPlayer.addAnalyticsListener(new EventLogger((MappingTrackSelector) mTrackSelector, "ExoPlayer"));
        }

        mInternalPlayer.addListener(this);
    }

    private RenderersFactory buildRenderersFactory() {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(mAppContext)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(
                        ExoPlayerConfig.isPreferSoftwareDecoder()
                                ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                                : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        if (ExoPlayerConfig.isPreferSoftwareDecoder()) {
            factory.setMediaCodecSelector(createSoftwarePreferSelector());
        }
        return factory;
    }

    private TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(mAppContext);
        DefaultTrackSelector.Parameters params = trackSelector.buildUponParameters()
                .setTunnelingEnabled(ExoPlayerConfig.isTunnelingEnabled())
                .setForceHighestSupportedBitrate(true)
                .build();
        trackSelector.setParameters(params);
        return trackSelector;
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        mTrackSelector = trackSelector;
    }

    public void setRenderersFactory(RenderersFactory renderersFactory) {
        mRenderersFactory = renderersFactory;
    }

    public void setLoadControl(LoadControl loadControl) {
        mLoadControl = loadControl;
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mCurrentUrl = path;
        mCurrentHeaders = headers;
        mDecodeRetried = false;
        mFormatRetryStage = 0;
        ExoPlayerConfig.resetPlaybackHints();
        mMediaSource = mMediaSourceHelper.getMediaSource(path, headers, ExoPlayerConfig.isCacheEnabled());
    }

    private static MediaCodecSelector createSoftwarePreferSelector() {
        return new MediaCodecSelector() {
            @Override
            public List<MediaCodecInfo> getDecoderInfos(
                    String mimeType,
                    boolean requiresSecureDecoder,
                    boolean requiresTunnelingDecoder) throws MediaCodecUtil.DecoderQueryException {
                List<MediaCodecInfo> decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                        mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                List<MediaCodecInfo> preferred = new ArrayList<>();
                for (MediaCodecInfo info : decoders) {
                    if (!info.hardwareAccelerated) {
                        preferred.add(info);
                    }
                }
                return preferred.isEmpty() ? decoders : preferred;
            }
        };
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        //no support
    }

    @Override
    public void start() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.stop();
    }

    @Override
    public void prepareAsync() {
        if (mInternalPlayer == null)
            return;
        if (mMediaSource == null) return;
        if (mSpeedPlaybackParameters != null) {
            mInternalPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }
        mIsPreparing = true;
        mInternalPlayer.setMediaSource(mMediaSource);
        mInternalPlayer.prepare();
    }

    @Override
    public void reset() {
        if (mInternalPlayer != null) {
            mInternalPlayer.stop();
            mInternalPlayer.clearMediaItems();
            mInternalPlayer.setVideoSurface(null);
            mIsPreparing = false;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null)
            return false;
        int state = mInternalPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mInternalPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.seekTo(time);
    }

    @Override
    public void release() {
        if (mInternalPlayer != null) {
            mInternalPlayer.removeListener(this);
            mInternalPlayer.release();
            mInternalPlayer = null;
        }

        mIsPreparing = false;
        mSpeedPlaybackParameters = null;
        mRenderersFactory = null;
        mTrackSelector = null;
    }

    @Override
    public long getCurrentPosition() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getDuration();
    }

    @Override
    public int getBufferedPercentage() {
        return mInternalPlayer == null ? 0 : mInternalPlayer.getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface surface) {
        if (mInternalPlayer != null) {
            mInternalPlayer.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null)
            setSurface(null);
        else
            setSurface(holder.getSurface());
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mInternalPlayer != null)
            mInternalPlayer.setVolume((leftVolume + rightVolume) / 2);
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mInternalPlayer != null)
            mInternalPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setOptions() {
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed);
        mSpeedPlaybackParameters = playbackParameters;
        if (mInternalPlayer != null) {
            mInternalPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    @Override
    public float getSpeed() {
        if (mSpeedPlaybackParameters != null) {
            return mSpeedPlaybackParameters.speed;
        }
        return 1f;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (mPlayerEventListener == null) return;
        if (mIsPreparing) {
            if (playbackState == Player.STATE_READY) {
                mPlayerEventListener.onPrepared();
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                mIsPreparing = false;
            }
            return;
        }
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                break;
            case Player.STATE_READY:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                break;
            case Player.STATE_ENDED:
                mPlayerEventListener.onCompletion();
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (handlePlaybackError(error)) {
            return;
        }
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onError();
        }
    }

    private boolean handlePlaybackError(ExoPlaybackException error) {
        if (mCurrentUrl == null) {
            return false;
        }
        if (error.type == ExoPlaybackException.TYPE_RENDERER && !mDecodeRetried) {
            mDecodeRetried = true;
            ExoPlayerConfig.setPreferSoftwareDecoder(true);
            retryPlayback(getCurrentPosition());
            return true;
        }
        if (error.type == ExoPlaybackException.TYPE_SOURCE && mFormatRetryStage == 0) {
            mFormatRetryStage = 1;
            ExoPlayerConfig.setFormatHint(C.TYPE_HLS);
            retryPlayback(getCurrentPosition());
            return true;
        }
        if (error.type == ExoPlaybackException.TYPE_SOURCE && mFormatRetryStage == 1) {
            mFormatRetryStage = 2;
            ExoPlayerConfig.setFormatHint(C.TYPE_OTHER);
            retryPlayback(getCurrentPosition());
            return true;
        }
        return false;
    }

    private void retryPlayback(long position) {
        if (mInternalPlayer != null) {
            mInternalPlayer.removeListener(this);
            mInternalPlayer.release();
            mInternalPlayer = null;
        }
        mIsPreparing = false;
        initPlayer();
        mMediaSource = mMediaSourceHelper.getMediaSource(
                mCurrentUrl, mCurrentHeaders, ExoPlayerConfig.isCacheEnabled());
        prepareAsync();
        if (position > 0) {
            seekTo(position);
        }
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
            if (videoSize.unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, videoSize.unappliedRotationDegrees);
            }
        }
    }
}
