package com.zeus.optimizer;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Aba Limpeza: análise completa do armazenamento por categoria e por tipo. */
final class CleanScreen extends Screen {

    private static final int[] TYPE_COLORS = {Theme.CYAN, Theme.PURPLE, Theme.PINK, Theme.BLUE,
            Theme.GOLD, Theme.ORANGE, 0xFF475569};

    private Scanner.Result scan;
    private boolean scanning, deleting;
    private String status = "Iniciando...";
    private TextView statusView;
    private Button deleteButton;
    private final Set<File> selected = new HashSet<>();

    CleanScreen(MainActivity a) {
        super(a);
    }

    @Override
    void build(LinearLayout root) {
        deleteButton = null;
        statusView = null;

        if (!Optimizer.hasStorageAccess(a)) {
            LinearLayout c = Theme.card(a);
            c.addView(Theme.icon(a, "🔒", Theme.GOLD, 52));
            c.addView(Theme.space(a, 10));
            c.addView(Theme.title(a, "Permissão necessária"));
            c.addView(Theme.space(a, 4));
            c.addView(Theme.body(a, "Para encontrar arquivos inúteis, o ZEUS Optimizer precisa de acesso aos arquivos do celular. Nada é apagado sem a sua confirmação."));
            addButton(c, Theme.primary(a, "Conceder acesso"), v -> a.requestStorageAccess());
            root.addView(c);
            return;
        }

        if (scanning || deleting) {
            LinearLayout c = Theme.card(a);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.addView(Theme.title(a, deleting ? "Apagando arquivos..." : "Analisando o armazenamento..."));
            ProgressBar spin = new ProgressBar(a);
            spin.setIndeterminateTintList(ColorStateList.valueOf(Theme.GOLD));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            lp.setMargins(0, dp(18), 0, dp(14));
            c.addView(spin, lp);
            statusView = Theme.small(a, deleting ? "Aguarde" : status);
            statusView.setGravity(Gravity.CENTER);
            c.addView(statusView);
            root.addView(c);
            return;
        }

        if (scan == null) {
            LinearLayout c = Theme.card(a);
            c.addView(Theme.icon(a, "🧹", Theme.CYAN, 52));
            c.addView(Theme.space(a, 10));
            c.addView(Theme.title(a, "Limpeza profunda"));
            c.addView(Theme.space(a, 4));
            c.addView(Theme.body(a, "Analisa todo o armazenamento: instaladores, miniaturas, temporários, lixeira, pastas vazias, duplicados, arquivos grandes, downloads antigos e mídia repetida do WhatsApp."));
            c.addView(Theme.space(a, 12));
            c.addView(legend("SEGURO", Theme.GREEN, "pode apagar sem medo, já vem marcado"));
            c.addView(legend("REVISAR", Theme.GOLD, "confira antes — pode ser algo seu"));
            c.addView(Theme.space(a, 8));
            c.addView(Theme.small(a, "Pastas do sistema e dados de apps (Android/data e Android/obb) nunca são tocadas."));
            addButton(c, Theme.primary(a, "🔍  Analisar armazenamento"), v -> startScan());
            root.addView(c);
            return;
        }

        // ---------------- Resumo
        LinearLayout sum = Theme.card(a);
        sum.addView(Theme.small(a, "PODE SER LIBERADO COM SEGURANÇA"));
        sum.addView(Theme.text(a, Theme.size(scan.safeBytes()), 34, Theme.GREEN, true));
        sum.addView(Theme.small(a, "+ " + Theme.size(scan.reviewBytes()) + " em arquivos para revisar • "
                + scan.scanned + " arquivos analisados"));
        addButton(sum, Theme.secondary(a, "Analisar de novo"), v -> startScan());
        root.addView(sum);

        // ---------------- O que ocupa espaço
        root.addView(Theme.section(a, "O que ocupa seu espaço"));
        LinearLayout br = Theme.card(a);
        float[] vals = new float[Scanner.TYPE_NAMES.length];
        for (int i = 0; i < vals.length; i++) vals[i] = scan.typeBytes[i];
        BarView bar = new BarView(a, 14);
        bar.set(Math.max(1, scan.totalBytes), vals, TYPE_COLORS);
        br.addView(bar);
        for (int i = 0; i < Scanner.TYPE_NAMES.length; i++) {
            if (scan.typeCount[i] == 0) continue;
            LinearLayout r = Theme.row(a);
            r.setPadding(0, dp(6), 0, dp(6));
            View dot = new View(a);
            dot.setBackground(Theme.rounded(a, TYPE_COLORS[i], 5));
            r.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
            TextView n = Theme.text(a, Scanner.TYPE_NAMES[i], 14, Theme.TEXT, false);
            n.setPadding(dp(10), 0, 0, 0);
            r.addView(n, Theme.weight(1));
            r.addView(Theme.small(a, scan.typeCount[i] + " arq.   "));
            r.addView(Theme.text(a, Theme.size(scan.typeBytes[i]), 14, Theme.TEXT, true));
            br.addView(r);
        }
        root.addView(br);

        if (scan.isEmpty()) {
            LinearLayout c = Theme.card(a);
            c.addView(Theme.title(a, "🎉 Nada para limpar"));
            c.addView(Theme.small(a, "Seu armazenamento está organizado."));
            root.addView(c);
            return;
        }

        root.addView(Theme.section(a, "Encontrado"));
        for (Scanner.Category cat : scan.categories) {
            if (!cat.files.isEmpty()) root.addView(categoryCard(cat));
        }
        deleteButton = Theme.danger(a, "");
        deleteButton.setOnClickListener(v -> confirmDelete());
        root.addView(deleteButton, Theme.fullWidth(a, 4));
        updateDeleteButton();
    }

    private View legend(String label, int color, String desc) {
        LinearLayout r = Theme.row(a);
        r.setPadding(0, dp(3), 0, dp(3));
        r.addView(Theme.tag(a, label, color));
        TextView d = Theme.small(a, "  " + desc);
        r.addView(d);
        return r;
    }

    void startScan() {
        if (scanning || !Optimizer.hasStorageAccess(a)) return;
        scanning = true;
        status = "Iniciando...";
        if (a.tab() == MainActivity.CLEAN) a.refresh();
        final File root = Environment.getExternalStorageDirectory();
        new Thread(() -> {
            Scanner.Result r;
            try {
                r = new Scanner(root, true, (files, path) -> a.ui.post(() -> {
                    status = files + " arquivos • " + path;
                    if (statusView != null) statusView.setText(status);
                })).scan();
            } catch (Throwable t) {
                r = null;
            }
            final Scanner.Result res = r;
            a.ui.post(() -> {
                scanning = false;
                if (res == null) {
                    a.toast("Não foi possível analisar o armazenamento");
                } else {
                    scan = res;
                    selected.clear();
                    for (Scanner.Category c : res.categories) if (c.safe) selected.addAll(c.files);
                }
                if (a.tab() == MainActivity.CLEAN) a.refresh();
            });
        }).start();
    }

    private View categoryCard(Scanner.Category cat) {
        LinearLayout c = Theme.card(a);

        LinearLayout head = Theme.row(a);
        CheckBox all = new CheckBox(a);
        all.setButtonTintList(ColorStateList.valueOf(Theme.GOLD));
        all.setChecked(selected.containsAll(cat.files));
        head.addView(all);
        head.addView(Theme.icon(a, cat.icon, cat.safe ? Theme.GREEN : Theme.GOLD, 38));
        LinearLayout tx = Theme.col(a);
        tx.setPadding(dp(10), 0, dp(6), 0);
        tx.addView(Theme.text(a, cat.title, 15, Theme.TEXT, true));
        tx.addView(Theme.small(a, Theme.size(cat.bytes) + " • " + cat.files.size()
                + (cat.files.size() == 1 ? " item" : " itens")));
        head.addView(tx, Theme.weight(1));
        head.addView(Theme.tag(a, cat.safe ? "SEGURO" : "REVISAR", cat.safe ? Theme.GREEN : Theme.GOLD));
        c.addView(head);

        c.addView(Theme.space(a, 8));
        c.addView(Theme.small(a, cat.desc));

        final LinearLayout list = Theme.col(a);
        final List<CheckBox> boxes = new ArrayList<>();
        final TextView toggle = Theme.text(a, "Ver arquivos ▾", 13.5f, Theme.CYAN, true);
        toggle.setPadding(0, dp(10), 0, dp(2));
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
                CheckBox cb = new CheckBox(a);
                cb.setButtonTintList(ColorStateList.valueOf(Theme.GOLD));
                cb.setText(f.getName() + "\n" + Theme.size(scan.sizeOf(f)) + " • " + shortPath(f.getParentFile()));
                cb.setTextColor(0xFFCBD5E1);
                cb.setTextSize(12.5f);
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
                list.addView(Theme.small(a, "+ " + (cat.files.size() - max) + " arquivos (marcados junto com a categoria)"));
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
                : "🗑  Apagar " + selected.size() + (selected.size() == 1 ? " item" : " itens") + " • " + Theme.size(bytes));
        deleteButton.setEnabled(!none);
        deleteButton.setAlpha(none ? 0.45f : 1f);
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
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
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
        a.refresh();
        new Thread(() -> {
            List<File> done = Optimizer.delete(a, list);
            long freed = 0;
            for (File f : done) freed += current.sizeOf(f);
            final long fr = freed;
            final Set<File> gone = new HashSet<>(done);
            a.ui.post(() -> {
                deleting = false;
                current.remove(gone);
                selected.removeAll(gone);
                a.toast("⚡ " + gone.size() + " itens apagados • " + Theme.size(fr) + " liberados");
                if (a.tab() == MainActivity.CLEAN) a.refresh();
            });
        }).start();
    }
}
