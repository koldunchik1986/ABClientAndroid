package ru.neverlands.abclient.ui;

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

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.manager.FastActionManager;
import ru.neverlands.abclient.utils.AppVars;

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
        // Ряд 1: Нападалки
        setupButton(view, R.id.buttonHitSimple, "Icons/i_svi_001.gif", "Обычная нападалка", "simple");
        setupButton(view, R.id.buttonHitBlood, "Icons/i_svi_002.gif", "Кровавая нападалка", "blood");
        setupButton(view, R.id.buttonHitUltimate, "Icons/i_w28_26.gif", "Боевая нападалка", "ultimate");
        setupButton(view, R.id.buttonHitClosedUltimate, "Icons/i_w28_26x.png", "Закрытая боевая", "closedultimate");

        // Ряд 2: Кулачки
        setupButton(view, R.id.buttonFistSimple, "Icons/i_w28_24.gif", "Обычная кулачка", "fist");
        setupButton(view, R.id.buttonFistClosed, "Icons/i_w28_25.gif", "Закрытая кулачка", "closedfist");
        setupButton(view, R.id.buttonClosed, "Icons/i_svi_205.gif", "Закрытая нападалка", "closed");

        // Ряд 3: Абилки
        setupButton(view, R.id.buttonFog, "Icons/i_svi_213.gif", "Туман", "fog");
        setupButton(view, R.id.buttonPoison, "Icons/i_w27_41.gif", "Яд", "poison");
        setupButton(view, R.id.buttonStrong, "Icons/i_w27_52.gif", "Сильная спина", "strong");
        setupButton(view, R.id.buttonInvisible, "Icons/i_w27_53.gif", "Невид", "invisible");
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
     * Обработчик нажатия на кнопку атаки.
     * Аналог C#: FormMain.FastAttack*(nick) + CheckClose()
     *
     * TODO: Заменить Toast на реальный вызов FastActionManager после портирования FormMainFast.cs
     *
     * @param attackType тип атаки
     * @param nick       ник цели
     */
    private void onAttackButtonClick(String attackType, String nick) {
        // Вызываем соответствующий метод FastActionManager (портировано из FormMainFast.cs)
        switch (attackType) {
            case "simple":      FastActionManager.fastAttack(nick); break;
            case "blood":       FastActionManager.fastAttackBlood(nick); break;
            case "ultimate":    FastActionManager.fastAttackUltimate(nick); break;
            case "closedultimate": FastActionManager.fastAttackClosedUltimate(nick); break;
            case "closed":      FastActionManager.fastAttackClosed(nick); break;
            case "fist":        FastActionManager.fastAttackFist(nick); break;
            case "closedfist":  FastActionManager.fastAttackClosedFist(nick); break;
            case "fog":         FastActionManager.fastAttackFog(nick); break;
            case "poison":      FastActionManager.fastAttackPoison(nick); break;
            case "strong":      FastActionManager.fastAttackStrong(nick); break;
            case "invisible":   FastActionManager.fastAttackNevidPot(nick); break;
            default:
                Toast.makeText(requireContext(), "Неизвестный тип: " + attackType, Toast.LENGTH_SHORT).show();
                return;
        }

        Toast.makeText(requireContext(), getAttackName(attackType) + " на " + nick, Toast.LENGTH_SHORT).show();

        // Аналог C# CheckClose(): if (checkBoxClose.Checked) Close()
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
            default: return attackType;
        }
    }
}
