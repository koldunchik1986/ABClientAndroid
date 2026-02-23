package ru.neverlands.abclient.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.model.QuickActionType;

/**
 * Адаптер для списка функций в диалоге выбора.
 */
public class FunctionListAdapter extends BaseAdapter {
    private final Context context;
    private final List<QuickActionType> functions;
    private final OnFunctionSelectedListener listener;
    private AlertDialog dialog;

    public interface OnFunctionSelectedListener {
        void onFunctionSelected(QuickActionType type);
    }

    public FunctionListAdapter(Context context, OnFunctionSelectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.functions = new ArrayList<>();
        
        for (QuickActionType type : QuickActionType.values()) {
            if (type != QuickActionType.NONE) {
                functions.add(type);
            }
        }
    }

    public void setDialog(AlertDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public int getCount() {
        return functions.size();
    }

    @Override
    public Object getItem(int position) {
        return functions.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_function, parent, false);
            holder = new ViewHolder();
            holder.nameText = convertView.findViewById(R.id.item_function_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final QuickActionType type = functions.get(position);
        holder.nameText.setText(type.getDisplayName());
        
        convertView.setOnClickListener(v -> {
            if (listener != null) {
                if (type == QuickActionType.QUICK_ACTIONS) {
                    // Показать подвыбор быстрых действий на себя
                    showQuickActionsSubMenu();
                } else {
                    listener.onFunctionSelected(type);
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                }
            }
        });

        return convertView;
    }

    private void showQuickActionsSubMenu() {
        // Список быстрых действий на себя
        final QuickActionType[] selfActions = {
            QuickActionType.QUICK_SELF_RASS,
            QuickActionType.QUICK_OPEN_NEVID,
            QuickActionType.QUICK_TELEPORT,
            QuickActionType.QUICK_ISLAND,
            QuickActionType.QUICK_TOTEM,
            QuickActionType.QUICK_ELIXIR_BLAZ,
            QuickActionType.QUICK_ELIXIR_CURE,
            QuickActionType.QUICK_ELIXIR_RESTORE
        };

        String[] items = new String[selfActions.length];
        for (int i = 0; i < selfActions.length; i++) {
            items[i] = selfActions[i].getDisplayName();
        }

        if (dialog != null) {
            dialog.dismiss();
        }

        new AlertDialog.Builder(context)
            .setTitle("Быстрые действия (на себя)")
            .setItems(items, (d, which) -> {
                QuickActionType selected = selfActions[which];
                listener.onFunctionSelected(selected);
                Toast.makeText(context, "Функция \"" + selected.getDisplayName() + "\" добавлена", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private static class ViewHolder {
        TextView nameText;
    }
}
