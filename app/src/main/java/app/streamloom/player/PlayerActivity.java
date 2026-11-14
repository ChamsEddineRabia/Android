package app.streamloom.player;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        String url = getIntent().getStringExtra("stream_url");
        String title = getIntent().getStringExtra("channel_name");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView view = new PlayerView(this);
        view.setUseController(true);
        view.setControllerAutoShow(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        TextView error = new TextView(this);
        error.setTextColor(Color.WHITE);
        error.setBackgroundColor(0xCC151515);
        error.setPadding(24, 18, 24, 18);
        error.setVisibility(View.GONE);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, -2);
        ep.gravity = android.view.Gravity.BOTTOM;
        root.addView(error, ep);
        setContentView(root);
        if (url == null || url.isEmpty()) { error.setText("No stream address was returned."); error.setVisibility(View.VISIBLE); return; }
        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                error.setText("Could not play this stream. The provider may require a different format or active access.");
                error.setVisibility(View.VISIBLE);
            }
        });
        view.setContentDescription(title == null ? "Video player" : "Playing " + title);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }

    @Override protected void onStop() { super.onStop(); if (player != null) player.pause(); }
    @Override protected void onDestroy() { if (player != null) { player.release(); player = null; } super.onDestroy(); }
}
