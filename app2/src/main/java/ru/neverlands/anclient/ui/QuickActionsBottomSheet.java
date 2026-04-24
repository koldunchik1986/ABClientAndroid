package ru.neverlands.anclient.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.IOException;
import java.io.InputStream;

import ru.neverlands.anclient.R;
import ru.neverlands.anclient.manager.FastActionManager;
import ru.neverlands.anclient.utils.AppVars;

/**
 * Панель быстрых действий (портирование FormQuick.cs).
 * Отображает набор кнопок для различных типов атак на указанного игрока.
 * Каждая кнопка соответствует типу атаки из ПК-версии (FormMain.FastAttack*).
 */
public class QuickActionsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_NICK = "nick";

    private EditText editTextNick;
    private SwitchCompat switchAutoClose;

    /**
     * Фабричный метод (аналог конструктора FormQuick(string nick) в C#).
     * @param nick Ник цели (может быть null).
     */
    public static QuickActionsBottomSheet newInstance(@Nullable String nick) {
        QuickActionsBottomSheet fragment = new QuickActionsBottomSheet();
        Bundle args = new Bundle();
        if (nick != null) {
            args.putString(ARG_NICK, nick);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_quick_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editTextNick = view.findViewById(R.id.editTextNick);
        switchAutoClose = view.findViewById(R.id.switchAutoClose);

        // Предзаполнение ника из аргументов (аналог C#: textBoxNick.Text = nick)
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_NICK)) {
            editTextNick.setText(args.getString(ARG_NICK));
        }

        // Настройка кнопок: иконка из assets + обработчик
        // Все иконки берутся с image.neverlands.ru/weapon/ — единый источник для всех кнопок
        // Ряд 1: Нападалки
        setupButtonUrl(view, R.id.buttonHitSimple,        "http://image.neverlands.ru/weapon/i_svi_001.gif", "Обычная нападалка", "simple");
        setupButtonUrl(view, R.id.buttonHitBlood,         "http://image.neverlands.ru/weapon/i_svi_002.gif", "Кровавая нападалка", "blood");
        setupButtonUrl(view, R.id.buttonHitUltimate,      "http://image.neverlands.ru/weapon/i_w28_26.gif",  "Боевая нападалка", "ultimate");
        setupButtonUrl(view, R.id.buttonHitClosedUltimate,"http://image.neverlands.ru/weapon/i_w28_26x.gif", "Закрытая боевая", "closedultimate");

        // Ряд 2: Кулачки
        setupButtonUrl(view, R.id.buttonFistSimple, "http://image.neverlands.ru/weapon/i_w28_24.gif", "Обычная кулачка", "fist");
        setupButtonUrl(view, R.id.buttonFistClosed, "http://image.neverlands.ru/weapon/i_w28_25.gif", "Закрытая кулачка", "closedfist");
        setupButtonUrl(view, R.id.buttonClosed,     "http://image.neverlands.ru/weapon/i_svi_205.gif", "Закрытая нападалка", "closed");

        // Ряд 3: Абилки
        setupButtonUrl(view, R.id.buttonFog,      "http://image.neverlands.ru/weapon/i_svi_213.gif", "Туман", "fog");
        setupButtonUrl(view, R.id.buttonPoison,   "http://image.neverlands.ru/weapon/i_w27_41.gif",  "Яд", "poison");
        setupButtonUrl(view, R.id.buttonStrong,   "http://image.neverlands.ru/weapon/i_w27_52.gif",  "Сильная спина", "strong");
        setupButtonUrl(view, R.id.buttonInvisible,"http://image.neverlands.ru/weapon/i_w27_53.gif",  "Невид", "invisible");

        // Ряд 4: Свитки (на себя / без ника)
        // Иконки берутся с image.neverlands.ru/weapon/ — там хранятся все иконки предметов
        setupButtonUrl(view, R.id.buttonSelfRass,  "http://image.neverlands.ru/weapon/i_w28_23.gif", "Рассеять невид", "selfRass");
        setupButtonUrl(view, R.id.buttonOpenNevid, "http://image.neverlands.ru/weapon/i_w28_28.gif", "Обнаружение", "openNevid");
        setupButtonUrl(view, R.id.buttonTeleport,  "http://image.neverlands.ru/weapon/i_w28_22.gif", "Телепорт", "teleport");
        setupButtonUrl(view, R.id.buttonIsland,    "http://image.neverlands.ru/weapon/i_w28_22.gif", "Остров (Туротор)", "island");

        // Ряд 5: Тотем + Эликсиры
        setupButtonUrl(view, R.id.buttonTotem,        "http://image.neverlands.ru/signs/totems/9.gif", "Тотем", "totem");
        setupButtonUrl(view, R.id.buttonElixirBlaz,   "http://image.neverlands.ru/weapon/i_w61_107.gif", "Эликсир Блаженства", "elixirBlaz");
        setupButtonUrl(view, R.id.buttonElixirCure,   "http://image.neverlands.ru/weapon/i_w61_104.gif", "Эликсир Исцеления", "elixirCure");
        setupButtonUrl(view, R.id.buttonElixirRestore,"http://image.neverlands.ru/weapon/i_w61_101.gif", "Эликсир Восстановления", "elixirRestore");
    }

    /**
     * Настраивает кнопку: загружает иконку из assets, устанавливает обработчики.
     * Аналог C#: toolTip1.SetToolTip(button, tooltip) + button.Click += handler
     *
     * @param view       корневой View
     * @param buttonId   ID кнопки в layout
     * @param assetPath  путь к иконке в assets (например "Icons/i_svi_001.gif")
     * @param tooltip    текст подсказки (показывается при long press)
     * @param attackType тип атаки (для идентификации действия)
     */
    private void setupButton(View view, int buttonId, String assetPath, String tooltip, String attackType) {
        ImageButton button = view.findViewById(buttonId);
        if (button == null) return;

        // Загружаем иконку из assets
        Context context = requireContext();
        try {
            InputStream is = context.getAssets().open(assetPath);
            Drawable drawable = Drawable.createFromStream(is, null);
            button.setImageDrawable(drawable);
            is.close();
        } catch (IOException e) {
            // Иконка не найдена — оставляем пустую кнопку
        }

        // Long press показывает тултип (аналог C# toolTip1.SetToolTip)
        button.setOnLongClickListener(v -> {
            Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show();
            return true;
        });

        // Click — выполняет быструю атаку (аналог C# ButtonXxxClick -> FormMain.FastAttackXxx)
        button.setOnClickListener(v -> {
            String nick = editTextNick.getText().toString().trim();
            if (nick.isEmpty()) {
                Toast.makeText(context, "Введите ник цели", Toast.LENGTH_SHORT).show();
                return;
            }

            onAttackButtonClick(attackType, nick);
        });
    }

    /**
     * Настраивает кнопку с иконкой из assets или URL (для кнопок на себя).
     * Если assetPath начинается с "http" — загружает через Glide по URL.
     * Иначе — из assets. При ошибке загрузки из assets пробует URL-фолбэк.
     */
    private void setupButtonUrl(View view, int buttonId, String iconPathOrUrl, String tooltip, String attackType) {
        ImageButton button = view.findViewById(buttonId);
        if (button == null) return;

        Context context = requireContext();
        if (iconPathOrUrl.startsWith("http")) {
            Glide.with(context).load(iconPathOrUrl).into(button);
        } else {
            try {
                InputStream is = context.getAssets().open(iconPathOrUrl);
                Drawable drawable = Drawable.createFromStream(is, null);
                button.setImageDrawable(drawable);
                is.close();
            } catch (IOException e) {
                // иконка не найдена в assets — оставляем пустой
            }
        }

        button.setOnLongClickListener(v -> {
            Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show();
            return true;
        });

        // Если тип содержит "Elf" — значит на себя, без ника
        boolean isSelf = attackType.equals("selfRass") || attackType.equals("openNevid")
                || attackType.equals("island") || attackType.equals("teleport")
                || attackType.equals("elixirBlaz") || attackType.equals("elixirCure")
                || attackType.equals("elixirRestore");

        button.setOnClickListener(v -> {
            if (isSelf) {
                onSelfActionClick(attackType);
            } else {
                String nick = editTextNick.getText().toString().trim();
                if (nick.isEmpty()) {
                    Toast.makeText(context, "Введите ник цели", Toast.LENGTH_SHORT).show();
                    return;
                }
                onAttackButtonClick(attackType, nick);
            }
        });
    }

    /**
     * Настраивает кнопку для действий на СЕБЯ (без ника).
     * Эти кнопки не требуют ввода ника — вызывают FastActionManager.*() без аргумента.
     */
    private void setupSelfButton(View view, int buttonId, String assetPath, String tooltip, String attackType) {
        ImageButton button = view.findViewById(buttonId);
        if (button == null) return;

        Context context = requireContext();
        try {
            InputStream is = context.getAssets().open(assetPath);
            Drawable drawable = Drawable.createFromStream(is, null);
            button.setImageDrawable(drawable);
            is.close();
        } catch (IOException e) {
            // Иконка не найдена
        }

        button.setOnLongClickListener(v -> {
            Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show();
            return true;
        });

        button.setOnClickListener(v -> {
            onSelfActionClick(attackType);
        });
    }

    /**
     * Обработчик нажатия на кнопку атаки.
     * Аналог C#: FormMain.FastAttack*(nick) + CheckClose()
     *
     * @param attackType тип атаки
     * @param nick       ник цели
     */
    private void onAttackButtonClick(String attackType, String nick) {
        // Вызываем соответствующий метод FastActionManager (портировано из FormMainFast.cs)
        // Все атаки запускаются через fastAttackAsync — он сначала проверяет бой цели,
        // ждёт окончания если нужно, и только потом армирует быстрое действие.
        // (аналог C#: все FastAttack* методы запускают поток FastAttackAsync)
        String weapon;
        switch (attackType) {
            case "simple":         weapon = "i_svi_001.gif"; break;
            case "blood":          weapon = "i_svi_002.gif"; break;
            case "ultimate":       weapon = "i_w28_26.gif";  break;
            case "closedultimate": weapon = "i_w28_26X.gif"; break;
            case "closed":         weapon = "i_svi_205.gif"; break;
            case "fist":           weapon = "i_w28_24.gif";  break;
            case "closedfist":     weapon = "i_w28_25.gif";  break;
            case "fog":            weapon = "i_svi_213.gif"; break;
            case "poison":         weapon = "Яд";            break;
            case "strong":         weapon = "Зелье Сильной Спины"; break;
            case "invisible":      weapon = "Зелье Невидимости";   break;
            case "totem":          weapon = "Тотем";         break;
            case "teleport":       weapon = "i_w28_22.gif";  break;
            default:
                Toast.makeText(requireContext(), "Неизвестный тип: " + attackType, Toast.LENGTH_SHORT).show();
                return;
        }
        FastActionManager.fastAttackAsync(weapon, nick);

        Toast.makeText(requireContext(), getAttackName(attackType) + " на " + nick, Toast.LENGTH_SHORT).show();

        // Аналог C# CheckClose(): if (checkBoxClose.Checked) Close()
        if (switchAutoClose.isChecked()) {
            dismiss();
        }
    }

    /**
     * Обработчик нажатия на кнопку действия БЕЗ цели (на себя).
     */
    private void onSelfActionClick(String attackType) {
        switch (attackType) {
            case "selfRass":     FastActionManager.fastAttackSelfRass(); break;
            case "openNevid":    FastActionManager.fastAttackOpenNevid(); break;
            case "teleport":     FastActionManager.fastAttackTeleport(""); break;
            case "island":       FastActionManager.fastAttackIslandPot(); break;
            case "elixirBlaz":   FastActionManager.fastAttackBlazElixir(); break;
            case "elixirCure":   FastActionManager.fastAttackMomentCureElixir(); break;
            case "elixirRestore": FastActionManager.fastAttackMomentRestoreElixir(); break;
            default:
                Toast.makeText(requireContext(), "Неизвестный тип: " + attackType, Toast.LENGTH_SHORT).show();
                return;
        }

        Toast.makeText(requireContext(), getAttackName(attackType), Toast.LENGTH_SHORT).show();

        if (switchAutoClose.isChecked()) {
            dismiss();
        }
    }

    /**
     * Возвращает русское название типа атаки.
     */
    private String getAttackName(String attackType) {
        switch (attackType) {
            case "simple": return "Обычная нападалка";
            case "blood": return "Кровавая нападалка";
            case "ultimate": return "Боевая нападалка";
            case "closedultimate": return "Закрытая боевая";
            case "closed": return "Закрытая нападалка";
            case "fist": return "Обычная кулачка";
            case "closedfist": return "Закрытая кулачка";
            case "fog": return "Туман";
            case "poison": return "Яд";
            case "strong": return "Сильная спина";
            case "invisible": return "Невид";
            case "totem": return "Тотем";
            case "teleport": return "Телепорт";
            case "selfRass": return "Рассеять невид";
            case "openNevid": return "Обнаружение";
            case "island": return "Остров";
            case "elixirBlaz": return "Эликсир Блаженства";
            case "elixirCure": return "Эликсир Исцеления";
            case "elixirRestore": return "Эликсир Восстановления";
            default: return attackType;
        }
    }
}
