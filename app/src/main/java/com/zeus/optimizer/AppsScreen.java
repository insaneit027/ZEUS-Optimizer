package com.zeus.optimizer;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Aba Apps: o que pode ser removido, tempo de tela e desinstalação em lote. */
final class AppsScreen extends Screen {

    private static final int REQ_UNINSTALL = 4210;
    private static final String[] FILTERS = {"Recomendado", "Mais usados", "Maiores", "A–Z"};

    private AppAnalyzer.Result result;
    private boolean loading;
    private int filter;
    private final Set<String> picked = new LinkedHashSet<>();
    private final Deque<String> queue = new ArrayDeque<>();
    private Button batchButton;

    AppsScreen(MainActivity a) {
        super(a);
    }

    @Override
    void build(LinearLayout root) {
        batchButton = null;
        boolean usage = AppAnalyzer.hasUsageAccess(a);
        if (!usage) {
            LinearLayout c = Theme.card(a);
            LinearLayout r = Theme.row(a);
            r.addView(Theme.icon(a, "📊", Theme.BLUE, 46));
            LinearLayout tx = Theme.col(a);
            tx.setPadding(dp(12), 0, 0, 0);
            tx.addView(Theme.title(a, "Ative a análise de uso"));
            tx.addView(Theme.small(a, "Assim o ZEUS descobre há quantos dias cada app não é aberto, quanto espaço ocupa e seu tempo de tela."));
            r.addView(tx, Theme.weight(1));
            c.addView(r);
            addButton(c, Theme.primary(a, "Ativar análise de uso"), v -> {
                result = null;
                a.requestUsageAccess();
            });
            root.addView(c);
        }

        if (result == null) {
            LinearLayout c = Theme.card(a);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            ProgressBar spin = new ProgressBar(a);
            spin.setIndeterminateTintList(ColorStateList.valueOf(Theme.GOLD));
            c.addView(spin, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView t = Theme.small(a, "Analisando apps instalados...");
            t.setPadding(0, dp(10), 0, 0);
            c.addView(t);
            root.addView(c);
            load();
            return;
        }

        // ---------------- Resumo
        LinearLayout sum = Theme.card(a);
        LinearLayout nums = Theme.row(a);
        nums.addView(number(String.valueOf(result.apps.size()), "apps", Theme.TEXT), Theme.weight(1));
        nums.addView(number(String.valueOf(result.removableCount), "esquecidos", result.removableCount > 0 ? Theme.RED : Theme.GREEN), Theme.weight(1));
        nums.addView(number(usage ? Theme.size(result.totalBytes) : "—", "ocupados", Theme.CYAN), Theme.weight(1));
        sum.addView(nums);
        if (usage && result.removableCount > 0) {
            sum.addView(Theme.space(a, 10));
            sum.addView(Theme.body(a, "Remover os apps esquecidos liberaria cerca de " + Theme.size(result.removableBytes) + "."));
            addButton(sum, Theme.secondary(a, "Selecionar todos os esquecidos"), v -> {
                for (AppAnalyzer.App ap : result.apps) if (ap.level == AppAnalyzer.REMOVE) picked.add(ap.pkg);
                a.refresh();
            });
            if (Shell.shizukuGranted() && android.os.Build.VERSION.SDK_INT >= 31) {
                addButton(sum, Theme.secondary(a, "💤  Hibernar os esquecidos (sem desinstalar)"), v -> hibernateForgotten());
            }
        }
        if (usage && result.totalScreenTime > 0) {
            sum.addView(Theme.space(a, 10));
            sum.addView(Theme.small(a, "Tempo de tela nos últimos 7 dias: " + AppAnalyzer.duration(result.totalScreenTime)));
        }
        root.addView(sum);

        // ---------------- Filtros
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Theme.row(a);
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            TextView chip = Theme.text(a, FILTERS[i], 13, i == filter ? 0xFF1A1300 : Theme.TEXT, true);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(i == filter
                    ? Theme.rounded(a, Theme.GOLD, 20)
                    : Theme.outlined(a, Theme.SURFACE, Theme.STROKE, 20));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.WRAP, Theme.WRAP);
            lp.setMargins(0, 0, dp(8), 0);
            chip.setOnClickListener(v -> {
                filter = idx;
                a.refresh();
            });
            chips.addView(chip, lp);
        }
        hs.addView(chips);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP);
        hlp.setMargins(0, dp(2), 0, dp(12));
        root.addView(hs, hlp);

        // ---------------- Desinstalar em lote
        batchButton = Theme.danger(a, "");
        batchButton.setOnClickListener(v -> startBatch());
        root.addView(batchButton, new LinearLayout.LayoutParams(Theme.MATCH, Theme.WRAP));
        root.addView(Theme.space(a, 12));
        updateBatch();

        // ---------------- Lista
        List<AppAnalyzer.App> list = new ArrayList<>(result.apps);
        Collections.sort(list, comparator());
        for (AppAnalyzer.App ap : list) root.addView(appRow(ap, usage));
        if (list.isEmpty()) {
            LinearLayout e = Theme.card(a);
            e.addView(Theme.title(a, "Nenhum app instalado por você"));
            e.addView(Theme.small(a, "Só os apps do sistema estão no celular."));
            root.addView(e);
        }

        LinearLayout sys = Theme.card(a);
        LinearLayout r = Theme.row(a);
        r.addView(Theme.icon(a, "🛡", Theme.GREEN, 42));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, 0, 0);
        tx.addView(Theme.title(a, result.systemCount + " apps do sistema protegidos"));
        tx.addView(Theme.small(a, "Ficam ocultos porque o Android precisa deles (telefone, câmera, Google Play Services...). Não tente removê-los."));
        r.addView(tx, Theme.weight(1));
        sys.addView(r);
        root.addView(sys);
    }

    private Comparator<AppAnalyzer.App> comparator() {
        switch (filter) {
            case 1:
                return (x, y) -> Long.compare(y.screenTime7d, x.screenTime7d);
            case 2:
                return (x, y) -> Long.compare(y.size, x.size);
            case 3:
                return (x, y) -> x.label.toLowerCase(Locale.getDefault()).compareTo(y.label.toLowerCase(Locale.getDefault()));
            default:
                return (x, y) -> x.level != y.level ? Integer.compare(x.level, y.level) : Long.compare(y.size, x.size);
        }
    }

    private LinearLayout number(String value, String label, int color) {
        LinearLayout c = Theme.col(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView v = Theme.text(a, value, 20, color, true);
        v.setGravity(Gravity.CENTER);
        c.addView(v);
        TextView l = Theme.small(a, label);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        return c;
    }

    private void load() {
        if (loading) return;
        loading = true;
        final int myGen = a.gen;
        new Thread(() -> {
            AppAnalyzer.Result r;
            try {
                r = AppAnalyzer.load(a.getApplicationContext());
            } catch (Throwable t) {
                r = new AppAnalyzer.Result();
            }
            final AppAnalyzer.Result res = r;
            a.ui.post(() -> {
                loading = false;
                result = res;
                if (a.tab() == MainActivity.APPS && a.gen == myGen) a.refresh();
            });
        }).start();
    }

    private View appRow(AppAnalyzer.App ap, boolean usage) {
        LinearLayout c = Theme.card(a);
        LinearLayout top = Theme.row(a);
        CheckBox cb = new CheckBox(a);
        cb.setButtonTintList(ColorStateList.valueOf(Theme.GOLD));
        cb.setChecked(picked.contains(ap.pkg));
        cb.setOnCheckedChangeListener((b, on) -> {
            if (on) picked.add(ap.pkg);
            else picked.remove(ap.pkg);
            updateBatch();
        });
        top.addView(cb);
        ImageView iv = new ImageView(a);
        if (ap.icon != null) iv.setImageDrawable(ap.icon);
        top.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(12), 0, dp(6), 0);
        TextView name = Theme.text(a, ap.label, 15, Theme.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(name);
        StringBuilder info = new StringBuilder();
        if (ap.size > 0) info.append(Theme.size(ap.size));
        if (ap.cache > 1024 * 1024) info.append(" • cache ").append(Theme.size(ap.cache));
        if (usage && ap.screenTime7d > 60000) info.append(" • ").append(AppAnalyzer.duration(ap.screenTime7d)).append("/sem");
        tx.addView(Theme.small(a, info.length() > 0 ? info.toString() : ap.pkg));
        int color = ap.level == AppAnalyzer.REMOVE ? Theme.RED : ap.level == AppAnalyzer.REVIEW ? Theme.GOLD : Theme.GREEN;
        tx.addView(Theme.text(a, ap.reason, 12.5f, color, true));
        top.addView(tx, Theme.weight(1));
        c.addView(top);

        LinearLayout btns = Theme.row(a);
        btns.setPadding(0, dp(10), 0, 0);
        Button un = ap.level == AppAnalyzer.REMOVE ? Theme.danger(a, "Desinstalar") : Theme.secondary(a, "Desinstalar");
        un.setTextSize(13);
        un.setPadding(dp(10), dp(10), dp(10), dp(10));
        un.setOnClickListener(v -> uninstall(ap.pkg));
        Button cache = Theme.secondary(a, "Limpar cache ›");
        cache.setTextSize(13);
        cache.setPadding(dp(10), dp(10), dp(10), dp(10));
        cache.setOnClickListener(v -> {
            a.toast("Toque em Armazenamento › Limpar cache");
            a.open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ap.pkg)));
        });
        LinearLayout.LayoutParams l1 = Theme.weight(1);
        l1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams l2 = Theme.weight(1);
        l2.setMargins(dp(6), 0, 0, 0);
        btns.addView(un, l1);
        btns.addView(cache, l2);
        c.addView(btns);
        if (Shell.shizukuGranted()) {
            Button fz = Theme.secondary(a, ap.frozen ? "🔥  Descongelar" : "🧊  Congelar (parar sem desinstalar)");
            fz.setTextSize(13);
            fz.setPadding(dp(10), dp(10), dp(10), dp(10));
            fz.setOnClickListener(v -> freeze(ap, !ap.frozen));
            c.addView(fz, Theme.fullWidth(a, 8));
        }
        if (ap.frozen) {
            c.addView(Theme.space(a, 6));
            c.addView(Theme.text(a, "🧊 Congelado: não roda nem aparece ativo até você descongelar", 12.5f, Theme.CYAN, true));
        }
        return c;
    }

    private void freeze(AppAnalyzer.App ap, boolean on) {
        final android.content.Context ctx = a.getApplicationContext();
        new Thread(() -> {
            String cmd = on
                    ? "am force-stop " + ap.pkg + "; pm suspend --user 0 " + ap.pkg
                    : "pm unsuspend --user 0 " + ap.pkg + "; pm enable " + ap.pkg;
            Shell.Result r = Shell.run(cmd);
            a.ui.post(() -> {
                a.toast(on ? "🧊 " + ap.label + " congelado" : "🔥 " + ap.label + " descongelado");
                result = null;
                if (a.tab() == MainActivity.APPS) a.refresh();
            });
        }).start();
    }

    private void hibernateForgotten() {
        final android.content.Context ctx = a.getApplicationContext();
        final java.util.List<String> pkgs = new java.util.ArrayList<>();
        for (AppAnalyzer.App ap : result.apps) if (ap.level == AppAnalyzer.REMOVE) pkgs.add(ap.pkg);
        a.toast("💤 Hibernando " + pkgs.size() + " apps esquecidos...");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (String p : pkgs) sb.append("cmd app_hibernation set-state ").append(p).append(" true >/dev/null 2>&1; ");
            Shell.run(sb.toString());
            a.ui.post(() -> a.toast("💤 " + pkgs.size() + " apps hibernados. Eles acordam sozinhos quando você abrir."));
        }).start();
    }

    private void updateBatch() {
        if (batchButton == null) return;
        int n = picked.size();
        batchButton.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        batchButton.setText("🗑  Desinstalar " + n + (n == 1 ? " app selecionado" : " apps selecionados"));
    }

    private void startBatch() {
        queue.clear();
        queue.addAll(picked);
        a.toast("Confirme cada desinstalação na tela do Android");
        next();
    }

    private void next() {
        String pkg = queue.poll();
        if (pkg == null) {
            result = null;
            a.refresh();
            return;
        }
        picked.remove(pkg);
        uninstall(pkg);
    }

    @SuppressWarnings("deprecation")
    private void uninstall(String pkg) {
        Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkg));
        i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            a.startActivityForResult(i, REQ_UNINSTALL);
        } catch (Exception e) {
            a.open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        }
    }

    @Override
    void onActivityResult(int requestCode, int resultCode) {
        if (requestCode != REQ_UNINSTALL) return;
        if (!queue.isEmpty()) {
            a.ui.postDelayed(this::next, 400);
        } else {
            result = null;
        }
    }
}
