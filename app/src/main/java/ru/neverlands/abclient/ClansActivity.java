package ru.neverlands.abclient;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
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

        TabLayoutMediator mediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            TextView tabText = new TextView(this);
            tabText.setText(position == 0 ? "Кланы" : "Текущие войны");
            tabText.setPadding(dpTab(14), dpTab(8), dpTab(14), dpTab(8));
            tabText.setTypeface(Typeface.DEFAULT_BOLD);
            tabText.setTextColor(0xFFFFFFFF);
            tab.setCustomView(tabText);
        });
        mediator.attach();
        updateTabSelectionBackgrounds(tabLayout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                styleTab(tab, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                styleTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                styleTab(tab, true);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateTabSelectionBackgrounds(TabLayout tabLayout) {
        for (int index = 0; index < tabLayout.getTabCount(); index++) {
            TabLayout.Tab tab = tabLayout.getTabAt(index);
            styleTab(tab, index == tabLayout.getSelectedTabPosition());
        }
    }

    private void styleTab(@Nullable TabLayout.Tab tab, boolean selected) {
        if (tab == null || tab.getCustomView() == null) {
            return;
        }
        View customView = tab.getCustomView();
        customView.setBackgroundColor(selected ? 0xCC7E57C2 : 0x00000000);
    }

    private int dpTab(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
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
            tvWarsStatus.setTextColor(0xFFFFFFFF);
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
            for (int index = 0; index < rows.size(); index++) {
                tableWars.addView(createDataRow(rows.get(index)));
                if (index < rows.size() - 1) {
                    tableWars.addView(createDividerRow());
                }
            }
        }

        private TableRow createHeaderRow() {
            TableRow row = new TableRow(requireContext());
            row.setBackgroundColor(0xFF7E57C2);
            row.addView(createHeaderCell("Дата", dp(126)));
            row.addView(createHeaderCell("Агрессор/Противник", dp(190)));
            row.addView(createHeaderCell("Счёт", dp(70)));
            return row;
        }

        private TableRow createDataRow(ClanWarsManager.WarTableRow data) {
            TableRow row = new TableRow(requireContext());

            int score1 = parseIntSafe(data.score1Text);
            int score2 = parseIntSafe(data.score2Text);
            int aggressorColor = resolveWarPartyColor(score1, score2, true);
            int opponentColor = resolveWarPartyColor(score1, score2, false);

            String dateCell = safeText(data.startText)
                    + "\n-\n" + safeText(data.endText);
            row.addView(createTextCell(dateCell, dp(126), true));
            row.addView(createPartiesCell(
                    data.aggressorInclinationIconUrl,
                    data.aggressorClanIconUrl,
                    data.aggressorText,
                    aggressorColor,
                    data.opponentInclinationIconUrl,
                    data.opponentClanIconUrl,
                    data.opponentText,
                    opponentColor,
                    dp(190)
            ));
            row.addView(createScoreCell(data.score1Text, data.score2Text, aggressorColor, opponentColor, dp(130)));
            return row;
        }

        private View createHeaderCell(String text, int widthPx) {
            TextView tv = new TextView(requireContext());
            TableRow.LayoutParams lp = new TableRow.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            tv.setPadding(dp(8), dp(8), dp(8), dp(8));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextSize(13f);
            tv.setTextColor(0xFFFFFFFF);
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
            tv.setSingleLine(false);
            tv.setGravity(leftAlign ? Gravity.START : Gravity.CENTER_HORIZONTAL);
            tv.setTextAlignment(leftAlign ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_CENTER);
            return tv;
        }

        private View createPartiesCell(
                String aggressorInclinationIcon,
                String aggressorClanIcon,
                String aggressorText,
                int aggressorColor,
                String opponentInclinationIcon,
                String opponentClanIcon,
                String opponentText,
                int opponentColor,
                int widthPx) {
            android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            TableRow.LayoutParams rowLp = new TableRow.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            root.setLayoutParams(rowLp);
            root.setPadding(dp(8), dp(6), dp(8), dp(6));

            root.addView(createPartyLine(aggressorInclinationIcon, aggressorClanIcon, aggressorText, aggressorColor));
            root.addView(createSeparatorLine("обьявил войну"));
            root.addView(createPartyLine(opponentInclinationIcon, opponentClanIcon, opponentText, opponentColor));
            return root;
        }

        private View createPartyLine(String inclinationIcon, String clanIcon, String text, int textColor) {
            android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
            root.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);

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
            tv.setTextColor(textColor);
            tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            root.addView(tv);
            return root;
        }

        private View createSeparatorLine(String text) {
            TextView tv = new TextView(requireContext());
            tv.setText(safeText(text));
            tv.setTextSize(12f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setGravity(Gravity.START);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            lp.bottomMargin = dp(2);
            tv.setLayoutParams(lp);
            return tv;
        }

        private View createScoreCell(String score1Text, String score2Text, int score1Color, int score2Color, int widthPx) {
            android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            TableRow.LayoutParams rowLp = new TableRow.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            root.setLayoutParams(rowLp);
            root.setPadding(dp(8), dp(6), dp(8), dp(6));

            TextView score1View = new TextView(requireContext());
            score1View.setText(safeText(score1Text));
            score1View.setTextSize(13f);
            score1View.setTypeface(Typeface.DEFAULT_BOLD);
            score1View.setTextColor(score1Color);
            score1View.setGravity(Gravity.CENTER_HORIZONTAL);
            score1View.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            root.addView(score1View);

            root.addView(createSeparatorLine("-"));

            TextView score2View = new TextView(requireContext());
            score2View.setText(safeText(score2Text));
            score2View.setTextSize(13f);
            score2View.setTypeface(Typeface.DEFAULT_BOLD);
            score2View.setTextColor(score2Color);
            score2View.setGravity(Gravity.CENTER_HORIZONTAL);
            score2View.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            root.addView(score2View);

            return root;
        }

        private int parseIntSafe(String value) {
            if (isEmpty(value)) {
                return 0;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (Exception ignored) {
                return 0;
            }
        }

        private int resolveWarPartyColor(int score1, int score2, boolean aggressor) {
            if (score1 > score2) {
                return aggressor ? 0xFF2E7D32 : 0xFFC62828;
            }
            if (score1 < score2) {
                return aggressor ? 0xFFC62828 : 0xFF2E7D32;
            }
            return 0xFFFFFFFF;
        }

        private View createDividerRow() {
            TableRow dividerRow = new TableRow(requireContext());
            TextView divider = new TextView(requireContext());
            divider.setBackgroundColor(0x337E57C2);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1)
            );
            lp.span = 3;
            divider.setLayoutParams(lp);
            dividerRow.addView(divider);
            return dividerRow;
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
