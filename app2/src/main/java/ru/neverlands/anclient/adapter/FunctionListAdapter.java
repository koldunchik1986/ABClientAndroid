package ru.neverlands.anclient.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ru.neverlands.anclient.R;
import ru.neverlands.anclient.license.LicenseRuntime;
import ru.neverlands.anclient.model.QuickActionType;

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
            // Фильтрация selector намеренно выполняется до render. Limited/public users
            // не должны видеть full-only functions, а назначение повторно проверяется в
            // `QuickButtonsManager.assignFunction(...)` вторым слоем.
            if (type != QuickActionType.NONE
                    && !isQuickSelfAction(type)
                    && LicenseRuntime.getInstance().isActionAllowed(type)) {
                functions.add(type);
            }
        }
        
        Collections.sort(functions, new Comparator<QuickActionType>() {
            @Override
            public int compare(QuickActionType o1, QuickActionType o2) {
                return o1.getDisplayName().compareTo(o2.getDisplayName());
            }
        });
    }

    private boolean isQuickSelfAction(QuickActionType type) {
        return type == QuickActionType.QUICK_SELF_RASS ||
               type == QuickActionType.QUICK_OPEN_NEVID ||
               type == QuickActionType.QUICK_TELEPORT ||
               type == QuickActionType.QUICK_ISLAND ||
               type == QuickActionType.QUICK_TOTEM ||
               type == QuickActionType.QUICK_ELIXIR_BLAZ ||
               type == QuickActionType.QUICK_ELIXIR_CURE ||
               type == QuickActionType.QUICK_ELIXIR_RESTORE;
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
            holder.iconView = convertView.findViewById(R.id.item_function_icon);
            holder.nameText = convertView.findViewById(R.id.item_function_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final QuickActionType type = functions.get(position);
        holder.nameText.setText(type.getDisplayName());
        
        String iconUrl = getIconUrlForAction(type);
        if (iconUrl != null) {
            int fallbackRes = getLocalIconForAction(type);
            Glide.with(context)
                .load(iconUrl)
                .placeholder(fallbackRes)
                .error(fallbackRes)
                .into(holder.iconView);
        } else {
            holder.iconView.setImageResource(getLocalIconForAction(type));
        }
        
        convertView.setOnClickListener(v -> {
            if (listener != null) {
                if (type == QuickActionType.QUICK_ACTIONS) {
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

    /** D6: реализация вынесена в {@link ru.neverlands.anclient.ui.QuickActionIcons#getIconUrl(QuickActionType)}. */
    private String getIconUrlForAction(QuickActionType type) {
        return ru.neverlands.anclient.ui.QuickActionIcons.getIconUrl(type);
    }

    private int getLocalIconForAction(QuickActionType type) {
        switch (type) {
            case QUICK_ACTIONS:
                return R.drawable.ic_sort;
            case OPEN_CONTACTS:
                return R.drawable.ic_add_contact;
            case OPEN_PINFO:
                return R.drawable.ic_info;
            case OPEN_LOGS:
                return R.drawable.ic_add;
            case OPEN_STATS:
                return R.drawable.ic_info;
            case TIMERS:
                return R.drawable.ic_timer;
            case REFRESH_CONTACTS:
                return R.drawable.ic_refresh;
            case AUTO_DRINK:
                return R.drawable.ic_add;
            case AUTO_MOVING:
                return R.drawable.ic_globe;
            case AUTO_COMPASS:
                return R.drawable.ic_compas;
            case AUTO_BOSS:
                return R.drawable.ic_compas;
            case AUTO_TREASURE:
                return R.drawable.ic_auto_detect;
            case AUTO_MINE:
                return R.drawable.ic_add;
            case AUTO_REFRESH:
                return R.drawable.ic_refresh;
            case AUTO_CAPTCHA:
                return R.drawable.ic_auto_detect;
            default:
                return R.drawable.ic_add;
        }
    }

    private void showQuickActionsSubMenu() {
        List<QuickActionType> selfActions = new ArrayList<>(Arrays.asList(
                QuickActionType.QUICK_SELF_RASS,
                QuickActionType.QUICK_OPEN_NEVID,
                QuickActionType.QUICK_TELEPORT,
                QuickActionType.QUICK_ISLAND,
                QuickActionType.QUICK_TOTEM,
                QuickActionType.QUICK_ELIXIR_BLAZ,
                QuickActionType.QUICK_ELIXIR_CURE,
                QuickActionType.QUICK_ELIXIR_RESTORE
        ));
        for (int index = selfActions.size() - 1; index >= 0; index--) {
            if (!LicenseRuntime.getInstance().isActionAllowed(selfActions.get(index))) {
                selfActions.remove(index);
            }
        }

        if (selfActions.isEmpty()) {
            Toast.makeText(context, "Быстрые действия недоступны", Toast.LENGTH_SHORT).show();
            return;
        }

        Collections.sort(selfActions, new Comparator<QuickActionType>() {
            @Override
            public int compare(QuickActionType o1, QuickActionType o2) {
                return o1.getDisplayName().compareTo(o2.getDisplayName());
            }
        });

        if (dialog != null) {
            dialog.dismiss();
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_function, null);
        ListView listView = dialogView.findViewById(R.id.functions_list);
        
        SelfActionAdapter adapter = new SelfActionAdapter(context, selfActions);
        listView.setAdapter(adapter);

        AlertDialog subDialog = new AlertDialog.Builder(context)
            .setTitle("Быстрые действия (на себя)")
            .setView(dialogView)
            .setNegativeButton("Отмена", (d, which) -> {
                if (dialog != null) {
                    dialog.dismiss();
                }
            })
            .create();
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            QuickActionType selected = selfActions.get(position);
            subDialog.dismiss();
            if (dialog != null) {
                dialog.dismiss();
            }
            listener.onFunctionSelected(selected);
            Toast.makeText(context, "Функция \"" + selected.getDisplayName() + "\" добавлена", Toast.LENGTH_SHORT).show();
        });
        
        subDialog.show();
    }

    private static class SelfActionAdapter extends BaseAdapter {
        private final Context context;
        private final List<QuickActionType> actions;

        SelfActionAdapter(Context context, List<QuickActionType> actions) {
            this.context = context;
            this.actions = actions;
        }

        @Override
        public int getCount() {
            return actions.size();
        }

        @Override
        public Object getItem(int position) {
            return actions.get(position);
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
                holder.iconView = convertView.findViewById(R.id.item_function_icon);
                holder.nameText = convertView.findViewById(R.id.item_function_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            QuickActionType type = actions.get(position);
            holder.nameText.setText(type.getDisplayName());

            String iconUrl = getIconUrlForAction(type);
            if (iconUrl != null) {
                Glide.with(context)
                    .load(iconUrl)
                    .placeholder(R.drawable.ic_add)
                    .error(R.drawable.ic_add)
                    .into(holder.iconView);
            } else {
                holder.iconView.setImageResource(R.drawable.ic_add);
            }

            return convertView;
        }

        /** D6: реализация вынесена в {@link ru.neverlands.anclient.ui.QuickActionIcons#getIconUrl(QuickActionType)}. */
        private String getIconUrlForAction(QuickActionType type) {
            return ru.neverlands.anclient.ui.QuickActionIcons.getIconUrl(type);
        }

        private static class ViewHolder {
            ImageView iconView;
            TextView nameText;
        }
    }

    private static class ViewHolder {
        ImageView iconView;
        TextView nameText;
    }
}
