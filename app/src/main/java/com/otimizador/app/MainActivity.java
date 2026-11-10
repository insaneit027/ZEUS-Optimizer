package com.otimizador.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int TAB_PAINEL = 0, TAB_LIMPEZA = 1, TAB_APPS = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private ScrollView scroll;
    private final Button[] tabButtons = new Button[3];
    private int tab = TAB_PAINEL;
    private int gen = 0;

    // Estado da limpeza
    private Scanner.Result scan;
    private boolean scanning;
    private boolean deleting;
    private TextView scanStatus;
    private Button deleteButton;
    private final Set<File> selected = new HashSet<>();

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.CARD);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        TextView title = Ui.text(this, "⚡ ZEUS Optimizer", 24, Ui.TEXT, true);
        title.setPadding(dp(20), dp(18), dp(20), dp(2));
        root.addView(title);
        TextView sub = Ui.text(this, "Desempenho, limpeza e apps", 13, Ui.MUTED, false);
        sub.setPadding(dp(20), 0, dp(20), dp(10));
        root.addView(sub);

        scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Ui.CARD);
        nav.setPadding(dp(8), dp(4), dp(8), dp(4));
        String[] names = {"📊  Painel", "🧹  Limpeza", "📱  Apps"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setBackgroundColor(0x00000000);
            b.setStateListAnimator(null);
            b.setOnClickListener(v -> {
                tab = idx;
                scroll.scrollTo(0, 0);
                render();
            });
            nav.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tabButtons[i] = b;
        }
        root.addView(nav);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    private void render() {
        gen++;
        for (int i = 0; i < 3; i++) {
            tabButtons[i].setTextColor(i == tab ? Ui.ACCENT : Ui.MUTED);
            tabButtons[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        content.removeAllViews();
        deleteButton = null;
        scanStatus = null;
        if (tab == TAB_PAINEL) showDashboard();
        else if (tab == TAB_LIMPEZA) showCleaner();
        else showApps();
    }

    // ------------------------------------------------------------------ PAINEL

    private void showDashboard() {
        StatFs st = new StatFs(Environment.getDataDirectory().getPath());
        long total = st.getTotalBytes(), free = st.getAvailableBytes(), used = total - free;
        int storagePct = pct(used, total);

        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        int ramPct = pct(mi.totalMem - mi.availMem, mi.totalMem);

        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, health = 0;
        float temp = 0;
        boolean charging = false;
        if (bat != null) {
            int l = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int s = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (l >= 0 && s > 0) level = l * 100 / s;
            temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            health = bat.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            int status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        // Nota de saúde
        int score = 100;
        List<String> issues = new ArrayList<>();
        if (storagePct >= 90) {
            score -= 35;
            issues.add("Armazenamento quase cheio — isso deixa o celular lento. Use a aba Limpeza.");
        } else if (storagePct >= 75) {
            score -= 15;
            issues.add("Armazenamento acima de 75%. Uma limpeza ajuda a manter o desempenho.");
        }
        if (ramPct >= 85 || mi.lowMemory) {
            score -= 20;
            issues.add("Memória RAM muito ocupada. Feche apps que não está usando.");
        } else if (ramPct >= 70) {
            score -= 8;
            issues.add("Memória RAM bem usada. Normal no Android, mas evite muitos apps abertos.");
        }
        if (temp >= 42) {
            score -= 20;
            issues.add("Bateria quente (" + fmt1(temp) + "°C). Evite jogos e carregar enquanto esquenta.");
        } else if (temp >= 38) {
            score -= 8;
            issues.add("Bateria morna (" + fmt1(temp) + "°C).");
        }
        if (health == BatteryManager.BATTERY_HEALTH_DEAD || health == BatteryManager.BATTERY_HEALTH_OVERHEAT) {
            score -= 15;
            issues.add("O sistema indica problema na bateria: " + healthText(health) + ".");
        }
        score = Math.max(score, 5);
        int scoreColor = score >= 80 ? Ui.ACCENT : score >= 55 ? Ui.WARN : Ui.DANGER;

        LinearLayout sc = card();
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView big = Ui.text(this, String.valueOf(score), 46, scoreColor, true);
        r.addView(big);
        LinearLayout col = vertical();
        col.setPadding(dp(16), 0, 0, 0);
        col.addView(Ui.text(this, score >= 80 ? "Celular em ótimo estado" : score >= 55 ? "Dá pra melhorar" : "Precisa de atenção", 18, Ui.TEXT, true));
        col.addView(Ui.text(this, "Nota de saúde de 0 a 100", 13, Ui.MUTED, false));
        r.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sc.addView(r);
        if (issues.isEmpty()) {
            sc.addView(space(8));
            sc.addView(p("✓ Nada de errado por aqui."));
        } else {
            for (String s : issues) {
                sc.addView(space(6));
                sc.addView(p("• " + s));
            }
        }
        content.addView(sc);

        // Armazenamento
        LinearLayout c1 = card();
        c1.addView(h("💾 Armazenamento interno"));
        c1.addView(Ui.bar(this, storagePct, Ui.levelColor(storagePct)));
        c1.addView(p(Ui.size(used) + " usados de " + Ui.size(total) + " (" + storagePct + "%)"));
        c1.addView(Ui.text(this, Ui.size(free) + " livres", 14, Ui.levelColor(storagePct), true));
        c1.addView(btn("Limpar armazenamento", Ui.ACCENT_DARK, v -> {
            tab = TAB_LIMPEZA;
            render();
        }));
        content.addView(c1);

        // RAM
        LinearLayout c2 = card();
        c2.addView(h("🧠 Memória RAM"));
        c2.addView(Ui.bar(this, ramPct, Ui.levelColor(ramPct)));
        c2.addView(p(Ui.size(mi.totalMem - mi.availMem) + " em uso de " + Ui.size(mi.totalMem) + " (" + ramPct + "%)"));
        Button kill = btn("Encerrar apps em segundo plano", Ui.CARD2, null);
        kill.setOnClickListener(v -> killBackground(kill));
        c2.addView(kill);
        c2.addView(space(6));
        c2.addView(small("O Android já administra a RAM sozinho. Isso só encerra apps parados em segundo plano — não existe \"turbo\" mágico."));
        content.addView(c2);

        // Bateria
        LinearLayout c3 = card();
        c3.addView(h("🔋 Bateria"));
        if (level >= 0) {
            c3.addView(Ui.bar(this, level, level <= 20 ? Ui.DANGER : Ui.ACCENT));
            c3.addView(p(level + "%" + (charging ? " • carregando" : "") + " • " + fmt1(temp) + "°C • " + healthText(health)));
        } else {
            c3.addView(p("Informações indisponíveis."));
        }
        content.addView(c3);

        // Dicas
        LinearLayout c4 = card();
        c4.addView(h("💡 Dicas que realmente funcionam"));
        String[] tips = {
                "Reinicie o celular pelo menos uma vez por semana.",
                "Mantenha pelo menos 15% do armazenamento livre.",
                "Reduza as animações: Opções do desenvolvedor › Escala de animação › 0,5x.",
                "Remova widgets e papéis de parede animados que você não usa.",
                "Mantenha o sistema e os apps atualizados.",
                "Desconfie de apps \"booster\" que ficam rodando o tempo todo: eles gastam bateria e RAM."
        };
        for (String t : tips) {
            c4.addView(space(6));
            c4.addView(p("• " + t));
        }
        c4.addView(btn("Abrir configurações de armazenamento", Ui.CARD2,
                v -> open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))));
        content.addView(c4);
    }

    private void killBackground(Button b) {
        b.setEnabled(false);
        b.setText("Encerrando...");
        new Thread(() -> {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            long before = availMem(am);
            for (ApplicationInfo a : getPackageManager().getInstalledApplications(0)) {
                if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || a.packageName.equals(getPackageName())) continue;
                try {
                    am.killBackgroundProcesses(a.packageName);
                } catch (Exception ignored) {
                }
            }
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, availMem(am) - before);
            ui.post(() -> {
                toast(freed > 0 ? "Liberado " + Ui.size(freed) + " de RAM" : "Apps em segundo plano encerrados");
                if (tab == TAB_PAINEL) render();
            });
        }).start();
    }

    private static long availMem(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private static String healthText(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "saúde boa";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "superaquecida";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "bateria gasta";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "sobretensão";
            case BatteryManager.BATTERY_HEALTH_COLD: return "fria demais";
            default: return "saúde desconhecida";
        }
    }

    // ------------------------------------------------------------------ LIMPEZA

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
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

    private void showCleaner() {
        if (!hasStorageAccess()) {
            LinearLayout c = card();
            c.addView(h("🔒 Permissão necessária"));
            c.addView(p("Para encontrar arquivos inúteis, o ZEUS Optimizer precisa de acesso aos arquivos do celular. Nada é apagado sem a sua confirmação."));
            c.addView(btn("Conceder acesso", Ui.ACCENT_DARK, v -> requestStorageAccess()));
            content.addView(c);
            return;
        }

        if (scanning || deleting) {
            LinearLayout c = card();
            c.addView(h(deleting ? "Apagando..." : "Analisando o armazenamento..."));
            ProgressBar spin = new ProgressBar(this);
            spin.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMargins(0, dp(12), 0, dp(12));
            c.addView(spin, lp);
            scanStatus = p(deleting ? "Aguarde" : "Iniciando...");
            c.addView(scanStatus);
            content.addView(c);
            return;
        }

        if (scan == null) {
            LinearLayout c = card();
            c.addView(h("🧹 Limpeza inteligente"));
            c.addView(p("Procura instaladores APK, miniaturas, temporários, lixeira, pastas vazias, arquivos duplicados, arquivos grandes, downloads antigos e mídia repetida do WhatsApp."));
            c.addView(space(10));
            c.addView(legend("SEGURO", Ui.ACCENT, "pode apagar sem medo, já vem marcado"));
            c.addView(legend("REVISAR", Ui.WARN, "confira antes — pode ser algo seu"));
            c.addView(space(6));
            c.addView(small("Pastas do sistema e dados de apps (Android/data e Android/obb) nunca são tocadas."));
            c.addView(btn("Analisar armazenamento", Ui.ACCENT_DARK, v -> startScan()));
            content.addView(c);
            return;
        }

        LinearLayout sum = card();
        sum.addView(h("Resultado da análise"));
        sum.addView(space(4));
        sum.addView(Ui.text(this, Ui.size(scan.safeBytes()), 30, Ui.ACCENT, true));
        sum.addView(p("podem ser apagados com segurança"));
        sum.addView(space(6));
        sum.addView(small(Ui.size(scan.reviewBytes()) + " em arquivos para revisar • " + scan.scanned + " arquivos analisados"));
        sum.addView(btn("Analisar de novo", Ui.CARD2, v -> startScan()));
        content.addView(sum);

        if (scan.isEmpty()) {
            LinearLayout c = card();
            c.addView(h("🎉 Nada para limpar"));
            c.addView(p("Seu armazenamento está organizado."));
            content.addView(c);
            return;
        }
        for (Scanner.Category cat : scan.categories) {
            if (!cat.files.isEmpty()) content.addView(categoryCard(cat));
        }
        deleteButton = btn("", Ui.DANGER_DARK, v -> confirmDelete());
        content.addView(deleteButton);
        updateDeleteButton();
    }

    private void startScan() {
        scanning = true;
        render();
        final File root = Environment.getExternalStorageDirectory();
        new Thread(() -> {
            Scanner.Result r;
            try {
                r = new Scanner(root, (files, path) -> ui.post(() -> {
                    if (scanStatus != null) scanStatus.setText(files + " arquivos • " + path);
                })).scan();
            } catch (Throwable t) {
                r = null;
            }
            final Scanner.Result res = r;
            ui.post(() -> {
                scanning = false;
                if (res == null) {
                    toast("Não foi possível analisar o armazenamento");
                } else {
                    scan = res;
                    selected.clear();
                    for (Scanner.Category c : res.categories) if (c.safe) selected.addAll(c.files);
                }
                if (tab == TAB_LIMPEZA) render();
            });
        }).start();
    }

    private View categoryCard(Scanner.Category cat) {
        LinearLayout c = card();

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox all = new CheckBox(this);
        all.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
        all.setChecked(selected.containsAll(cat.files));
        head.addView(all);
        LinearLayout tx = vertical();
        tx.addView(Ui.text(this, cat.title, 16, Ui.TEXT, true));
        tx.addView(Ui.text(this, Ui.size(cat.bytes) + " • " + cat.files.size()
                + (cat.files.size() == 1 ? " item" : " itens"), 13, Ui.MUTED, false));
        head.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(tag(cat.safe ? "SEGURO" : "REVISAR", cat.safe ? Ui.ACCENT : Ui.WARN));
        c.addView(head);

        c.addView(space(6));
        c.addView(small(cat.desc));

        final LinearLayout list = vertical();
        final List<CheckBox> boxes = new ArrayList<>();
        final TextView toggle = Ui.text(this, "Ver arquivos ▾", 14, Ui.BLUE, true);
        toggle.setPadding(0, dp(10), 0, dp(4));
        toggle.setOnClickListener(v -> {
            if (list.getChildCount() > 0) {
                list.removeAllViews();
                boxes.clear();
                toggle.setText("Ver arquivos ▾");
                return;
            }
            toggle.setText("Ocultar arquivos ▴");
            int max = Math.min(cat.files.size(), 300);
            for (int i = 0; i < max; i++) {
                final File f = cat.files.get(i);
                CheckBox cb = new CheckBox(this);
                cb.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
                cb.setText(f.getName() + "\n" + Ui.size(scan.sizeOf(f)) + " • " + shortPath(f.getParentFile()));
                cb.setTextColor(Ui.TEXT);
                cb.setTextSize(13);
                cb.setPadding(dp(4), dp(6), 0, dp(6));
                cb.setChecked(selected.contains(f));
                cb.setOnCheckedChangeListener((b, on) -> {
                    if (on) selected.add(f);
                    else selected.remove(f);
                    updateDeleteButton();
                });
                list.addView(cb);
                boxes.add(cb);
            }
            if (cat.files.size() > max) {
                list.addView(small("+ " + (cat.files.size() - max) + " arquivos (marcados junto com a categoria)"));
            }
        });
        all.setOnCheckedChangeListener((b, on) -> {
            if (on) selected.addAll(cat.files);
            else for (File f : cat.files) selected.remove(f);
            for (CheckBox cb : boxes) cb.setChecked(on);
            updateDeleteButton();
        });
        c.addView(toggle);
        c.addView(list);
        return c;
    }

    private String shortPath(File dir) {
        if (dir == null) return "";
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String p = dir.getAbsolutePath();
        return p.startsWith(root) ? (p.length() == root.length() ? "/" : p.substring(root.length())) : p;
    }

    private void updateDeleteButton() {
        if (deleteButton == null || scan == null) return;
        long bytes = 0;
        for (File f : selected) bytes += scan.sizeOf(f);
        boolean none = selected.isEmpty();
        deleteButton.setText(none ? "Selecione itens para apagar"
                : "🗑  Apagar " + selected.size() + (selected.size() == 1 ? " item" : " itens") + " (" + Ui.size(bytes) + ")");
        deleteButton.setEnabled(!none);
        deleteButton.setAlpha(none ? 0.5f : 1f);
    }

    private void confirmDelete() {
        if (scan == null || selected.isEmpty()) return;
        boolean review = false;
        for (Scanner.Category c : scan.categories) {
            if (c.safe) continue;
            for (File f : c.files) {
                if (selected.contains(f)) {
                    review = true;
                    break;
                }
            }
            if (review) break;
        }
        String msg = "Os " + selected.size() + " itens selecionados serão apagados permanentemente."
                + (review ? "\n\nAtenção: você marcou itens da categoria REVISAR. Confirme que não precisa deles." : "");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Apagar arquivos?")
                .setMessage(msg)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", (d, w) -> doDelete())
                .show();
    }

    private void doDelete() {
        final List<File> list = new ArrayList<>(selected);
        final Scanner.Result current = scan;
        deleting = true;
        render();
        new Thread(() -> {
            long freed = 0;
            int ok = 0;
            Set<File> done = new HashSet<>();
            List<String> paths = new ArrayList<>();
            for (File f : list) {
                boolean gone;
                try {
                    gone = f.delete() || !f.exists();
                } catch (Exception e) {
                    gone = false;
                }
                if (gone) {
                    freed += current.sizeOf(f);
                    ok++;
                    done.add(f);
                    paths.add(f.getAbsolutePath());
                }
            }
            try {
                if (!paths.isEmpty()) {
                    MediaScannerConnection.scanFile(getApplicationContext(), paths.toArray(new String[0]), null, null);
                }
            } catch (Exception ignored) {
            }
            final long fr = freed;
            final int n = ok;
            ui.post(() -> {
                deleting = false;
                current.remove(done);
                selected.removeAll(done);
                toast("Pronto! " + n + " itens apagados • " + Ui.size(fr) + " liberados");
                if (tab == TAB_LIMPEZA) render();
            });
        }).start();
    }

    // ------------------------------------------------------------------ APPS

    private boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int uid = android.os.Process.myUid();
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void showApps() {
        final boolean usage = hasUsageAccess();
        if (!usage) {
            LinearLayout c = card();
            c.addView(h("🔒 Ative o acesso de uso"));
            c.addView(p("Com essa permissão o ZEUS Optimizer descobre há quantos dias cada app não é aberto e quanto espaço ocupa, para indicar o que pode ser removido."));
            c.addView(small("Na tela que abrir, procure \"ZEUS Optimizer\" e ative."));
            c.addView(btn("Ativar acesso de uso", Ui.ACCENT_DARK,
                    v -> open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
            content.addView(c);
        }
        final LinearLayout loading = card();
        loading.addView(p("Carregando apps..."));
        content.addView(loading);
        final int myGen = gen;
        new Thread(() -> {
            AppAnalyzer.Result r;
            try {
                r = AppAnalyzer.load(this, usage);
            } catch (Throwable t) {
                r = null;
            }
            final AppAnalyzer.Result res = r;
            ui.post(() -> {
                if (myGen != gen) return;
                content.removeView(loading);
                if (res == null) {
                    content.addView(p("Não foi possível listar os apps."));
                    return;
                }
                renderApps(res, usage);
            });
        }).start();
    }

    private void renderApps(AppAnalyzer.Result r, boolean usage) {
        LinearLayout sum = card();
        sum.addView(h("📱 Seus apps"));
        sum.addView(p(r.apps.size() + " apps instalados por você"));
        if (usage) {
            sum.addView(space(6));
            if (r.removableCount > 0) {
                sum.addView(Ui.text(this, r.removableCount + (r.removableCount == 1 ? " app pode" : " apps podem") + " ser removido" + (r.removableCount == 1 ? "" : "s"), 20, Ui.DANGER, true));
                if (r.removableBytes > 0) sum.addView(p("Liberaria cerca de " + Ui.size(r.removableBytes)));
            } else {
                sum.addView(Ui.text(this, "Nenhum app esquecido 👍", 18, Ui.ACCENT, true));
            }
        }
        sum.addView(space(8));
        sum.addView(legend("REMOVER", Ui.DANGER, "não usado há 60+ dias"));
        sum.addView(legend("REVISAR", Ui.WARN, "pouco usado ou com cache alto"));
        sum.addView(legend("MANTER", Ui.ACCENT, "em uso"));
        content.addView(sum);

        for (AppAnalyzer.App a : r.apps) content.addView(appRow(a));

        LinearLayout sys = card();
        sys.addView(h("🛡 Apps do sistema: " + r.systemCount));
        sys.addView(p("Ficam ocultos porque são necessários para o Android funcionar (telefone, câmera, Google Play Services, etc.). Não tente removê-los."));
        content.addView(sys);
    }

    private View appRow(AppAnalyzer.App a) {
        LinearLayout c = card();
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        if (a.icon != null) iv.setImageDrawable(a.icon);
        top.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tx = vertical();
        tx.setPadding(dp(12), 0, 0, 0);
        tx.addView(Ui.text(this, a.label, 16, Ui.TEXT, true));
        String info = a.size > 0
                ? Ui.size(a.size) + (a.cache > 0 ? " • cache " + Ui.size(a.cache) : "")
                : a.pkg;
        tx.addView(Ui.text(this, info, 13, Ui.MUTED, false));
        int color = a.level == AppAnalyzer.REMOVE ? Ui.DANGER : a.level == AppAnalyzer.REVIEW ? Ui.WARN : Ui.ACCENT;
        tx.addView(Ui.text(this, a.reason, 13, color, true));
        top.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(top);

        LinearLayout btns = row();
        btns.setPadding(0, dp(10), 0, 0);
        Button un = Ui.button(this, "Desinstalar", a.level == AppAnalyzer.REMOVE ? Ui.DANGER_DARK : Ui.CARD2);
        un.setOnClickListener(v -> open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))));
        Button cache = Ui.button(this, "Limpar cache ›", Ui.CARD2);
        cache.setOnClickListener(v -> {
            toast("Toque em Armazenamento › Limpar cache");
            open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMargins(dp(6), 0, 0, 0);
        btns.addView(un, l1);
        btns.addView(cache, l2);
        c.addView(btns);
        return c;
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private static int pct(long part, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, part * 100 / total));
    }

    private static String fmt1(float v) {
        return String.format(Locale.getDefault(), "%.1f", v);
    }

    private LinearLayout card() {
        return Ui.card(this);
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView h(String s) {
        return Ui.text(this, s, 17, Ui.TEXT, true);
    }

    private TextView p(String s) {
        return Ui.text(this, s, 14, Ui.TEXT, false);
    }

    private TextView small(String s) {
        return Ui.text(this, s, 12.5f, Ui.MUTED, false);
    }

    private View space(int dpHeight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpHeight)));
        return v;
    }

    private TextView tag(String s, int color) {
        TextView t = Ui.text(this, s, 11, color, true);
        t.setBackground(Ui.rounded(this, (color & 0x00FFFFFF) | 0x33000000, 8));
        t.setPadding(dp(8), dp(3), dp(8), dp(3));
        return t;
    }

    private View legend(String label, int color, String desc) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(3), 0, dp(3));
        r.addView(tag(label, color));
        TextView d = small("  " + desc);
        r.addView(d);
        return r;
    }

    private Button btn(String text, int color, View.OnClickListener l) {
        Button b = Ui.button(this, text, color);
        if (l != null) b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Não foi possível abrir essa tela neste aparelho");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}


/**
 * Varre o armazenamento compartilhado e separa o que é seguro apagar
 * do que o usuário precisa revisar. Nunca entra em Android/data e Android/obb.
 */
final class Scanner {

    interface Progress {
        void update(int files, String path);
    }

    static final class Category {
        final String title;
        final String desc;
        final boolean safe;
        final List<File> files = new ArrayList<>();
        long bytes;

        Category(String title, String desc, boolean safe) {
            this.title = title;
            this.desc = desc;
            this.safe = safe;
        }
    }

    static final class Result {
        final List<Category> categories = new ArrayList<>();
        final Map<File, Long> sizes = new HashMap<>();
        int scanned;

        long sizeOf(File f) {
            Long l = sizes.get(f);
            return l == null ? 0 : l;
        }

        long safeBytes() {
            long b = 0;
            for (Category c : categories) if (c.safe) b += c.bytes;
            return b;
        }

        long reviewBytes() {
            long b = 0;
            for (Category c : categories) if (!c.safe) b += c.bytes;
            return b;
        }

        boolean isEmpty() {
            for (Category c : categories) if (!c.files.isEmpty()) return false;
            return true;
        }

        void remove(Set<File> gone) {
            for (Category c : categories) {
                c.files.removeIf(gone::contains);
                long b = 0;
                for (File f : c.files) b += sizeOf(f);
                c.bytes = b;
            }
        }
    }

    private static final long MB = 1024L * 1024;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private final Category apk = new Category("Instaladores APK",
            "Arquivos .apk servem só para instalar apps. Apagar não desinstala nada.", true);
    private final Category thumbs = new Category("Miniaturas da galeria",
            "Cache de miniaturas (.thumbnails). O Android recria quando precisar.", true);
    private final Category temp = new Category("Temporários e logs",
            "Downloads incompletos (.part, .crdownload), arquivos .tmp e registros .log.", true);
    private final Category trash = new Category("Lixeira de fotos",
            "Arquivos que já foram para a lixeira (.trashed). Seriam apagados depois de qualquer jeito.", true);
    private final Category empty = new Category("Pastas vazias",
            "Pastas sem nenhum arquivo dentro.", true);
    private final Category dup = new Category("Arquivos duplicados",
            "Cópias idênticas. A cópia mais antiga de cada arquivo é mantida e não aparece aqui.", false);
    private final Category waSent = new Category("Mídia enviada pelo WhatsApp",
            "Cópias das fotos e vídeos que você enviou. O original normalmente continua na galeria.", false);
    private final Category waBackup = new Category("Backups antigos do WhatsApp",
            "Backups locais com data no nome. O backup mais recente (msgstore.db) não é tocado.", false);
    private final Category oldDl = new Category("Downloads antigos (+90 dias)",
            "Arquivos da pasta Download que não são mexidos há mais de 3 meses.", false);
    private final Category large = new Category("Arquivos grandes (+100 MB)",
            "Vídeos, backups e outros arquivos pesados. Confira se ainda precisa deles.", false);

    private final File root;
    private final String rootPath;
    private final Progress progress;
    private final Result result = new Result();
    private final List<File> pending = new ArrayList<>();
    private final Set<File> dupSet = new HashSet<>();
    private final long now = System.currentTimeMillis();
    private long lastReport;

    Scanner(File root, Progress progress) {
        this.root = root;
        this.rootPath = root.getAbsolutePath();
        this.progress = progress;
    }

    Result scan() {
        walk(root, 0, false);
        findDuplicates();
        for (File f : pending) {
            if (dupSet.contains(f)) continue;
            long len = result.sizeOf(f);
            String rel = relative(f).toLowerCase(Locale.ROOT);
            if (len >= 100 * MB) {
                add(large, f, len);
            } else if (rel.startsWith("/download") && now - f.lastModified() > 90 * DAY) {
                add(oldDl, f, len);
            }
        }
        Collections.addAll(result.categories, apk, thumbs, temp, trash, empty, dup, waSent, waBackup, oldDl, large);
        return result;
    }

    private String relative(File f) {
        String p = f.getAbsolutePath();
        return p.length() > rootPath.length() ? p.substring(rootPath.length()) : "";
    }

    private void walk(File dir, int depth, boolean inThumbs) {
        if (depth > 16) return;
        String rel = relative(dir).toLowerCase(Locale.ROOT);
        if (rel.startsWith("/android/data") || rel.startsWith("/android/obb")) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        if (list.length == 0) {
            if (depth > 1 && !rel.startsWith("/android")) add(empty, dir, 0);
            return;
        }
        boolean th = inThumbs || dir.getName().equalsIgnoreCase(".thumbnails");
        for (File f : list) {
            try {
                if (Files.isSymbolicLink(f.toPath())) continue;
            } catch (Exception e) {
                continue;
            }
            if (f.isDirectory()) walk(f, depth + 1, th);
            else file(f, rel, th);
        }
    }

    private void report(String where) {
        long t = System.currentTimeMillis();
        if (t - lastReport > 250 && progress != null) {
            lastReport = t;
            progress.update(result.scanned, where);
        }
    }

    private void file(File f, String relDir, boolean inThumbs) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.equals(".nomedia")) return;
        result.scanned++;
        report(relDir.isEmpty() ? "/" : relDir);
        long len = f.length();

        if (inThumbs) { add(thumbs, f, len); return; }
        if (n.startsWith(".trashed-")) { add(trash, f, len); return; }
        if (n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk")) { add(apk, f, len); return; }
        if (n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".crdownload")
                || n.endsWith(".part") || n.endsWith(".partial") || n.endsWith(".log")) {
            add(temp, f, len);
            return;
        }
        boolean whatsapp = relDir.contains("/whatsapp/") || relDir.contains("/whatsapp business/");
        if (whatsapp && relDir.endsWith("/databases") && n.matches("msgstore-\\d{4}-\\d{2}-\\d{2}.*")) {
            add(waBackup, f, len);
            return;
        }
        if (whatsapp && relDir.contains("/media/") && relDir.endsWith("/sent")) {
            add(waSent, f, len);
            return;
        }
        // Os demais podem virar duplicados, grandes ou downloads antigos
        result.sizes.put(f, len);
        pending.add(f);
    }

    private void findDuplicates() {
        Map<Long, List<File>> bySize = new HashMap<>();
        for (File f : pending) {
            long len = result.sizeOf(f);
            if (len >= 256 * 1024) bySize.computeIfAbsent(len, k -> new ArrayList<>()).add(f);
        }
        for (List<File> group : bySize.values()) {
            if (group.size() < 2) continue;
            report("Procurando duplicados...");
            Map<String, List<File>> byHash = new HashMap<>();
            for (File f : group) {
                String h = hash(f);
                if (h != null) byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(f);
            }
            for (List<File> same : byHash.values()) {
                if (same.size() < 2) continue;
                Collections.sort(same, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 1; i < same.size(); i++) {
                    File f = same.get(i);
                    add(dup, f, result.sizeOf(f));
                    dupSet.add(f);
                }
            }
        }
    }

    private static String hash(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            long len = raf.length();
            byte[] buf = new byte[64 * 1024];
            if (len <= 16 * MB) {
                readRange(raf, md, buf, 0, len);
            } else {
                readRange(raf, md, buf, 0, 4 * MB);
                readRange(raf, md, buf, len / 2, 4 * MB);
                readRange(raf, md, buf, len - 4 * MB, 4 * MB);
            }
            StringBuilder sb = new StringBuilder().append(len).append(':');
            for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void readRange(RandomAccessFile raf, MessageDigest md, byte[] buf, long start, long count)
            throws IOException {
        raf.seek(start);
        long left = count;
        while (left > 0) {
            int r = raf.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) break;
            md.update(buf, 0, r);
            left -= r;
        }
    }

    private void add(Category c, File f, long len) {
        c.files.add(f);
        c.bytes += len;
        result.sizes.put(f, len);
    }
}


/** Lista os apps instalados pelo usuário e indica quais podem ser removidos. */
final class AppAnalyzer {

    static final int REMOVE = 0, REVIEW = 1, KEEP = 2;

    static final class App {
        String pkg, label, reason;
        Drawable icon;
        long size, cache, lastUsed;
        int level = KEEP;
    }

    static final class Result {
        final List<App> apps = new ArrayList<>();
        int systemCount;
        int removableCount;
        long removableBytes;
    }

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long MB = 1024L * 1024;

    static Result load(Context ctx, boolean usage) {
        Result r = new Result();
        PackageManager pm = ctx.getPackageManager();
        long now = System.currentTimeMillis();

        Map<String, UsageStats> stats = new HashMap<>();
        if (usage) {
            try {
                UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
                Map<String, UsageStats> m = usm.queryAndAggregateUsageStats(now - 365L * DAY, now);
                if (m != null) stats = m;
            } catch (Exception ignored) {
            }
        }
        StorageStatsManager ssm = (StorageStatsManager) ctx.getSystemService(Context.STORAGE_STATS_SERVICE);
        Set<String> keep = protectedPackages(ctx, pm);

        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                r.systemCount++;
                continue;
            }
            if (ai.packageName.equals(ctx.getPackageName())) continue;

            App a = new App();
            a.pkg = ai.packageName;
            a.label = String.valueOf(ai.loadLabel(pm));
            try {
                a.icon = ai.loadIcon(pm);
            } catch (Exception ignored) {
            }

            long installed = now;
            try {
                installed = pm.getPackageInfo(a.pkg, 0).firstInstallTime;
            } catch (Exception ignored) {
            }

            if (usage && ssm != null) {
                try {
                    StorageStats s = ssm.queryStatsForPackage(ai.storageUuid, a.pkg, android.os.Process.myUserHandle());
                    a.cache = s.getCacheBytes();
                    a.size = s.getAppBytes() + s.getDataBytes();
                } catch (Exception ignored) {
                }
            }

            UsageStats u = stats.get(a.pkg);
            a.lastUsed = u != null ? u.getLastTimeUsed() : 0;
            if (a.lastUsed < 946684800000L) a.lastUsed = 0; // antes de 2000 = sem registro

            long since = a.lastUsed > 0 ? (now - a.lastUsed) / DAY : -1;
            long age = (now - installed) / DAY;
            boolean launchable = pm.getLaunchIntentForPackage(a.pkg) != null;

            if (!usage) {
                a.level = KEEP;
                a.reason = "Ative o acesso de uso para analisar";
            } else if (keep.contains(a.pkg) || !launchable) {
                a.level = KEEP;
                a.reason = "Teclado, tela inicial ou serviço — mantenha se usa";
            } else if (since < 0 || since >= 60) {
                if (age < 14) {
                    a.level = KEEP;
                    a.reason = "Instalado recentemente";
                } else {
                    a.level = REMOVE;
                    a.reason = since < 0
                            ? "Sem uso registrado — pode remover"
                            : "Não usado há " + since + " dias — pode remover";
                }
            } else if (since >= 30) {
                a.level = REVIEW;
                a.reason = "Pouco usado: aberto há " + since + " dias";
            } else {
                a.level = KEEP;
                a.reason = since == 0 ? "Em uso: aberto hoje"
                        : "Em uso: aberto há " + since + (since == 1 ? " dia" : " dias");
            }
            if (a.cache >= 200 * MB && a.level == KEEP) {
                a.level = REVIEW;
                a.reason += " • cache alto, vale limpar";
            }
            if (a.level == REMOVE) {
                r.removableCount++;
                r.removableBytes += a.size;
            }
            r.apps.add(a);
        }

        Collections.sort(r.apps, (x, y) -> x.level != y.level
                ? Integer.compare(x.level, y.level)
                : Long.compare(y.size, x.size));
        return r;
    }

    /** Teclados ativos e a tela inicial padrão nunca são sugeridos para remoção. */
    private static Set<String> protectedPackages(Context ctx, PackageManager pm) {
        Set<String> s = new HashSet<>();
        try {
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            for (InputMethodInfo i : imm.getEnabledInputMethodList()) s.add(i.getPackageName());
        } catch (Exception ignored) {
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) s.add(ri.activityInfo.packageName);
        } catch (Exception ignored) {
        }
        return s;
    }
}


final class Ui {
    static final int BG = 0xFF0F172A;
    static final int CARD = 0xFF1E293B;
    static final int CARD2 = 0xFF334155;
    static final int TEXT = 0xFFF1F5F9;
    static final int MUTED = 0xFF94A3B8;
    static final int ACCENT = 0xFF22C55E;
    static final int ACCENT_DARK = 0xFF15803D;
    static final int WARN = 0xFFF59E0B;
    static final int DANGER = 0xFFEF4444;
    static final int DANGER_DARK = 0xFFB91C1C;
    static final int BLUE = 0xFF38BDF8;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        double k = b / 1024.0;
        if (k < 1024) return String.format(Locale.getDefault(), "%.0f KB", k);
        double m = k / 1024;
        if (m < 1024) return String.format(Locale.getDefault(), "%.1f MB", m);
        return String.format(Locale.getDefault(), "%.2f GB", m / 1024);
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(c, CARD, 16));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        return l;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static Button button(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(c, color, 12));
        b.setStateListAnimator(null);
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    static ProgressBar bar(Context c, int pct, int color) {
        ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(pct);
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(CARD2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 10));
        lp.setMargins(0, dp(c, 10), 0, dp(c, 8));
        pb.setLayoutParams(lp);
        return pb;
    }

    static int levelColor(int pct) {
        return pct >= 90 ? DANGER : pct >= 75 ? WARN : ACCENT;
    }
}
