package ru.neverlands.abclient.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.model.LezBotsClass;
import ru.neverlands.abclient.model.LezBotsClassCollection;
import ru.neverlands.abclient.model.LezBotsGroup;
import ru.neverlands.abclient.model.LezSayType;
import ru.neverlands.abclient.model.LezSpell;
import ru.neverlands.abclient.model.LezSpellCollection;
import ru.neverlands.abclient.model.UserConfig;
import ru.neverlands.abclient.utils.AppVars;

/**
 * Диалог настроек автобоя.
 * Аналог FormSettingsAb.cs из ПК версии.
 * Содержит 4 вкладки: Общие, Группы, Ротация, Останов.
 */
public class AutoBoiSettingsFragment extends DialogFragment {

    private static final String TAG = "AutoBoiSettings";

    // Текущая выбранная группа (для вкладок Ротация и Останов)
    private int selectedGroupIndex = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_autoboi_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null) {
            getDialog().setTitle("Настройки авто-боя");
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TabLayout tabLayout = view.findViewById(R.id.tabLayout);
        ViewPager2 viewPager = view.findViewById(R.id.viewPager);

        SettingsPagerAdapter adapter = new SettingsPagerAdapter(requireActivity());
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Общие"); break;
                case 1: tab.setText("Группы"); break;
                case 2: tab.setText("Ротация"); break;
                case 3: tab.setText("Останов"); break;
            }
        }).attach();

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            saveAllSettings(adapter);
            dismiss();
        });
    }

    private void saveAllSettings(SettingsPagerAdapter adapter) {
        UserConfig profile = AppVars.Profile;
        if (profile == null) return;

        // Сохраняем вкладку 1 (Общие)
        GeneralTabFragment general = adapter.getGeneral();
        if (general != null) general.saveSettings(profile);

        // Сохраняем текущую группу (вкладки 3 и 4)
        RotationTabFragment rotation = adapter.getRotation();
        if (rotation != null && profile.LezGroups != null
                && selectedGroupIndex < profile.LezGroups.size()) {
            rotation.saveGroup(profile.LezGroups.get(selectedGroupIndex));
        }

        StopTabFragment stop = adapter.getStop();
        if (stop != null && profile.LezGroups != null
                && selectedGroupIndex < profile.LezGroups.size()) {
            stop.saveGroup(profile.LezGroups.get(selectedGroupIndex));
        }

        // Сохраняем профиль на диск
        if (getContext() != null) {
            profile.save(getContext());
        }
    }

    // ─────────────────────────────── Pager Adapter ──────────────────────────────

    private class SettingsPagerAdapter extends androidx.viewpager2.adapter.FragmentStateAdapter {
        private GeneralTabFragment general;
        private GroupsTabFragment groups;
        private RotationTabFragment rotation;
        private StopTabFragment stop;

        SettingsPagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1:
                    groups = new GroupsTabFragment();
                    return groups;
                case 2:
                    rotation = new RotationTabFragment();
                    return rotation;
                case 3:
                    stop = new StopTabFragment();
                    return stop;
                default:
                    general = new GeneralTabFragment();
                    return general;
            }
        }

        @Override
        public int getItemCount() { return 4; }

        GeneralTabFragment getGeneral() { return general; }
        RotationTabFragment getRotation() { return rotation; }
        StopTabFragment getStop() { return stop; }
    }

    // ─────────────────────────── Вкладка 1: Общие ───────────────────────────────

    public static class GeneralTabFragment extends Fragment {
        private CheckBox checkDoAutoboi, checkDoWaitHp, checkDoWaitMa,
                checkDoDrinkHp, checkDoDrinkMa, checkDoWinTimeout;
        private SeekBar seekWaitHp, seekWaitMa, seekDrinkHp, seekDrinkMa;
        private TextView tvWaitHp, tvWaitMa, tvDrinkHp, tvDrinkMa;
        private RadioGroup radioGroupSay;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.tab_autoboi_general, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
            checkDoAutoboi = v.findViewById(R.id.checkDoAutoboi);
            checkDoWaitHp = v.findViewById(R.id.checkDoWaitHp);
            checkDoWaitMa = v.findViewById(R.id.checkDoWaitMa);
            checkDoDrinkHp = v.findViewById(R.id.checkDoDrinkHp);
            checkDoDrinkMa = v.findViewById(R.id.checkDoDrinkMa);
            checkDoWinTimeout = v.findViewById(R.id.checkDoWinTimeout);
            seekWaitHp = v.findViewById(R.id.seekWaitHp);
            seekWaitMa = v.findViewById(R.id.seekWaitMa);
            seekDrinkHp = v.findViewById(R.id.seekDrinkHp);
            seekDrinkMa = v.findViewById(R.id.seekDrinkMa);
            tvWaitHp = v.findViewById(R.id.tvWaitHp);
            tvWaitMa = v.findViewById(R.id.tvWaitMa);
            tvDrinkHp = v.findViewById(R.id.tvDrinkHp);
            tvDrinkMa = v.findViewById(R.id.tvDrinkMa);
            radioGroupSay = v.findViewById(R.id.radioGroupSay);

            setupSeekBar(seekWaitHp, tvWaitHp, "%");
            setupSeekBar(seekWaitMa, tvWaitMa, "%");
            setupSeekBar(seekDrinkHp, tvDrinkHp, "%");
            setupSeekBar(seekDrinkMa, tvDrinkMa, "%");

            loadSettings();
        }

        private void setupSeekBar(SeekBar bar, TextView label, String suffix) {
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                    label.setText(p + suffix);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        private void loadSettings() {
            UserConfig p = AppVars.Profile;
            if (p == null) return;
            checkDoAutoboi.setChecked(p.LezDoAutoboi);
            checkDoWaitHp.setChecked(p.LezDoWaitHp);
            checkDoWaitMa.setChecked(p.LezDoWaitMa);
            seekWaitHp.setProgress(p.LezWaitHp);
            tvWaitHp.setText(p.LezWaitHp + "%");
            seekWaitMa.setProgress(p.LezWaitMa);
            tvWaitMa.setText(p.LezWaitMa + "%");
            checkDoDrinkHp.setChecked(p.LezDoDrinkHp);
            checkDoDrinkMa.setChecked(p.LezDoDrinkMa);
            seekDrinkHp.setProgress(p.LezDrinkHp);
            tvDrinkHp.setText(p.LezDrinkHp + "%");
            seekDrinkMa.setProgress(p.LezDrinkMa);
            tvDrinkMa.setText(p.LezDrinkMa + "%");
            checkDoWinTimeout.setChecked(p.LezDoWinTimeout);

            // Радиокнопки LezSay
            int sayId = R.id.radioSayNo;
            if (p.LezSay == LezSayType.Chat) sayId = R.id.radioSayChat;
            else if (p.LezSay == LezSayType.Clan) sayId = R.id.radioSayClan;
            else if (p.LezSay == LezSayType.Pair) sayId = R.id.radioSayPair;
            radioGroupSay.check(sayId);
        }

        void saveSettings(UserConfig p) {
            p.LezDoAutoboi = checkDoAutoboi.isChecked();
            p.LezDoWaitHp = checkDoWaitHp.isChecked();
            p.LezDoWaitMa = checkDoWaitMa.isChecked();
            p.LezWaitHp = seekWaitHp.getProgress();
            p.LezWaitMa = seekWaitMa.getProgress();
            p.LezDoDrinkHp = checkDoDrinkHp.isChecked();
            p.LezDoDrinkMa = checkDoDrinkMa.isChecked();
            p.LezDrinkHp = seekDrinkHp.getProgress();
            p.LezDrinkMa = seekDrinkMa.getProgress();
            p.LezDoWinTimeout = checkDoWinTimeout.isChecked();

            int checkedId = radioGroupSay.getCheckedRadioButtonId();
            if (checkedId == R.id.radioSayChat) p.LezSay = LezSayType.Chat;
            else if (checkedId == R.id.radioSayClan) p.LezSay = LezSayType.Clan;
            else if (checkedId == R.id.radioSayPair) p.LezSay = LezSayType.Pair;
            else p.LezSay = LezSayType.No;
        }
    }

    // ─────────────────────────── Вкладка 2: Группы ──────────────────────────────

    public static class GroupsTabFragment extends Fragment {
        private RecyclerView recyclerGroups;
        private Spinner spinnerBotClass;
        private SeekBar seekNewGroupLevel;
        private TextView tvNewGroupLevel;
        private GroupsAdapter groupsAdapter;
        private int selectedGroupIdx = 0;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.tab_autoboi_groups, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
            recyclerGroups = v.findViewById(R.id.recyclerGroups);
            spinnerBotClass = v.findViewById(R.id.spinnerBotClass);
            seekNewGroupLevel = v.findViewById(R.id.seekNewGroupLevel);
            tvNewGroupLevel = v.findViewById(R.id.tvNewGroupLevel);

            seekNewGroupLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                    tvNewGroupLevel.setText(String.valueOf(p));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });

            // Spinner классов противников
            List<LezBotsClass> classes = LezBotsClassCollection.listForComboBox();
            List<String> classNames = new ArrayList<>();
            for (LezBotsClass c : classes) classNames.add(c.name);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, classNames);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerBotClass.setAdapter(spinnerAdapter);

            // RecyclerView групп
            UserConfig p = AppVars.Profile;
            List<LezBotsGroup> groups = p != null ? p.LezGroups : new ArrayList<>();
            groupsAdapter = new GroupsAdapter(groups, idx -> selectedGroupIdx = idx);
            recyclerGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerGroups.setAdapter(groupsAdapter);

            v.findViewById(R.id.btnCreateGroup).setOnClickListener(btn -> createGroup(classes));
            v.findViewById(R.id.btnDeleteGroup).setOnClickListener(btn -> deleteGroup());
        }

        private void createGroup(List<LezBotsClass> classes) {
            UserConfig p = AppVars.Profile;
            if (p == null) return;
            int classIdx = spinnerBotClass.getSelectedItemPosition();
            if (classIdx < 0 || classIdx >= classes.size()) return;
            int classId = classes.get(classIdx).id;
            int level = seekNewGroupLevel.getProgress();
            // Генерируем новый уникальный Id
            int maxId = 0;
            for (LezBotsGroup g : p.LezGroups) if (g.Id > maxId) maxId = g.Id;
            LezBotsGroup newGroup = new LezBotsGroup(maxId + 1, level);
            newGroup.Change(classId, level);
            p.LezGroups.add(newGroup);
            groupsAdapter.notifyItemInserted(p.LezGroups.size() - 1);
        }

        private void deleteGroup() {
            UserConfig p = AppVars.Profile;
            if (p == null || p.LezGroups.isEmpty()) return;
            if (selectedGroupIdx < 0 || selectedGroupIdx >= p.LezGroups.size()) return;
            LezBotsGroup g = p.LezGroups.get(selectedGroupIdx);
            // Нельзя удалить группу "Все" (Id=1, MinimalLevel=0)
            if (g.Id == 1 && g.MinimalLevel == 0) {
                android.widget.Toast.makeText(requireContext(),
                        "Нельзя удалить группу «Все»", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            p.LezGroups.remove(selectedGroupIdx);
            groupsAdapter.notifyItemRemoved(selectedGroupIdx);
            if (selectedGroupIdx >= p.LezGroups.size()) selectedGroupIdx = p.LezGroups.size() - 1;
        }

        // RecyclerView адаптер для групп
        static class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.VH> {
            private final List<LezBotsGroup> groups;
            private int selected = 0;
            private final OnGroupSelected listener;

            interface OnGroupSelected { void onSelected(int idx); }

            GroupsAdapter(List<LezBotsGroup> groups, OnGroupSelected listener) {
                this.groups = groups;
                this.listener = listener;
            }

            @NonNull @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_lez_group, parent, false);
                return new VH(v);
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int pos) {
                LezBotsGroup g = groups.get(pos);
                holder.tvName.setText(g.toString());
                holder.tvSelected.setVisibility(pos == selected ? View.VISIBLE : View.GONE);
                holder.itemView.setOnClickListener(v -> {
                    int old = selected;
                    selected = holder.getAdapterPosition();
                    notifyItemChanged(old);
                    notifyItemChanged(selected);
                    listener.onSelected(selected);
                });
            }

            @Override public int getItemCount() { return groups.size(); }

            static class VH extends RecyclerView.ViewHolder {
                TextView tvName, tvSelected;
                VH(@NonNull View v) {
                    super(v);
                    tvName = v.findViewById(R.id.tvGroupName);
                    tvSelected = v.findViewById(R.id.tvGroupSelected);
                }
            }
        }
    }

    // ─────────────────────────── Вкладка 3: Ротация ─────────────────────────────

    public static class RotationTabFragment extends Fragment {
        private CheckBox checkDoRestoreHp, checkDoRestoreMa, checkDoAbilBlocks, checkDoAbilHits,
                checkDoMagHits, checkDoMagBlocks, checkDoHits, checkDoBlocks, checkDoMiscAbils;
        private SeekBar seekRestoreHp, seekRestoreMa, seekMagHits;
        private TextView tvRestoreHp, tvRestoreMa, tvMagHits;
        private SpellListAdapter spellsHitsAdapter, spellsBlocksAdapter,
                spellsRestoreHpAdapter, spellsRestoreMaAdapter, spellsMiscAdapter;

        @Nullable @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.tab_autoboi_rotation, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
            checkDoRestoreHp = v.findViewById(R.id.checkDoRestoreHp);
            checkDoRestoreMa = v.findViewById(R.id.checkDoRestoreMa);
            checkDoAbilBlocks = v.findViewById(R.id.checkDoAbilBlocks);
            checkDoAbilHits = v.findViewById(R.id.checkDoAbilHits);
            checkDoMagHits = v.findViewById(R.id.checkDoMagHits);
            checkDoMagBlocks = v.findViewById(R.id.checkDoMagBlocks);
            checkDoHits = v.findViewById(R.id.checkDoHits);
            checkDoBlocks = v.findViewById(R.id.checkDoBlocks);
            checkDoMiscAbils = v.findViewById(R.id.checkDoMiscAbils);
            seekRestoreHp = v.findViewById(R.id.seekRestoreHp);
            seekRestoreMa = v.findViewById(R.id.seekRestoreMa);
            seekMagHits = v.findViewById(R.id.seekMagHits);
            tvRestoreHp = v.findViewById(R.id.tvRestoreHp);
            tvRestoreMa = v.findViewById(R.id.tvRestoreMa);
            tvMagHits = v.findViewById(R.id.tvMagHits);

            setupSeekBar(seekRestoreHp, tvRestoreHp, "%");
            setupSeekBar(seekRestoreMa, tvRestoreMa, "%");
            seekMagHits.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) { tvMagHits.setText(String.valueOf(p)); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });

            v.findViewById(R.id.btnFullHp).setOnClickListener(btn -> { seekRestoreHp.setProgress(100); tvRestoreHp.setText("100%"); });
            v.findViewById(R.id.btnFullMa).setOnClickListener(btn -> { seekRestoreMa.setProgress(100); tvRestoreMa.setText("100%"); });

            // Инициализация списков заклинаний
            LezSpellCollection.init(requireContext());
            spellsHitsAdapter = setupSpellList(v, R.id.listSpellsHits, LezSpellCollection.Hits, null);
            spellsBlocksAdapter = setupSpellList(v, R.id.listSpellsBlocks, LezSpellCollection.Blocks, null);
            spellsRestoreHpAdapter = setupSpellList(v, R.id.listSpellsRestoreHp, LezSpellCollection.RestoreHp, null);
            spellsRestoreMaAdapter = setupSpellList(v, R.id.listSpellsRestoreMa, LezSpellCollection.RestoreMa, null);
            spellsMiscAdapter = setupSpellList(v, R.id.listSpellsMisc, LezSpellCollection.Misc, null);

            loadGroup();
        }

        private SpellListAdapter setupSpellList(View root, int recyclerViewId, int[] spellIds, int[] checked) {
            RecyclerView rv = root.findViewById(recyclerViewId);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setNestedScrollingEnabled(false);
            SpellListAdapter adapter = new SpellListAdapter(spellIds, checked);
            rv.setAdapter(adapter);
            return adapter;
        }

        private void setupSeekBar(SeekBar bar, TextView label, String suffix) {
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) { label.setText(p + suffix); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        private void loadGroup() {
            UserConfig p = AppVars.Profile;
            if (p == null || p.LezGroups == null || p.LezGroups.isEmpty()) return;
            LezBotsGroup g = p.LezGroups.get(0);
            checkDoRestoreHp.setChecked(g.DoRestoreHp);
            checkDoRestoreMa.setChecked(g.DoRestoreMa);
            seekRestoreHp.setProgress(g.RestoreHp);
            tvRestoreHp.setText(g.RestoreHp + "%");
            seekRestoreMa.setProgress(g.RestoreMa);
            tvRestoreMa.setText(g.RestoreMa + "%");
            checkDoAbilBlocks.setChecked(g.DoAbilBlocks);
            checkDoAbilHits.setChecked(g.DoAbilHits);
            checkDoMagHits.setChecked(g.DoMagHits);
            seekMagHits.setProgress(Math.min(g.MagHits, 1000));
            tvMagHits.setText(String.valueOf(g.MagHits));
            checkDoMagBlocks.setChecked(g.DoMagBlocks);
            checkDoHits.setChecked(g.DoHits);
            checkDoBlocks.setChecked(g.DoBlocks);
            checkDoMiscAbils.setChecked(g.DoMiscAbils);
            if (spellsHitsAdapter != null) spellsHitsAdapter.setChecked(g.SpellsHits);
            if (spellsBlocksAdapter != null) spellsBlocksAdapter.setChecked(g.SpellsBlocks);
            if (spellsRestoreHpAdapter != null) spellsRestoreHpAdapter.setChecked(g.SpellsRestoreHp);
            if (spellsRestoreMaAdapter != null) spellsRestoreMaAdapter.setChecked(g.SpellsRestoreMa);
            if (spellsMiscAdapter != null) spellsMiscAdapter.setChecked(g.SpellsMisc);
        }

        void saveGroup(LezBotsGroup g) {
            g.DoRestoreHp = checkDoRestoreHp.isChecked();
            g.DoRestoreMa = checkDoRestoreMa.isChecked();
            g.RestoreHp = seekRestoreHp.getProgress();
            g.RestoreMa = seekRestoreMa.getProgress();
            g.DoAbilBlocks = checkDoAbilBlocks.isChecked();
            g.DoAbilHits = checkDoAbilHits.isChecked();
            g.DoMagHits = checkDoMagHits.isChecked();
            g.MagHits = seekMagHits.getProgress();
            g.DoMagBlocks = checkDoMagBlocks.isChecked();
            g.DoHits = checkDoHits.isChecked();
            g.DoBlocks = checkDoBlocks.isChecked();
            g.DoMiscAbils = checkDoMiscAbils.isChecked();
            if (spellsHitsAdapter != null) g.SpellsHits = spellsHitsAdapter.getChecked();
            if (spellsBlocksAdapter != null) g.SpellsBlocks = spellsBlocksAdapter.getChecked();
            if (spellsRestoreHpAdapter != null) g.SpellsRestoreHp = spellsRestoreHpAdapter.getChecked();
            if (spellsRestoreMaAdapter != null) g.SpellsRestoreMa = spellsRestoreMaAdapter.getChecked();
            if (spellsMiscAdapter != null) g.SpellsMisc = spellsMiscAdapter.getChecked();
        }
    }

    // ─────────────────────────── Вкладка 4: Останов ─────────────────────────────

    public static class StopTabFragment extends Fragment {
        private CheckBox checkDoStopNow, checkDoStopLowHp, checkDoStopLowMa, checkDoExit, checkDoExitRisky;
        private SeekBar seekStopLowHp, seekStopLowMa;
        private TextView tvStopLowHp, tvStopLowMa;

        @Nullable @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.tab_autoboi_stop, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
            checkDoStopNow = v.findViewById(R.id.checkDoStopNow);
            checkDoStopLowHp = v.findViewById(R.id.checkDoStopLowHp);
            checkDoStopLowMa = v.findViewById(R.id.checkDoStopLowMa);
            checkDoExit = v.findViewById(R.id.checkDoExit);
            checkDoExitRisky = v.findViewById(R.id.checkDoExitRisky);
            seekStopLowHp = v.findViewById(R.id.seekStopLowHp);
            seekStopLowMa = v.findViewById(R.id.seekStopLowMa);
            tvStopLowHp = v.findViewById(R.id.tvStopLowHp);
            tvStopLowMa = v.findViewById(R.id.tvStopLowMa);

            setupSeekBar(seekStopLowHp, tvStopLowHp);
            setupSeekBar(seekStopLowMa, tvStopLowMa);

            loadGroup();
        }

        private void setupSeekBar(SeekBar bar, TextView label) {
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) { label.setText(p + "%"); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        private void loadGroup() {
            UserConfig p = AppVars.Profile;
            if (p == null || p.LezGroups == null || p.LezGroups.isEmpty()) return;
            LezBotsGroup g = p.LezGroups.get(0);
            checkDoStopNow.setChecked(g.DoStopNow);
            checkDoStopLowHp.setChecked(g.DoStopLowHp);
            seekStopLowHp.setProgress(g.StopLowHp);
            tvStopLowHp.setText(g.StopLowHp + "%");
            checkDoStopLowMa.setChecked(g.DoStopLowMa);
            seekStopLowMa.setProgress(g.StopLowMa);
            tvStopLowMa.setText(g.StopLowMa + "%");
            checkDoExit.setChecked(g.DoExit);
            checkDoExitRisky.setChecked(g.DoExitRisky);
        }

        void saveGroup(LezBotsGroup g) {
            g.DoStopNow = checkDoStopNow.isChecked();
            g.DoStopLowHp = checkDoStopLowHp.isChecked();
            g.StopLowHp = seekStopLowHp.getProgress();
            g.DoStopLowMa = checkDoStopLowMa.isChecked();
            g.StopLowMa = seekStopLowMa.getProgress();
            g.DoExit = checkDoExit.isChecked();
            g.DoExitRisky = checkDoExitRisky.isChecked();
        }
    }

    // ─────────────────────────── Адаптер заклинаний ─────────────────────────────

    /**
     * RecyclerView адаптер для списка заклинаний с чекбоксами.
     * Аналог listSpellsHits/Blocks/etc. в FormSettingsAb.cs.
     */
    static class SpellListAdapter extends RecyclerView.Adapter<SpellListAdapter.VH> {
        private final int[] spellIds;
        private final boolean[] checked;

        SpellListAdapter(int[] spellIds, int[] checkedIds) {
            this.spellIds = spellIds;
            this.checked = new boolean[spellIds.length];
            if (checkedIds != null) setChecked(checkedIds);
        }

        void setChecked(int[] checkedIds) {
            if (checkedIds == null) return;
            java.util.Set<Integer> set = new java.util.HashSet<>();
            for (int id : checkedIds) set.add(id);
            for (int i = 0; i < spellIds.length; i++) {
                checked[i] = set.contains(spellIds[i]);
            }
            notifyDataSetChanged();
        }

        int[] getChecked() {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < spellIds.length; i++) {
                if (checked[i]) result.add(spellIds[i]);
            }
            int[] arr = new int[result.size()];
            for (int i = 0; i < result.size(); i++) arr[i] = result.get(i);
            return arr;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lez_spell, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            int id = spellIds[pos];
            LezSpell spell = LezSpellCollection.Spells.get(id);
            String name = spell != null ? spell.Name : "Заклинание #" + id;

            // Поиск index в массиве Hits/Blocks/etc. для Od и Mana
            int odVal = 0, manaVal = 0;
            int[] allIds = LezSpellCollection.Hits; // placeholder; Od indexed by position
            // Попытка найти OD/Mana из коллекции (по позиции в полных массивах)
            // В ПК версии Od и PosMana индексируются по общему порядку спеллов в коллекции
            // Здесь упрощённо берём из Spells если есть
            holder.tvName.setText(name);
            holder.tvInfo.setText("ID:" + id);
            holder.checkSpell.setChecked(checked[pos]);
            holder.checkSpell.setOnCheckedChangeListener((btn, isChecked) -> {
                int p = holder.getAdapterPosition();
                if (p >= 0 && p < SpellListAdapter.this.checked.length) {
                    SpellListAdapter.this.checked[p] = isChecked;
                }
            });
        }

        @Override public int getItemCount() { return spellIds.length; }

        static class VH extends RecyclerView.ViewHolder {
            CheckBox checkSpell;
            TextView tvName, tvInfo;
            VH(@NonNull View v) {
                super(v);
                checkSpell = v.findViewById(R.id.checkSpell);
                tvName = v.findViewById(R.id.tvSpellName);
                tvInfo = v.findViewById(R.id.tvSpellInfo);
            }
        }
    }
}
