package tv.danmaku.ijk.media.flutter;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Surface;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.view.TextureRegistry;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.widget.FileMediaDataSource;

public class FlutterVideoPlayer {

    private Context context;

    private IjkMediaPlayer player;

    private Surface surface;

    private final TextureRegistry.SurfaceTextureEntry textureEntry;

    private QueuingEventSink eventSink = new QueuingEventSink();

    private final EventChannel eventChannel;

    FlutterVideoPlayer(
            Context context,
            EventChannel eventChannel,
            TextureRegistry.SurfaceTextureEntry textureEntry,
            MethodChannel.Result result) {
        this.context = context;
        this.eventChannel = eventChannel;
        this.textureEntry = textureEntry;

        player = new IjkMediaPlayer();
        setupVideoPlayer(eventChannel, textureEntry, result);
    }

    private void setupVideoPlayer(
            EventChannel eventChannel,
            TextureRegistry.SurfaceTextureEntry textureEntry,
            MethodChannel.Result result) {

        eventChannel.setStreamHandler(
                new EventChannel.StreamHandler() {
                    @Override
                    public void onListen(Object o, EventChannel.EventSink sink) {
                        eventSink.setDelegate(sink);
                    }

                    @Override
                    public void onCancel(Object o) {
                        eventSink.setDelegate(null);
                    }
                });

        surface = new Surface(textureEntry.surfaceTexture());
        player.setSurface(surface);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);

        player.setOnPreparedListener(mp -> sendPrepared());
        player.setOnCompletionListener(mp -> {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "completed");
            eventSink.success(event);
        });
        player.setOnErrorListener((mp, what, extra) -> {
            if (eventSink != null) {
                eventSink.error("VideoError", "Video player had error " + what, null);
            }
            return false;
        });
        player.setOnBufferingUpdateListener((mp, percent) -> sendBufferingUpdate(percent));

        Map<String, Object> reply = new HashMap<>();
        reply.put("textureId", textureEntry.id());
        result.success(reply);
    }

    private void sendBufferingUpdate(int percent) {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "bufferingUpdate");
        List<? extends Number> range = Arrays.asList(0, percent);
        // iOS supports a list of buffered ranges, so here is a list with a single range.
        event.put("values", Collections.singletonList(range));
        eventSink.success(event);
    }

    void setDataSource(String dataSource) {
        try {
            Uri uri = Uri.parse(dataSource);
            String scheme = uri.getScheme();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    (TextUtils.isEmpty(scheme) || scheme.equalsIgnoreCase("file"))) {
                player.setDataSource(new FileMediaDataSource(new File(uri.toString())));
            } else {
                player.setDataSource(context, uri, null);
            }
            player.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void play() {
        player.start();
    }

    void pause() {
        player.pause();
    }

    void setLooping(boolean value) {
        player.setLooping(value);
    }

    void setVolume(double value) {
        float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
        player.setVolume(bracketedValue, bracketedValue);
    }

    void seekTo(int location) {
        player.seekTo(location);
    }

    long getPosition() {
        return player.getCurrentPosition();
    }

    private void sendPrepared() {
        Map<String, Object> event = new HashMap<>();
        event.put("event", "prepared");
        event.put("duration", player.getDuration());
        event.put("width", player.getVideoWidth());
        event.put("height", player.getVideoHeight());
        eventSink.success(event);
    }

    void dispose() {
        if (player != null && player.isPlaying())
            player.stop();
        textureEntry.release();
        eventChannel.setStreamHandler(null);
        if (surface != null) {
            surface.release();
        }
        if (player != null) {
            player.release();
        }
    }
}
