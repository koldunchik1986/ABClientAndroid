package ru.neverlands.abclient.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.text.InputType;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private interface OnGroupIndexChangedListener {
        void onGroupIndexChanged(int index);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Dialog_Alert);
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

    /**
     * Централизованно сохраняет все вкладки настроек авто-боя в текущий профиль.
     *
     * Последовательность:
     * 1) Сохраняет вкладку "Общие" (флаги автоповедения и пороги).
     * 2) Сохраняет выбранную группу из вкладок "Ротация" и "Останов".
     * 3) Выполняет legacy-fallback для задержки удара:
     *    текущее `group.HitDelaySec` дублируется в `profile.LezHitDelaySec`,
     *    чтобы старые части парсера/профиля не теряли значение.
     * 4) Сортирует группы по приоритету, синхронизирует runtime-флаги Fury и пишет профиль на диск.
     *
     * Зависимости:
     * - `SettingsPagerAdapter` (`getGeneral/getRotation/getStop`),
     * - `UserConfig` (`LezGroups`, `LezDoFury`, `LezHitDelaySec`, `save(...)`),
     * - `AppVars` (`DoFury`, `AutoFuryCheckScroll`, `AutoFuryArmedScroll`, `AutoFuryHand`, `AutoFuryHandD`),
     * - `Collections.sort(...)` для приоритетного порядка групп (как в C# логике выбора первой подходящей группы).
     */
    private void saveAllSettings(SettingsPagerAdapter adapter) {
        UserConfig profile = AppVars.Profile;
        if (profile == null) return;

        // Сохраняем вкладку 1 (Общие)
        GeneralTabFragment general = adapter.getGeneral();
        if (general != null) general.saveSettings(profile);

        // Сохраняем текущую группу (вкладки 3 и 4)
        RotationTabFragment rotation = adapter.getRotation();
        if (rotation != null && profile.LezGroups != null
                && selectedGroupIndex >= 0
                && selectedGroupIndex < profile.LezGroups.size()) {
            rotation.saveGroup(profile.LezGroups.get(selectedGroupIndex));
            // Legacy-fallback для старых профилей/парсеров:
            // дублируем текущее групповое значение в глобальный autoboi@hitDelaySec.
            profile.LezHitDelaySec = Math.max(0, profile.LezGroups.get(selectedGroupIndex).HitDelaySec);
        }

        StopTabFragment stop = adapter.getStop();
        if (stop != null && profile.LezGroups != null
                && selectedGroupIndex >= 0
                && selectedGroupIndex < profile.LezGroups.size()) {
            stop.saveGroup(profile.LezGroups.get(selectedGroupIndex));
        }

        // Сохраняем профиль на диск
        if (getContext() != null) {
            if (profile.LezGroups != null) {
                Collections.sort(profile.LezGroups);
            }
            profile.LezDoFury = profile.hasAnyLezFuryGroup();
            AppVars.DoFury = profile.LezDoFury;
            if (AppVars.DoFury) {
                AppVars.AutoFuryCheckScroll = true;
                AppVars.AutoFuryArmedScroll = false;
            } else {
                AppVars.AutoFuryCheckScroll = false;
                AppVars.AutoFuryArmedScroll = false;
                AppVars.AutoFuryHand = "";
                AppVars.AutoFuryHandD = "";
            }
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
                    groups.setSelectedGroupIndex(selectedGroupIndex);
                    groups.setOnGroupIndexChangedListener(index -> {
                        selectedGroupIndex = index;
                        if (rotation != null) {
                            rotation.setSelectedGroupIndex(index);
                        }
                        if (stop != null) {
                            stop.setSelectedGroupIndex(index);
                        }
                    });
                    return groups;
                case 2:
                    rotation = new RotationTabFragment();
                    rotation.setSelectedGroupIndex(selectedGroupIndex);
                    return rotation;
                case 3:
                    stop = new StopTabFragment();
                    stop.setSelectedGroupIndex(selectedGroupIndex);
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
        }

        void saveSettings(UserConfig p) {
            // Важно для ViewPager2: если вкладка ещё не открывалась пользователем,
            // onViewCreated() мог не выполниться и поля останутся null.
            // В этом случае просто пропускаем сохранение этой вкладки без краша.
            if (checkDoAutoboi == null
                    || checkDoWaitHp == null || checkDoWaitMa == null
                    || checkDoDrinkHp == null || checkDoDrinkMa == null || checkDoWinTimeout == null
                    || seekWaitHp == null || seekWaitMa == null || seekDrinkHp == null || seekDrinkMa == null) {
                return;
            }
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
        }
    }

    // --------------------------- Вкладка 2: Группы ---------------------------

    public static class GroupsTabFragment extends Fragment {
        private RecyclerView recyclerGroups;
        private Spinner spinnerBotClass;
        private SeekBar seekNewGroupLevel;
        private TextView tvNewGroupLevel;
        private GroupsAdapter groupsAdapter;
        private int selectedGroupIdx = 0;
        private OnGroupIndexChangedListener groupIndexChangedListener;

        void setOnGroupIndexChangedListener(OnGroupIndexChangedListener listener) {
            groupIndexChangedListener = listener;
        }

        void setSelectedGroupIndex(int index) {
            selectedGroupIdx = Math.max(0, index);
            if (groupsAdapter != null) {
                groupsAdapter.setSelected(selectedGroupIdx);
            }
        }

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
            if (groups != null) {
                Collections.sort(groups);
            }
            groupsAdapter = new GroupsAdapter(groups, idx -> {
                selectedGroupIdx = idx;
                notifyGroupIndexChanged();
            });
            recyclerGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerGroups.setAdapter(groupsAdapter);

            if (groups.isEmpty()) {
                selectedGroupIdx = 0;
            } else if (selectedGroupIdx >= groups.size()) {
                selectedGroupIdx = groups.size() - 1;
            }
            groupsAdapter.setSelected(selectedGroupIdx);
            notifyGroupIndexChanged();

            v.findViewById(R.id.btnCreateGroup).setOnClickListener(btn -> createGroup(classes));
            v.findViewById(R.id.btnDeleteGroup).setOnClickListener(btn -> deleteGroup());
        }

        private void notifyGroupIndexChanged() {
            if (groupIndexChangedListener != null) {
                groupIndexChangedListener.onGroupIndexChanged(selectedGroupIdx);
            }
        }

        private void createGroup(List<LezBotsClass> classes) {
            UserConfig p = AppVars.Profile;
            if (p == null) return;
            int classIdx = spinnerBotClass.getSelectedItemPosition();
            if (classIdx < 0 || classIdx >= classes.size()) return;
            int classId = classes.get(classIdx).id;
            int level = seekNewGroupLevel.getProgress();
            // C# parity: Id группы — это Id класса врага, а не автоинкремент.
            // Сортировка и проверка дубликата выполняются как в FormSettingsAb.buttonCreateGroup_Click.
            LezBotsGroup newGroup = new LezBotsGroup(1, 0);
            newGroup.Change(classId, level);
            int insertIndex = -1;
            for (int i = 0; i < p.LezGroups.size(); i++) {
                LezBotsGroup cursorGroup = p.LezGroups.get(i);
                int result = newGroup.compareTo(cursorGroup);
                if (result == -1) {
                    p.LezGroups.add(i, newGroup);
                    insertIndex = i;
                    break;
                }
                if (result == 0) {
                    android.widget.Toast.makeText(requireContext(),
                            "Такая группа уже существует", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if (insertIndex < 0) {
                p.LezGroups.add(newGroup);
                insertIndex = p.LezGroups.size() - 1;
            }
            groupsAdapter.notifyDataSetChanged();
            selectedGroupIdx = insertIndex;
            groupsAdapter.setSelected(selectedGroupIdx);
            notifyGroupIndexChanged();
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
            if (selectedGroupIdx < 0) selectedGroupIdx = 0;
            groupsAdapter.setSelected(selectedGroupIdx);
            notifyGroupIndexChanged();
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
                // Дополнительная визуализация выбранной группы: полупрозрачная фиолетовая подложка строки.
                // Зависимость: цвет взят из ресурсов темы, чтобы совпадать с основной палитрой приложения.
                int selectedBg = ContextCompat.getColor(holder.itemView.getContext(), R.color.ab_autoboi_group_selected_bg);
                holder.itemView.setBackgroundColor(pos == selected ? selectedBg : android.graphics.Color.TRANSPARENT);
                holder.itemView.setOnClickListener(v -> {
                    int newIndex = holder.getAdapterPosition();
                    if (newIndex == RecyclerView.NO_POSITION) return;
                    setSelected(newIndex);
                    listener.onSelected(selected);
                });
            }

            @Override public int getItemCount() { return groups.size(); }

            void setSelected(int index) {
                if (groups.isEmpty()) {
                    selected = 0;
                    notifyDataSetChanged();
                    return;
                }
                int bounded = Math.max(0, Math.min(index, groups.size() - 1));
                if (selected == bounded) {
                    notifyDataSetChanged();
                    return;
                }
                int old = selected;
                selected = bounded;
                if (old >= 0 && old < groups.size()) {
                    notifyItemChanged(old);
                }
                notifyItemChanged(selected);
            }

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
        private int selectedGroupIdx = 0;
        private CheckBox checkDoFury, checkDoRestoreHp, checkDoRestoreMa, checkDoAbilBlocks, checkDoAbilHits,
                checkDoMagHits, checkDoMagBlocks, checkDoHits, checkDoBlocks, checkDoMiscAbils;
        private SeekBar seekRestoreHp, seekRestoreMa, seekMagHits;
        private TextView tvRestoreHp, tvRestoreMa, tvMagHits;
        private TextView tvGroupHitDelaySecValue;
        private Button btnSetGroupHitDelaySec;
        private int pendingGroupHitDelaySec = 0;
        // Локальные списки ID для вкладки "Ротация".
        // В базовом сценарии равны LezSpellCollection.Hits/Blocks/... (как в C#),
        // но для Misc могут расширяться fallback-элементами с сервера (новые ID).
        private int[] hitsSpellIds;
        private int[] blocksSpellIds;
        private int[] restoreHpSpellIds;
        private int[] restoreMaSpellIds;
        private int[] miscSpellIds;
        private SpellListAdapter spellsHitsAdapter, spellsBlocksAdapter,
                spellsRestoreHpAdapter, spellsRestoreMaAdapter, spellsMiscAdapter;

        @Nullable @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.tab_autoboi_rotation, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
            checkDoFury = v.findViewById(R.id.checkDoFury);
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
            tvGroupHitDelaySecValue = v.findViewById(R.id.tvGroupHitDelaySecValue);
            btnSetGroupHitDelaySec = v.findViewById(R.id.btnSetGroupHitDelaySec);

            setupSeekBar(seekRestoreHp, tvRestoreHp, "%");
            setupSeekBar(seekRestoreMa, tvRestoreMa, "%");
            seekMagHits.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) { tvMagHits.setText(String.valueOf(p)); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });

            v.findViewById(R.id.btnFullHp).setOnClickListener(btn -> { seekRestoreHp.setProgress(100); tvRestoreHp.setText("100%"); });
            v.findViewById(R.id.btnFullMa).setOnClickListener(btn -> { seekRestoreMa.setProgress(100); tvRestoreMa.setText("100%"); });
            if (btnSetGroupHitDelaySec != null) {
                btnSetGroupHitDelaySec.setOnClickListener(btn -> showGroupHitDelayInputDialog());
            }

            // Инициализация списков заклинаний
            LezSpellCollection.init(requireContext());
            resolveRotationSpellIdsWithServerFallback();
            spellsHitsAdapter = setupSpellList(v, R.id.listSpellsHits, hitsSpellIds, null);
            spellsBlocksAdapter = setupSpellList(v, R.id.listSpellsBlocks, blocksSpellIds, null);
            spellsRestoreHpAdapter = setupSpellList(v, R.id.listSpellsRestoreHp, restoreHpSpellIds, null);
            spellsRestoreMaAdapter = setupSpellList(v, R.id.listSpellsRestoreMa, restoreMaSpellIds, null);
            spellsMiscAdapter = setupSpellList(v, R.id.listSpellsMisc, miscSpellIds, null);
            setupCollapsibleSpellCategories(v);

            loadGroup();
        }

        /**
         * Формирует наборы ID для вкладки "Ротация".
         *
         * Базовая логика — строго как в C# LezSpellCollection (Hits/Blocks/RestoreHp/RestoreMa/Misc).
         * Дополнение для Android:
         * - если в `spells.txt` появились новые ID (обычно после обновления сервера),
         *   они автоматически добавляются в категорию `Misc`, чтобы были доступны в UI
         *   без немедленного обновления APK.
         *
         * Важно:
         * - добавляются только ID > max(legacy C# categories), чтобы не "раздувать" список
         *   историческими небоевыми/служебными spell-кодами;
         * - приоритет категорий C# не меняется.
         *
         * Зависимости:
         * - LezSpellCollection.{Hits, Blocks, RestoreHp, RestoreMa, Misc, Spells}
         * - порядок Spells из TreeMap (возрастающий ID) для стабильного отображения.
         */
        private void resolveRotationSpellIdsWithServerFallback() {
            hitsSpellIds = LezSpellCollection.Hits.clone();
            blocksSpellIds = LezSpellCollection.Blocks.clone();
            restoreHpSpellIds = LezSpellCollection.RestoreHp.clone();
            restoreMaSpellIds = LezSpellCollection.RestoreMa.clone();
            miscSpellIds = buildMiscWithServerFallback(LezSpellCollection.Misc);
        }

        /**
         * Возвращает категорию Misc с fallback-новыми ID из серверного списка spell-имен.
         * Новыми считаются ID, превышающие максимальный ID из базовых C#-категорий.
         */
        private int[] buildMiscWithServerFallback(int[] baseMisc) {
            int legacyMaxId = findLegacyMaxSpellId();
            Set<Integer> knownCategoryIds = new LinkedHashSet<>();
            addAll(knownCategoryIds, LezSpellCollection.Hits);
            addAll(knownCategoryIds, LezSpellCollection.Blocks);
            addAll(knownCategoryIds, LezSpellCollection.RestoreHp);
            addAll(knownCategoryIds, LezSpellCollection.RestoreMa);
            addAll(knownCategoryIds, LezSpellCollection.Misc);

            List<Integer> merged = new ArrayList<>();
            for (int id : baseMisc) {
                merged.add(id);
            }

            int added = 0;
            for (Map.Entry<Integer, LezSpell> entry : LezSpellCollection.Spells.entrySet()) {
                int id = entry.getKey();
                if (id <= legacyMaxId) continue;
                if (knownCategoryIds.contains(id)) continue;
                LezSpell spell = entry.getValue();
                if (spell == null) continue;
                String name = spell.Name == null ? "" : spell.Name.trim();
                if (name.isEmpty()) continue;
                merged.add(id);
                added++;
            }

            android.util.Log.d(TAG, "Rotation fallback: legacyMaxId=" + legacyMaxId
                    + ", baseMisc=" + baseMisc.length
                    + ", addedNewServerIds=" + added
                    + ", finalMisc=" + merged.size());
            return toIntArray(merged);
        }

        /**
         * Ищет максимальный "эталонный" ID из C#-категорий.
         */
        private int findLegacyMaxSpellId() {
            int max = 0;
            max = Math.max(max, maxOf(LezSpellCollection.Hits));
            max = Math.max(max, maxOf(LezSpellCollection.Blocks));
            max = Math.max(max, maxOf(LezSpellCollection.RestoreHp));
            max = Math.max(max, maxOf(LezSpellCollection.RestoreMa));
            max = Math.max(max, maxOf(LezSpellCollection.Misc));
            return max;
        }

        private int maxOf(int[] arr) {
            int max = 0;
            if (arr == null) return max;
            for (int value : arr) {
                if (value > max) max = value;
            }
            return max;
        }

        private void addAll(Set<Integer> set, int[] arr) {
            if (arr == null) return;
            for (int value : arr) set.add(value);
        }

        private int[] toIntArray(List<Integer> list) {
            int[] result = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = list.get(i);
            }
            return result;
        }

        private SpellListAdapter setupSpellList(View root, int recyclerViewId, int[] spellIds, int[] checked) {
            RecyclerView rv = root.findViewById(recyclerViewId);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            // Для раскрывающихся секций используем общий скролл ScrollView,
            // поэтому внутренний nested-scroll у списка отключен.
            rv.setNestedScrollingEnabled(false);
            SpellListAdapter adapter = new SpellListAdapter(spellIds, checked);
            rv.setAdapter(adapter);
            return adapter;
        }

        /**
         * Настройка разворачиваемых/сворачиваемых категорий заклинаний во вкладке "Ротация".
         * Зависимости:
         * - id заголовков/стрелок/списков из tab_autoboi_rotation.xml;
         * - RecyclerView категорий уже инициализированы через setupSpellList(...).
         */
        private void setupCollapsibleSpellCategories(@NonNull View root) {
            setupCategoryToggle(root, R.id.headerSpellsHits, R.id.tvArrowSpellsHits, R.id.listSpellsHits, true);
            setupCategoryToggle(root, R.id.headerSpellsBlocks, R.id.tvArrowSpellsBlocks, R.id.listSpellsBlocks, true);
            setupCategoryToggle(root, R.id.headerSpellsMisc, R.id.tvArrowSpellsMisc, R.id.listSpellsMisc, true);
            setupCategoryToggle(root, R.id.headerSpellsRestoreHp, R.id.tvArrowSpellsRestoreHp, R.id.listSpellsRestoreHp, true);
            setupCategoryToggle(root, R.id.headerSpellsRestoreMa, R.id.tvArrowSpellsRestoreMa, R.id.listSpellsRestoreMa, true);
        }

        /**
         * Привязывает поведение "клик по заголовку -> показать/скрыть список".
         * Стрелка: вниз (▼) = свернуто, вверх (▲) = раскрыто.
         */
        private void setupCategoryToggle(@NonNull View root,
                                         int headerId,
                                         int arrowId,
                                         int listId,
                                         boolean expandedByDefault) {
            View header = root.findViewById(headerId);
            TextView arrow = root.findViewById(arrowId);
            View list = root.findViewById(listId);
            if (header == null || arrow == null || list == null) {
                return;
            }
            applyCategoryExpanded(list, arrow, expandedByDefault);
            header.setOnClickListener(clicked -> {
                boolean expanded = list.getVisibility() == View.VISIBLE;
                applyCategoryExpanded(list, arrow, !expanded);
            });
        }

        /**
         * Применяет состояние категории к UI.
         */
        private void applyCategoryExpanded(@NonNull View list, @NonNull TextView arrow, boolean expanded) {
            list.setVisibility(expanded ? View.VISIBLE : View.GONE);
            arrow.setText(expanded ? "▲" : "▼");
        }

        private void setupSeekBar(SeekBar bar, TextView label, String suffix) {
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) { label.setText(p + suffix); }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        /**
         * Нормализует ввод задержки удара для текущей группы.
         *
         * Правила:
         * - значения < 0 обрезаются до 0;
         * - верхняя граница ограничена 300 сек, чтобы исключить невалидные/ошибочные большие значения.
         *
         * Зависимости:
         * - вызывается в `showGroupHitDelayInputDialog(...)`,
         * - вызывается в `loadGroup()` при чтении из `LezBotsGroup.HitDelaySec`,
         * - вызывается в `saveGroup(...)` перед записью в профиль.
         */
        private static int normalizeHitDelaySec(int value) {
            if (value < 0) {
                return 0;
            }
            return Math.min(value, 300);
        }

        /**
         * Обновляет текстовую индикацию задержки удара в UI вкладки "Ротация".
         *
         * Формат:
         * - `0` -> "случайная (1-2 сек)" (legacy-режим);
         * - `N > 0` -> "N сек" (фиксированная задержка).
         *
         * Зависимости:
         * - `tvGroupHitDelaySecValue` из `tab_autoboi_rotation.xml`,
         * - используется после загрузки группы (`loadGroup`) и после ручного ввода (`showGroupHitDelayInputDialog`).
         */
        private void updateGroupHitDelayLabel(int delaySec) {
            if (tvGroupHitDelaySecValue == null) {
                return;
            }
            if (delaySec <= 0) {
                tvGroupHitDelaySecValue.setText("Задержка ударов: случайная (1-2 сек)");
            } else {
                tvGroupHitDelaySecValue.setText("Задержка ударов: " + delaySec + " сек");
            }
        }

        /**
         * Показывает диалог ввода "Задержки ударов" для выбранной группы противников.
         *
         * Поведение:
         * - вводится целое число секунд;
         * - значение нормализуется через `normalizeHitDelaySec(...)`;
         * - результат сохраняется во временное поле `pendingGroupHitDelaySec` и сразу отражается в UI.
         *
         * Важно:
         * - фактическая запись в `LezBotsGroup.HitDelaySec` выполняется в `saveGroup(...)` при нажатии "Сохранить".
         *
         * Зависимости:
         * - `AlertDialog` + `EditText` (Android UI),
         * - `pendingGroupHitDelaySec`,
         * - `updateGroupHitDelayLabel(...)`.
         */
        private void showGroupHitDelayInputDialog() {
            if (getContext() == null) {
                return;
            }
            final int currentDelaySec = normalizeHitDelaySec(pendingGroupHitDelaySec);
            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("0 = случайно (1-2 сек)");
            input.setText(String.valueOf(currentDelaySec));
            input.setSelection(input.getText().length());

            new AlertDialog.Builder(requireContext())
                    .setTitle("Задержка ударов (группа)")
                    .setMessage("Введите задержку между ударами в секундах для текущей группы.\n0 = случайная (1-2 сек).")
                    .setView(input)
                    .setPositiveButton("ОК", (dialog, which) -> {
                        int value;
                        try {
                            value = Integer.parseInt(input.getText().toString().trim());
                        } catch (Exception ignored) {
                            value = currentDelaySec;
                        }
                        pendingGroupHitDelaySec = normalizeHitDelaySec(value);
                        updateGroupHitDelayLabel(pendingGroupHitDelaySec);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }

        void setSelectedGroupIndex(int index) {
            selectedGroupIdx = Math.max(0, index);
            if (getView() != null) {
                loadGroup();
            }
        }

        /**
         * Загружает в UI параметры текущей выбранной группы ротации.
         *
         * Что заполняет:
         * - боевые флаги (Fury/магия/удары/блоки/прочее),
         * - пороги восстановления,
         * - выбранные списки заклинаний,
         * - пер-групповую задержку удара (`HitDelaySec`).
         *
         * Зависимости:
         * - `AppVars.Profile.LezGroups`,
         * - адаптеры списков заклинаний (`spells*Adapter`),
         * - `normalizeHitDelaySec(...)` и `updateGroupHitDelayLabel(...)`.
         */
        private void loadGroup() {
            UserConfig p = AppVars.Profile;
            if (p == null || p.LezGroups == null || p.LezGroups.isEmpty()) return;
            int safeIndex = Math.max(0, Math.min(selectedGroupIdx, p.LezGroups.size() - 1));
            LezBotsGroup g = p.LezGroups.get(safeIndex);
            checkDoFury.setChecked(g.DoFury);
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
            pendingGroupHitDelaySec = normalizeHitDelaySec(g.HitDelaySec);
            updateGroupHitDelayLabel(pendingGroupHitDelaySec);
            if (spellsHitsAdapter != null) spellsHitsAdapter.setChecked(g.SpellsHits);
            if (spellsBlocksAdapter != null) spellsBlocksAdapter.setChecked(g.SpellsBlocks);
            if (spellsRestoreHpAdapter != null) spellsRestoreHpAdapter.setChecked(g.SpellsRestoreHp);
            if (spellsRestoreMaAdapter != null) spellsRestoreMaAdapter.setChecked(g.SpellsRestoreMa);
            if (spellsMiscAdapter != null) spellsMiscAdapter.setChecked(g.SpellsMisc);
        }

        /**
         * Считывает текущие значения UI вкладки "Ротация" и сохраняет их в заданную группу.
         *
         * Что сохраняет:
         * - все флаги ротации и пороги,
         * - выбранные наборы заклинаний по категориям,
         * - пер-групповую задержку удара (`g.HitDelaySec`).
         *
         * Защита:
         * - если view вкладки не создано (ленивая инициализация ViewPager2), метод завершается без NPE.
         *
         * Зависимости:
         * - `LezBotsGroup` (модель группы),
         * - адаптеры `SpellListAdapter`,
         * - `pendingGroupHitDelaySec` + `normalizeHitDelaySec(...)`.
         */
        void saveGroup(LezBotsGroup g) {
            // Защита от NPE при "Сохранить" без открытия вкладки "Ротация".
            // Зависимость: жизненный цикл ViewPager2 (fragment view создаётся лениво).
            if (checkDoFury == null || checkDoRestoreHp == null || checkDoRestoreMa == null || checkDoAbilBlocks == null
                    || checkDoAbilHits == null || checkDoMagHits == null || checkDoMagBlocks == null
                    || checkDoHits == null || checkDoBlocks == null || checkDoMiscAbils == null
                    || seekRestoreHp == null || seekRestoreMa == null || seekMagHits == null) {
                return;
            }
            g.DoFury = checkDoFury.isChecked();
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
            g.HitDelaySec = normalizeHitDelaySec(pendingGroupHitDelaySec);
            if (spellsHitsAdapter != null) g.SpellsHits = spellsHitsAdapter.getChecked();
            if (spellsBlocksAdapter != null) g.SpellsBlocks = spellsBlocksAdapter.getChecked();
            if (spellsRestoreHpAdapter != null) g.SpellsRestoreHp = spellsRestoreHpAdapter.getChecked();
            if (spellsRestoreMaAdapter != null) g.SpellsRestoreMa = spellsRestoreMaAdapter.getChecked();
            if (spellsMiscAdapter != null) g.SpellsMisc = spellsMiscAdapter.getChecked();
        }
    }

    // ─────────────────────────── Вкладка 4: Останов ─────────────────────────────

    public static class StopTabFragment extends Fragment {
        private int selectedGroupIdx = 0;
        private CheckBox checkDoStopNow, checkDoStopLowHp, checkDoStopLowMa, checkDoExit, checkDoExitRisky;
        private SeekBar seekStopLowHp, seekStopLowMa;
        private TextView tvStopLowHp, tvStopLowMa;
        private RadioGroup radioGroupSay;

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
            radioGroupSay = v.findViewById(R.id.radioGroupSayByGroup);

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

        void setSelectedGroupIndex(int index) {
            selectedGroupIdx = Math.max(0, index);
            if (getView() != null) {
                loadGroup();
            }
        }

        private void loadGroup() {
            UserConfig p = AppVars.Profile;
            if (p == null || p.LezGroups == null || p.LezGroups.isEmpty()) return;
            int safeIndex = Math.max(0, Math.min(selectedGroupIdx, p.LezGroups.size() - 1));
            LezBotsGroup g = p.LezGroups.get(safeIndex);
            checkDoStopNow.setChecked(g.DoStopNow);
            checkDoStopLowHp.setChecked(g.DoStopLowHp);
            seekStopLowHp.setProgress(g.StopLowHp);
            tvStopLowHp.setText(g.StopLowHp + "%");
            checkDoStopLowMa.setChecked(g.DoStopLowMa);
            seekStopLowMa.setProgress(g.StopLowMa);
            tvStopLowMa.setText(g.StopLowMa + "%");
            checkDoExit.setChecked(g.DoExit);
            checkDoExitRisky.setChecked(g.DoExitRisky);
            // Пер-групповая настройка "Сообщение о нападении".
            int sayId = R.id.radioSayNoByGroup;
            LezSayType say = g.AttackSay != null ? g.AttackSay : LezSayType.No;
            if (say == LezSayType.Chat) sayId = R.id.radioSayChatByGroup;
            else if (say == LezSayType.Clan) sayId = R.id.radioSayClanByGroup;
            else if (say == LezSayType.Pair) sayId = R.id.radioSayPairByGroup;
            radioGroupSay.check(sayId);
        }

        void saveGroup(LezBotsGroup g) {
            // Защита от NPE при "Сохранить" без открытия вкладки "Останов".
            // Зависимость: жизненный цикл ViewPager2 (fragment view создаётся лениво).
            if (checkDoStopNow == null || checkDoStopLowHp == null || checkDoStopLowMa == null
                    || checkDoExit == null || checkDoExitRisky == null
                    || seekStopLowHp == null || seekStopLowMa == null || radioGroupSay == null) {
                return;
            }
            g.DoStopNow = checkDoStopNow.isChecked();
            g.DoStopLowHp = checkDoStopLowHp.isChecked();
            g.StopLowHp = seekStopLowHp.getProgress();
            g.DoStopLowMa = checkDoStopLowMa.isChecked();
            g.StopLowMa = seekStopLowMa.getProgress();
            g.DoExit = checkDoExit.isChecked();
            g.DoExitRisky = checkDoExitRisky.isChecked();
            // Сохраняем канал анонса нападения для выбранной группы.
            int checkedId = radioGroupSay.getCheckedRadioButtonId();
            if (checkedId == R.id.radioSayChatByGroup) g.AttackSay = LezSayType.Chat;
            else if (checkedId == R.id.radioSayClanByGroup) g.AttackSay = LezSayType.Clan;
            else if (checkedId == R.id.radioSayPairByGroup) g.AttackSay = LezSayType.Pair;
            else g.AttackSay = LezSayType.No;
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
        /**
         * Явный словарь иконок заклинаний (по заданию пользователя).
         * Ключ — нормализованное название заклинания.
         */
        private static final java.util.Map<String, String> SPELL_ICON_BY_NAME = createSpellIconMap();

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
            // Имя и иконка для отображения в строке заклинания.
            // Зависимость: resolveDisplaySpellName()/resolveSpellIconUrl() используют словарь из требования UI.
            String displayName = resolveDisplaySpellName(spell != null ? spell.Name : null);
            String iconUrl = resolveSpellIconUrl(id, spell != null ? spell.Name : null);
            if (displayName == null || displayName.isEmpty()) {
                displayName = "Заклинание #" + id;
            }
            String name = spell != null ? spell.Name : "Заклинание #" + id;

            // Поиск index в массиве Hits/Blocks/etc. для Od и Mana
            int odVal = 0, manaVal = 0;
            int[] allIds = LezSpellCollection.Hits; // placeholder; Od indexed by position
            // Попытка найти OD/Mana из коллекции (по позиции в полных массивах)
            // В ПК версии Od и PosMana индексируются по общему порядку спеллов в коллекции
            // Здесь упрощённо берём из Spells если есть
            holder.tvName.setText(displayName);
            holder.tvInfo.setText("ID:" + id);
            if (iconUrl != null && !iconUrl.isEmpty()) {
                holder.spellIcon.setVisibility(View.VISIBLE);
                Glide.with(holder.spellIcon.getContext())
                        .load(iconUrl)
                        .into(holder.spellIcon);
            } else {
                holder.spellIcon.setVisibility(View.GONE);
                Glide.with(holder.spellIcon.getContext()).clear(holder.spellIcon);
            }
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
            ImageView spellIcon;
            TextView tvName, tvInfo;
            VH(@NonNull View v) {
                super(v);
                checkSpell = v.findViewById(R.id.checkSpell);
                spellIcon = v.findViewById(R.id.ivSpellIcon);
                tvName = v.findViewById(R.id.tvSpellName);
                tvInfo = v.findViewById(R.id.tvSpellInfo);
            }
        }

        /**
         * Маппинг названий заклинаний на URL иконок.
         * Имена нормализуются (trim + ё->е) для устойчивого сопоставления.
         */
        private static java.util.Map<String, String> createSpellIconMap() {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put(normalizeSpellName("Смазанный удар"), "http://image.neverlands.ru/magic/m269.gif");
            map.put(normalizeSpellName("Огненная стрела"), "http://image.neverlands.ru/magic/m37.gif");
            map.put(normalizeSpellName("Тело-Огонь"), "http://image.neverlands.ru/magic/m56.gif");
            map.put(normalizeSpellName("Молния"), "http://image.neverlands.ru/magic/m205.gif");
            map.put(normalizeSpellName("Ураган"), "http://image.neverlands.ru/magic/m208.gif");
            map.put(normalizeSpellName("Песочная стрела"), "http://image.neverlands.ru/magic/m122.gif");
            map.put(normalizeSpellName("Колючки"), "http://image.neverlands.ru/magic/m94.gif");
            map.put(normalizeSpellName("Ледяная стрела"), "http://image.neverlands.ru/magic/m144.gif");
            map.put(normalizeSpellName("Прикосновение льдом"), "http://image.neverlands.ru/magic/m148.gif");
            map.put(normalizeSpellName("Святой кокон"), "http://image.neverlands.ru/magic/m267.gif");
            map.put(normalizeSpellName("Кривое зеркало Хаоса"), "http://image.neverlands.ru/magic/m271.gif");
            map.put(normalizeSpellName("Огненный щит"), "http://image.neverlands.ru/magic/m57.gif");
            map.put(normalizeSpellName("Воздушный барьер"), "http://image.neverlands.ru/magic/m258.gif");
            map.put(normalizeSpellName("Стена из песка"), "http://image.neverlands.ru/magic/m142.gif");
            map.put(normalizeSpellName("Ледяной щит"), "http://image.neverlands.ru/magic/m181.gif");
            map.put(normalizeSpellName("Тотальная защита"), "http://image.neverlands.ru/magic/m380.gif");
            map.put(normalizeSpellName("Уязвимость от огня"), "http://image.neverlands.ru/magic/m55.gif");
            map.put(normalizeSpellName("Танец огня"), "http://image.neverlands.ru/magic/m49.gif");
            map.put(normalizeSpellName("Танец пламени"), "http://image.neverlands.ru/magic/m49.gif");
            map.put(normalizeSpellName("Огненная спираль"), "http://image.neverlands.ru/magic/m51.gif");
            map.put(normalizeSpellName("Вампиризм"), "http://image.neverlands.ru/magic/m265.gif");
            map.put(normalizeSpellName("Защита пламени"), "http://image.neverlands.ru/magic/m73.gif");
            map.put(normalizeSpellName("Освежающий бриз"), "http://image.neverlands.ru/magic/m223.gif");
            map.put(normalizeSpellName("Источник"), "http://image.neverlands.ru/magic/m85.gif");
            map.put(normalizeSpellName("Восстановление MP"), "http://image.neverlands.ru/magic/m306.gif");
            return map;
        }

        /**
         * Возвращает URL иконки заклинания:
         * 1) сначала по явному маппингу имени (для переименований/исключений),
         * 2) затем fallback по ID в формате image.neverlands.ru/magic/m{ID}.gif.
         * Это убирает необходимость вручную добавлять каждое новое заклинание.
         */
        private static String resolveSpellIconUrl(int spellId, String spellName) {
            String mapped = SPELL_ICON_BY_NAME.get(normalizeSpellName(spellName));
            if (mapped != null && !mapped.isEmpty()) {
                return mapped;
            }
            if (spellId >= 0) {
                return "http://image.neverlands.ru/magic/m" + spellId + ".gif";
            }
            return null;
        }

        /**
         * Переименование отображаемого названия:
         * "Танец огня" -> "Танец пламени" (только UI, без изменения данных профиля/сервера).
         */
        private static String resolveDisplaySpellName(String spellName) {
            String normalized = normalizeSpellName(spellName);
            if ("Танец огня".equalsIgnoreCase(normalized)) {
                return "Танец пламени";
            }
            return spellName;
        }

        private static String normalizeSpellName(String spellName) {
            if (spellName == null) return "";
            return spellName.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}

