package ru.neverlands.abclient.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;
import ru.neverlands.abclient.R;
import ru.neverlands.abclient.model.Contact;

/**
 * Адаптер для RecyclerView, отображающего список контактов с группами по кланам.
 * Поддерживает два типа элементов: заголовок группы и элемент контакта.
 */
public class ContactsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // --- Внутренние классы для представления элементов списка ---

    /**
     * Абстрактный базовый класс для всех элементов, которые могут быть отображены в списке.
     * Позволяет создать гетерогенный список (содержащий разные типы объектов).
     */
    public static abstract class DisplayableItem {
        /** Возвращает тип элемента (для выбора layout-файла). */
        abstract public int getType();
        /** Возвращает уникальный ID элемента (для стабильной работы RecyclerView). */
        abstract public long getId();
    }

    /**
     * Представляет заголовок группы (клана) в списке.
     */
    public static class GroupHeaderItem extends DisplayableItem {
        public final String clanName;
        public final String clanIco;
        public final String clanLevel;
        public final int totalMemberCount;
        public final int onlineMemberCount;
        public boolean isExpanded;

        public GroupHeaderItem(String clanName, String clanIco, String clanLevel, int totalMemberCount, int onlineMemberCount) {
            this.clanName = clanName;
            this.clanIco = clanIco;
            this.clanLevel = clanLevel;
            this.totalMemberCount = totalMemberCount;
            this.onlineMemberCount = onlineMemberCount;
            this.isExpanded = true; // По умолчанию группы развернуты
        }

        @Override
        public int getType() {
            // Возвращает ID layout-файла, который будет использоваться для этого типа элемента.
            return R.layout.list_item_contact_group_header;
        }

        @Override
        public long getId() {
            // Используем хэш-код имени клана как уникальный ID.
            return clanName.hashCode();
        }
    }

    /**
     * Представляет один контакт (персонажа) в списке.
     */
    public static class ContactItem extends DisplayableItem {
        public final Contact contact;

        public ContactItem(Contact contact) {
            this.contact = contact;
        }

        @Override
        public int getType() {
            return R.layout.contact_list_item_v2;
        }

        @Override
        public long getId() {
            // ID игрока - надежный уникальный идентификатор.
            return contact.playerID.hashCode();
        }
    }

    // --- Поля адаптера и интерфейсы для колбэков ---

    private List<DisplayableItem> items; // Список, содержащий и заголовки, и контакты
    private final OnInfoClickListener onInfoClickListener;
    private final OnWarStatusClickListener onWarStatusClickListener;
    private final OnItemLongClickListener onItemLongClickListener;
    private final OnGroupClickListener onGroupClickListener;
    private final OnGroupLongClickListener onGroupLongClickListener;

    // --- Интерфейсы для обработки нажатий в Activity ---
    public interface OnInfoClickListener { void onInfoClick(Contact contact); }
    public interface OnWarStatusClickListener { void onWarStatusClick(Contact contact); }
    public interface OnItemLongClickListener { void onItemLongClick(Contact contact); }
    public interface OnGroupClickListener { void onGroupClick(GroupHeaderItem groupHeaderItem); }
    public interface OnGroupLongClickListener { void onGroupLongClick(GroupHeaderItem groupHeaderItem); }

    public ContactsAdapter(List<DisplayableItem> items, OnInfoClickListener onInfoClickListener, OnWarStatusClickListener onWarStatusClickListener, OnItemLongClickListener onItemLongClickListener, OnGroupClickListener onGroupClickListener, OnGroupLongClickListener onGroupLongClickListener) {
        this.items = items;
        this.onInfoClickListener = onInfoClickListener;
        this.onWarStatusClickListener = onWarStatusClickListener;
        this.onItemLongClickListener = onItemLongClickListener;
        this.onGroupClickListener = onGroupClickListener;
        this.onGroupLongClickListener = onGroupLongClickListener;
        setHasStableIds(true); // Включаем поддержку стабильных ID для улучшения производительности
    }

    // --- Переопределенные методы RecyclerView.Adapter ---

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    /**
     * Определяет, какой тип View нужно создать для элемента на данной позиции.
     */
    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    /**
     * Создает ViewHolder в зависимости от типа элемента (заголовок или контакт).
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == R.layout.list_item_contact_group_header) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_contact_group_header, parent, false);
            return new GroupHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_list_item_v2, parent, false);
            return new ContactViewHolder(view);
        }
    }

    /**
     * Заполняет ViewHolder данными в зависимости от его типа.
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof GroupHeaderViewHolder) {
            ((GroupHeaderViewHolder) holder).bind((GroupHeaderItem) items.get(position), onGroupClickListener, onGroupLongClickListener);
        } else if (holder instanceof ContactViewHolder) {
            ((ContactViewHolder) holder).bind(((ContactItem) items.get(position)).contact, onInfoClickListener, onWarStatusClickListener, onItemLongClickListener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Обновляет список элементов и перерисовывает RecyclerView.
     */
    public void updateItems(List<DisplayableItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    // --- Классы ViewHolder ---

    /**
     * ViewHolder для заголовка группы.
     */
    static class GroupHeaderViewHolder extends RecyclerView.ViewHolder {
        private final ImageView clanIconImageView;
        private final TextView clanNameTextView;
        private final ImageView expandIndicatorImageView;

        public GroupHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            clanIconImageView = itemView.findViewById(R.id.clanIconImageView);
            clanNameTextView = itemView.findViewById(R.id.clanNameTextView);
            expandIndicatorImageView = itemView.findViewById(R.id.expandIndicatorImageView);
        }

        /**
         * Заполняет View заголовка данными из объекта GroupHeaderItem.
         */
        public void bind(final GroupHeaderItem group, final OnGroupClickListener groupClickListener, final OnGroupLongClickListener groupLongClickListener) {
            // Формирование строки заголовка: "Имя (Level: Уровень Users: онлайн/всего)"
            String headerText = String.format("%s (Level: %s Users: %d/%d)", group.clanName, group.clanLevel, group.onlineMemberCount, group.totalMemberCount);
            clanNameTextView.setText(headerText);

            // Загрузка иконки клана
            if (group.clanIco != null && !group.clanIco.isEmpty()) {
                clanIconImageView.setVisibility(View.VISIBLE);
                String clanIconUrl = "http://image.neverlands.ru/signs/" + group.clanIco;
                Glide.with(itemView.getContext()).load(clanIconUrl).into(clanIconImageView);
            } else {
                clanIconImageView.setVisibility(View.INVISIBLE); // Скрываем, если иконки нет
            }

            // Установка иконки-индикатора (стрелка вверх/вниз)
            if (group.isExpanded) {
                expandIndicatorImageView.setImageResource(R.drawable.ic_expand_less);
            } else {
                expandIndicatorImageView.setImageResource(R.drawable.ic_expand_more);
            }
            // Установка обработчиков нажатий
            itemView.setOnClickListener(v -> groupClickListener.onGroupClick(group));
            itemView.setOnLongClickListener(v -> {
                groupLongClickListener.onGroupLongClick(group);
                return true;
            });
        }
    }

    /**
     * ViewHolder для элемента контакта.
     */
    static class ContactViewHolder extends RecyclerView.ViewHolder {
        // ... view поля ...
        private final ImageView onlineStatusIndicator, inclinationIcon, clanIcon;
        private final TextView warStatusText, contactNickText, locationTextView;
        private final ImageButton infoButton;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            // ... инициализация view ...
            onlineStatusIndicator = itemView.findViewById(R.id.onlineStatusIndicator);
            inclinationIcon = itemView.findViewById(R.id.inclinationIcon);
            clanIcon = itemView.findViewById(R.id.clanIcon);
            warStatusText = itemView.findViewById(R.id.warStatusText);
            contactNickText = itemView.findViewById(R.id.contactNickText);
            locationTextView = itemView.findViewById(R.id.locationTextView);
            infoButton = itemView.findViewById(R.id.infoButton);
        }

        /**
         * Заполняет View контакта данными из объекта Contact.
         */
        public void bind(final Contact contact, final OnInfoClickListener infoListener, final OnWarStatusClickListener warListener, final OnItemLongClickListener longListener) {
            // Индикатор онлайна
            int onlineColor = (contact.onlineStatus == 1) ? Color.GREEN : Color.RED;
            onlineStatusIndicator.setColorFilter(onlineColor);

            // Иконка склонности
            String inclinationUrl = null;
            switch (contact.inclination) {
                case 4: inclinationUrl = "http://image.neverlands.ru/signs/chaoss.gif"; break;
                case 3: inclinationUrl = "http://image.neverlands.ru/signs/sumers.gif"; break;
                case 2: inclinationUrl = "http://image.neverlands.ru/signs/lights.gif"; break;
                case 1: inclinationUrl = "http://image.neverlands.ru/signs/darks.gif"; break;
            }
            if (inclinationUrl != null) {
                inclinationIcon.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(inclinationUrl).into(inclinationIcon);
            } else {
                inclinationIcon.setVisibility(View.GONE);
            }

            // Иконка клана рядом с ником (теперь отображается всегда)
            if (contact.clanIco != null && !contact.clanIco.isEmpty()) {
                clanIcon.setVisibility(View.VISIBLE);
                String clanIconUrl = "http://image.neverlands.ru/signs/" + contact.clanIco;
                Glide.with(itemView.getContext()).load(clanIconUrl).into(clanIcon);
            } else {
                clanIcon.setVisibility(View.GONE);
            }

            // Статус боя
            if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
                warStatusText.setVisibility(View.VISIBLE);
                warStatusText.setOnClickListener(v -> warListener.onWarStatusClick(contact));
            } else {
                warStatusText.setVisibility(View.GONE);
            }

            // Формирование основной строки с ником, уровнем и локацией
            String nickAndLevel = contact.nick + " : [" + contact.playerLevel + "]";
            String location = "[" + contact.geoLocation + "]";
            contactNickText.setText(nickAndLevel);
            locationTextView.setText(location);

            // Окрашивание ника в зависимости от classId
            switch (contact.classId) {
                case 1: // Враг
                    contactNickText.setTextColor(Color.RED);
                    break;
                case 2: // Друг
                    contactNickText.setTextColor(Color.parseColor("#008000")); // Темно-зеленый
                    break;
                default: // Нейтрал
                    contactNickText.setTextColor(Color.parseColor("#FF6200EE"));
                    break;
            }

            // Установка обработчиков
            infoButton.setOnClickListener(v -> infoListener.onInfoClick(contact));
            itemView.setOnLongClickListener(v -> {
                longListener.onItemLongClick(contact);
                return true;
            });
        }
    }
}
