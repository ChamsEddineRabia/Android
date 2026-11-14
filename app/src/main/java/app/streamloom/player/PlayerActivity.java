package app.streamloom.player;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TrackSelectionDialogBuilder;

public final class PlayerActivity extends Activity {
    private static final int INK = 0xFF0B0D12;
    private static final int PANEL = 0xE61A1E27;
    private static final int WHITE = 0xFFF4F2EE;
    private static final int MUTED = 0xFFB1B3BB;
    private static final int SIGNAL = 0xFFFF765F;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private FrameLayout hud;
    private TextView playPause;
    private TextView liveLabel;
    private TextView errorLabel;
    private boolean controlsVisible = true;
    private boolean cropping = false;
    private final Runnable hideHud = () -> setHudVisible(false);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 28) window.setNavigationBarDividerColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        String url = getIntent().getStringExtra("stream_url");
        String title = getIntent().getStringExtra("channel_name");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        PlayerView video = new PlayerView(this);
        video.setUseController(false);
        video.setKeepContentOnPlayerReset(true);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        hud = new FrameLayout(this);
        hud.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB8000000, 0x00000000, 0x00000000, 0xD9000000}));
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));
        buildTop(hud, title == null || title.trim().isEmpty() ? "Live channel" : title);
        buildCenter(hud);
        buildBottom(hud, title == null || title.trim().isEmpty() ? "Live channel" : title);
        root.setOnClickListener(v -> toggleHud());
        hud.setOnClickListener(v -> toggleHud());
        setContentView(root);
        enterImmersive();

        if (url == null || url.isEmpty()) { showError("No stream address was returned. Check this channel with your provider."); return; }
        player = new ExoPlayer.Builder(this).build();
        video.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (playPause != null) playPause.setText(isPlaying ? "Ⅱ" : "▶");
                if (isPlaying) scheduleHide();
            }
            @Override public void onPlayerError(PlaybackException e) {
                showError("This stream could not start. It may be offline or unavailable for this device.");
                setHudVisible(true);
            }
        });
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
        scheduleHide();
    }

    private void buildTop(FrameLayout root, String title) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(10), dp(20), dp(10));
        TextView back = circle("‹", 46);
        bar.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        back.setOnClickListener(v -> finish());
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView kicker = label("NOW PLAYING", 9, 0xFFD2D3D8);
        kicker.setLetterSpacing(.14f);
        TextView name = label(title, 16, WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(1);
        copy.addView(kicker);
        copy.addView(name);
        bar.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = circle("···", 46);
        bar.addView(more, new LinearLayout.LayoutParams(dp(46), dp(46)));
        more.setOnClickListener(v -> showTracks(C.TRACK_TYPE_AUDIO));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.topMargin = dp(18);
        root.addView(bar, lp);
    }

    private void buildCenter(FrameLayout root) {
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        playPause = circle("Ⅱ", 76);
        playPause.setTextSize(25);
        playPause.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause(); else { player.play(); errorLabel.setVisibility(View.GONE); }
            scheduleHide();
        });
        controls.addView(playPause, new LinearLayout.LayoutParams(dp(76), dp(76)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(controls, lp);
    }

    private void buildBottom(FrameLayout root, String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(15), dp(18), dp(15));
        panel.setBackground(rounded(PANEL, 22));
        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        liveLabel = label("●  LIVE", 10, SIGNAL);
        liveLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        liveLabel.setLetterSpacing(.08f);
        info.addView(liveLabel);
        TextView now = label("   Watching now", 11, MUTED);
        info.addView(now);
        panel.addView(info);
        TextView heading = label(title, 19, WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(0, dp(5), 0, dp(13));
        panel.addView(heading);
        View rule = new View(this);
        rule.setBackgroundColor(0x30FFFFFF);
        panel.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        TextView playback = action("Captions", "CC");
        actions.addView(playback, new LinearLayout.LayoutParams(0, dp(42), 1));
        playback.setOnClickListener(v -> showTracks(C.TRACK_TYPE_TEXT));
        TextView fit = action("Screen fit", "□");
        actions.addView(fit, new LinearLayout.LayoutParams(0, dp(42), 1));
        fit.setOnClickListener(v -> toggleVideoSize());
        TextView exit = action("Close player", "×");
        actions.addView(exit, new LinearLayout.LayoutParams(0, dp(42), 1));
        exit.setOnClickListener(v -> finish());
        panel.addView(actions);
        errorLabel = label("", 12, 0xFFFFB5A2);
        errorLabel.setVisibility(View.GONE);
        errorLabel.setPadding(0, dp(10), 0, 0);
        panel.addView(errorLabel);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        lp.bottomMargin = dp(20);
        root.addView(panel, lp);
    }

    private TextView circle(String text, int size) {
        TextView view = label(text, 19, WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(0x8C242934, size));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView action(String text, String icon) {
        TextView view = label(icon + "   " + text, 11, WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(9), 0, dp(5), 0);
        view.setSingleLine(true);
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void toggleVideoSize() {
        if (player == null) return;
        cropping = !cropping;
        player.setVideoScalingMode(cropping ? C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING : C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        scheduleHide();
    }

    private void showTracks(int type) {
        if (player == null) return;
        try { new TrackSelectionDialogBuilder(this, type == C.TRACK_TYPE_TEXT ? "Captions" : "Audio", player, type).build().show(); }
        catch (Exception ignored) { showError(type == C.TRACK_TYPE_TEXT ? "No captions were provided by this stream." : "No audio options were provided by this stream."); }
    }

    private void showError(String message) {
        if (errorLabel != null) { errorLabel.setText(message); errorLabel.setVisibility(View.VISIBLE); }
    }

    private void toggleHud() { setHudVisible(!controlsVisible); }

    private void setHudVisible(boolean visible) {
        controlsVisible = visible;
        if (hud != null) hud.animate().alpha(visible ? 1f : 0f).setDuration(180).start();
        if (visible) scheduleHide(); else handler.removeCallbacks(hideHud);
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideHud);
        handler.postDelayed(hideHud, 4500);
    }

    private void enterImmersive() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterImmersive(); }
    @Override protected void onStop() { super.onStop(); handler.removeCallbacks(hideHud); if (player != null) player.pause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (player != null) { player.release(); player = null; } super.onDestroy(); }
}
