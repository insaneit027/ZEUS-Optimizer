package com.zeus.optimizer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final int HOME = 0, CLEAN = 1, APPS = 2, BATTERY = 3, TOOLS = 4;
    private static final String[] NAV_ICONS = {"⚡", "🧹", "📱", "🔋", "🛠"};
    private static final String[] NAV_NAMES = {"Início", "Limpeza", "Apps", "Bateria", "Ferramentas"};

    final Handler ui = new Handler(Looper.getMainLooper());
    int gen;
    private int tab = HOME;
    private ScrollView scroll;
    private LinearLayout content;
    private final LinearLayout[] navItems = new LinearLayout[5];
    private final TextView[] navIcons = new TextView[5];
    private final TextView[] navLabels = new TextView[5];
    private Screen[] screens;
    private boolean autoScanPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Theme.BG);
        getWindow().setNavigationBarColor(Theme.SURFACE);

        screens = new Screen[]{new HomeScreen(this), new CleanScreen(this), new AppsScreen(this),
                new BatteryScreen(this), new ToolsScreen(this)};

        LinearLayout root = Theme.col(this);
        root.setBackgroundColor(Theme.BG);

        // Cabeçalho
        LinearLayout header = Theme.row(this);
        header.setPadding(dp(18), dp(16), dp(18), dp(10));
        TextView logo = Theme.icon(this, "⚡", Theme.GOLD, 40);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Theme.GOLD, Theme.GOLD2});
        lg.setShape(GradientDrawable.OVAL);
        logo.setBackground(lg);
        header.addView(logo);
        LinearLayout names = Theme.col(this);
        names.setPadding(dp(12), 0, 0, 0);
        LinearLayout nameRow = Theme.row(this);
        TextView zeus = Theme.text(this, "ZEUS", 21, Theme.GOLD, true);
        zeus.setLetterSpacing(0.08f);
        nameRow.addView(zeus);
        TextView opt = Theme.text(this, " OPTIMIZER", 21, Theme.TEXT, true);
        opt.setLetterSpacing(0.08f);
        nameRow.addView(opt);
        names.addView(nameRow);
        names.addView(Theme.small(this, "Desempenho no máximo"));
        header.addView(names, Theme.weight(1));
        header.addView(Theme.tag(this, "v2.0", Theme.CYAN));
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = Theme.col(this);
        content.setPadding(dp(16), dp(4), dp(16), dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(Theme.MATCH, 0, 1f));

        // Barra de navegação
        LinearLayout nav = Theme.row(this);
        nav.setBackgroundColor(Theme.SURFACE);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout item = Theme.col(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(6), 0, dp(6));
            TextView ic = Theme.text(this, NAV_ICONS[i], 19, Theme.TEXT, false);
            ic.setGravity(Gravity.CENTER);
            TextView lb = Theme.text(this, NAV_NAMES[i], 10.5f, Theme.MUTED, true);
            lb.setGravity(Gravity.CENTER);
            lb.setSingleLine(true);
            item.addView(ic);
            item.addView(lb);
            item.setOnClickListener(v -> go(idx));
            nav.addView(item, new LinearLayout.LayoutParams(0, Theme.WRAP, 1f));
            navItems[i] = item;
            navIcons[i] = ic;
            navLabels[i] = lb;
        }
        root.addView(nav);
        setContentView(root);

        Intent in = getIntent();
        if (in != null) {
            tab = Math.max(0, Math.min(4, in.getIntExtra("tab", HOME)));
            autoScanPending = in.getBooleanExtra("autoscan", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        if (autoScanPending) {
            autoScanPending = false;
            ((CleanScreen) screens[CLEAN]).startScan();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        gen++;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Screen s : screens) s.onActivityResult(requestCode, resultCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    int tab() {
        return tab;
    }

    void go(int t) {
        if (t == tab) return;
        tab = t;
        scroll.scrollTo(0, 0);
        render();
    }

    void render() {
        gen++;
        for (int i = 0; i < 5; i++) {
            boolean on = i == tab;
            navLabels[i].setTextColor(on ? Theme.GOLD : Theme.DIM);
            navIcons[i].setAlpha(on ? 1f : 0.55f);
            navItems[i].setBackground(on ? Theme.rounded(this, Theme.alpha(Theme.GOLD, 0x1C), 14) : null);
        }
        content.removeAllViews();
        screens[tab].build(content);
        content.setAlpha(0f);
        content.setTranslationY(dp(10));
        content.animate().alpha(1f).translationY(0).setDuration(240).start();
    }

    /** Reconstrói a aba atual sem animação nem voltar ao topo. */
    void refresh() {
        gen++;
        content.removeAllViews();
        screens[tab].build(content);
    }

    void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                open(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    void requestUsageAccess() {
        toast("Procure \"ZEUS Optimizer\" na lista e ative");
        open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    boolean open(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            toast("Não foi possível abrir essa tela neste aparelho");
            return false;
        }
    }

    void openSetting(String action) {
        open(new Intent(action));
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    int dp(float v) {
        return Theme.dp(this, v);
    }
}
