package ru.neverlands.abclient;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ru.neverlands.abclient.manager.ClanWarsManager;
import ru.neverlands.abclient.repository.ApiRepository;

/**
 * Экран "Кланы" с двумя вкладками:
 * 1) Синхронизация clans.txt
 * 2) Текущие войны wars.cgi
 */
public class ClansActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clans);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Кланы");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new ClansPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText("Кланы");
            } else {
                tab.setText("Текущие войны");
            }
        }).attach();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class ClansPagerAdapter extends FragmentStateAdapter {
        ClansPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new ClansSyncFragment();
            }
            return new CurrentWarsFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class ClansSyncFragment extends Fragment {
        private MaterialButton btnSyncClans;
        private TextView tvSyncStatus;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_clans_sync, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            btnSyncClans = view.findViewById(R.id.btnSyncClans);
            tvSyncStatus = view.findViewById(R.id.tvSyncStatus);

            btnSyncClans.setOnClickListener(v -> syncClans());
            updateStatusText();
        }

        private void syncClans() {
            if (!isAdded()) {
                return;
            }
            btnSyncClans.setEnabled(false);
            tvSyncStatus.setText("Синхронизация clans.txt...");

            ClanWarsManager.getInstance(requireContext()).syncClanListAsync(new ApiRepository.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        btnSyncClans.setEnabled(true);
                        updateStatusText();
                        Toast.makeText(requireContext(), "Список кланов обновлён", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(String message) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        btnSyncClans.setEnabled(true);
                        tvSyncStatus.setText("Ошибка синхронизации: " + safeText(message));
                        Toast.makeText(requireContext(), "Ошибка обновления clans.txt", Toast.LENGTH_LONG).show();
                    });
                }
            });
        }

        private void updateStatusText() {
            if (!isAdded()) {
                return;
            }
            File clansFile = ClanWarsManager.getInstance(requireContext()).getClansFile();
            if (clansFile.exists()) {
                tvSyncStatus.setText("Файл: " + clansFile.getAbsolutePath()
                        + "\nОбновлён: " + formatTime(clansFile.lastModified()));
            } else {
                tvSyncStatus.setText("Файл clans.txt ещё не загружен.");
            }
        }

        private String formatTime(long timeMs) {
            if (timeMs <= 0L) {
                return "-";
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timeMs));
        }
    }

    public static class CurrentWarsFragment extends Fragment {
        private static final TimeZone KIEV_TIME_ZONE = TimeZone.getTimeZone("Europe/Kiev");

        private MaterialButton btnRefreshWars;
        private TextView tvWarsStatus;
        private TableLayout tableWars;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_clans_wars, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            btnRefreshWars = view.findViewById(R.id.btnRefreshWars);
            tvWarsStatus = view.findViewById(R.id.tvWarsStatus);
            tableWars = view.findViewById(R.id.tableWars);

            btnRefreshWars.setOnClickListener(v -> refreshWars(true));
            renderRows(ClanWarsManager.getInstance(requireContext()).buildWarsTableRows());
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshWars(false);
        }

        private void refreshWars(boolean manual) {
            if (!isAdded()) {
                return;
            }
            btnRefreshWars.setEnabled(false);
            tvWarsStatus.setText("Синхронизация текущих войн...");

            ClanWarsManager manager = ClanWarsManager.getInstance(requireContext());
            manager.syncWarsAsync(new ApiRepository.ApiCallback<List<ClanWarsManager.WarEntry>>() {
                @Override
                public void onSuccess(List<ClanWarsManager.WarEntry> result) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        btnRefreshWars.setEnabled(true);
                        List<ClanWarsManager.WarTableRow> rows = manager.buildWarsTableRows();
                        renderRows(rows);
                        tvWarsStatus.setText("Обновлено: " + formatTimeKiev(System.currentTimeMillis())
                                + " | Войн: " + rows.size());
                        if (manual) {
                            Toast.makeText(requireContext(), "Текущие войны обновлены", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(String message) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        btnRefreshWars.setEnabled(true);
                        List<ClanWarsManager.WarTableRow> rows = manager.buildWarsTableRows();
                        renderRows(rows);
                        tvWarsStatus.setText("Ошибка синхронизации: " + safeText(message)
                                + " | Показан кеш, войн: " + rows.size());
                    });
                }
            });
        }

        private void renderRows(List<ClanWarsManager.WarTableRow> rows) {
            if (!isAdded() || tableWars == null) {
                return;
            }
            tableWars.removeAllViews();
            tableWars.addView(createHeaderRow());
            for (ClanWarsManager.WarTableRow row : rows) {
                tableWars.addView(createDataRow(row));
            }
        }

        private TableRow createHeaderRow() {
            TableRow row = new TableRow(requireContext());
            row.setBackgroundColor(0xFFE0E0E0);
            row.addView(createHeaderCell("Дата Начала", dp(160)));
            row.addView(createHeaderCell("Агрессор", dp(300)));
            row.addView(createHeaderCell("Счёт1", dp(70)));
            row.addView(createHeaderCell("Счёт2", dp(70)));
            row.addView(createHeaderCell("Противник", dp(300)));
            row.addView(createHeaderCell("Конец", dp(160)));
            return row;
        }

        private TableRow createDataRow(ClanWarsManager.WarTableRow data) {
            TableRow row = new TableRow(requireContext());
            row.addView(createTextCell(data.startText, dp(160), true));
            row.addView(createPartyCell(
                    data.aggressorInclinationIconUrl,
                    data.aggressorClanIconUrl,
                    data.aggressorText,
                    dp(300)
            ));
            row.addView(createTextCell(data.score1Text, dp(70), false));
            row.addView(createTextCell(data.score2Text, dp(70), false));
            row.addView(createPartyCell(
                    data.opponentInclinationIconUrl,
                    data.opponentClanIconUrl,
                    data.opponentText,
                    dp(300)
            ));
            row.addView(createTextCell(data.endText, dp(160), true));
            return row;
        }

        private View createHeaderCell(String text, int widthPx) {
            TextView tv = new TextView(requireContext());
            TableRow.LayoutParams lp = new TableRow.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            tv.setPadding(dp(8), dp(8), dp(8), dp(8));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextSize(13f);
            tv.setText(text);
            return tv;
        }

        private View createTextCell(String text, int widthPx, boolean leftAlign) {
            TextView tv = new TextView(requireContext());
            TableRow.LayoutParams lp = new TableRow.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            tv.setPadding(dp(8), dp(6), dp(8), dp(6));
            tv.setTextSize(13f);
            tv.setText(safeText(text));
            tv.setTypeface(Typeface.DEFAULT);
            tv.setTextAlignment(leftAlign ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_CENTER);
            return tv;
        }

        private View createPartyCell(String inclinationIcon, String clanIcon, String text, int widthPx) {
            android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
            root.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            TableRow.LayoutParams rowLp = new TableRow.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            root.setLayoutParams(rowLp);
            root.setPadding(dp(8), dp(6), dp(8), dp(6));

            ImageView inclView = new ImageView(requireContext());
            android.widget.LinearLayout.LayoutParams iconLp = new android.widget.LinearLayout.LayoutParams(dp(15), dp(12));
            inclView.setLayoutParams(iconLp);
            if (!isEmpty(inclinationIcon)) {
                Glide.with(this).load(inclinationIcon).into(inclView);
                root.addView(inclView);
            }

            ImageView clanView = new ImageView(requireContext());
            android.widget.LinearLayout.LayoutParams clanLp = new android.widget.LinearLayout.LayoutParams(dp(15), dp(12));
            clanLp.leftMargin = dp(4);
            clanView.setLayoutParams(clanLp);
            if (!isEmpty(clanIcon)) {
                Glide.with(this).load(clanIcon).into(clanView);
                root.addView(clanView);
            }

            TextView tv = new TextView(requireContext());
            android.widget.LinearLayout.LayoutParams textLp = new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textLp.leftMargin = dp(6);
            tv.setLayoutParams(textLp);
            tv.setText(safeText(text));
            tv.setTextSize(13f);
            root.addView(tv);
            return root;
        }

        private int dp(int value) {
            float density = requireContext().getResources().getDisplayMetrics().density;
            return (int) (value * density);
        }

        private String formatTimeKiev(long timeMs) {
            if (timeMs <= 0L) {
                return "-";
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(KIEV_TIME_ZONE);
            return sdf.format(new Date(timeMs));
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
