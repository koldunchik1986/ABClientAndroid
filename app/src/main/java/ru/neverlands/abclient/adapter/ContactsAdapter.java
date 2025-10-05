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

public class ContactsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // --- Item types and models ---
    public static abstract class DisplayableItem {
        abstract public int getType();
        abstract public long getId();
    }

    public static class GroupHeaderItem extends DisplayableItem {
        public final String clanName;
        public final String clanIco;
        public final String clanLevel;
        public final int memberCount;
        public boolean isExpanded;

        public GroupHeaderItem(String clanName, String clanIco, String clanLevel, int memberCount) {
            this.clanName = clanName;
            this.clanIco = clanIco;
            this.clanLevel = clanLevel;
            this.memberCount = memberCount;
            this.isExpanded = true; // Groups are expanded by default
        }

        @Override
        public int getType() {
            return R.layout.list_item_contact_group_header;
        }

        @Override
        public long getId() {
            return clanName.hashCode();
        }
    }

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
            return contact.playerID.hashCode();
        }
    }

    // --- Adapter fields and interfaces ---

    private List<DisplayableItem> items;
    private final OnInfoClickListener onInfoClickListener;
    private final OnWarStatusClickListener onWarStatusClickListener;
    private final OnItemLongClickListener onItemLongClickListener;
    private final OnGroupClickListener onGroupClickListener;
    private final OnGroupLongClickListener onGroupLongClickListener;

    public interface OnInfoClickListener {
        void onInfoClick(Contact contact);
    }

    public interface OnWarStatusClickListener {
        void onWarStatusClick(Contact contact);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Contact contact);
    }

    public interface OnGroupClickListener {
        void onGroupClick(GroupHeaderItem groupHeaderItem);
    }

    public interface OnGroupLongClickListener {
        void onGroupLongClick(GroupHeaderItem groupHeaderItem);
    }

    public ContactsAdapter(List<DisplayableItem> items, OnInfoClickListener onInfoClickListener, OnWarStatusClickListener onWarStatusClickListener, OnItemLongClickListener onItemLongClickListener, OnGroupClickListener onGroupClickListener, OnGroupLongClickListener onGroupLongClickListener) {
        this.items = items;
        this.onInfoClickListener = onInfoClickListener;
        this.onWarStatusClickListener = onWarStatusClickListener;
        this.onItemLongClickListener = onItemLongClickListener;
        this.onGroupClickListener = onGroupClickListener;
        this.onGroupLongClickListener = onGroupLongClickListener;
        setHasStableIds(true);
    }

    // --- RecyclerView.Adapter overrides ---

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

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

    public void updateItems(List<DisplayableItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    // --- ViewHolders ---

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

        public void bind(final GroupHeaderItem group, final OnGroupClickListener groupClickListener, final OnGroupLongClickListener groupLongClickListener) {
            String headerText = group.clanName + " (Level: " + group.clanLevel + " Users: " + group.memberCount + ")";
            clanNameTextView.setText(headerText);

            if (group.clanIco != null && !group.clanIco.isEmpty()) {
                clanIconImageView.setVisibility(View.VISIBLE);
                String clanIconUrl = "http://image.neverlands.ru/signs/" + group.clanIco;
                Glide.with(itemView.getContext()).load(clanIconUrl).into(clanIconImageView);
            } else {
                clanIconImageView.setVisibility(View.INVISIBLE);
            }

            if (group.isExpanded) {
                expandIndicatorImageView.setImageResource(R.drawable.ic_expand_less);
            } else {
                expandIndicatorImageView.setImageResource(R.drawable.ic_expand_more);
            }
            itemView.setOnClickListener(v -> groupClickListener.onGroupClick(group));
            itemView.setOnLongClickListener(v -> {
                groupLongClickListener.onGroupLongClick(group);
                return true;
            });
        }
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final ImageView onlineStatusIndicator;
        private final ImageView inclinationIcon;
        private final ImageView clanIcon;
        private final TextView warStatusText;
        private final TextView contactNickText;
        private final ImageButton infoButton;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            onlineStatusIndicator = itemView.findViewById(R.id.onlineStatusIndicator);
            inclinationIcon = itemView.findViewById(R.id.inclinationIcon);
            clanIcon = itemView.findViewById(R.id.clanIcon);
            warStatusText = itemView.findViewById(R.id.warStatusText);
            contactNickText = itemView.findViewById(R.id.contactNickText);
            infoButton = itemView.findViewById(R.id.infoButton);
        }

        public void bind(final Contact contact, final OnInfoClickListener infoListener, final OnWarStatusClickListener warListener, final OnItemLongClickListener longListener) {
            int onlineColor = (contact.onlineStatus == 1) ? Color.GREEN : Color.RED;
            onlineStatusIndicator.setColorFilter(onlineColor);

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
            if (contact.clanIco != null && !contact.clanIco.isEmpty()) {
                clanIcon.setVisibility(View.VISIBLE);
                String clanIconUrl = "http://image.neverlands.ru/signs/" + contact.clanIco;
                Glide.with(itemView.getContext()).load(clanIconUrl).into(clanIcon);
            } else {
                // We don't show the clan icon next to the name anymore, it's in the group header
                clanIcon.setVisibility(View.GONE);
                }
            if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
                warStatusText.setVisibility(View.VISIBLE);
                warStatusText.setOnClickListener(v -> warListener.onWarStatusClick(contact));
            } else {
                warStatusText.setVisibility(View.GONE);
            }

            contactNickText.setText(contact.nick);

            switch (contact.classId) {
                case 1: // Foe
                    contactNickText.setTextColor(Color.RED);
                    break;
                case 2: // Friend
                    contactNickText.setTextColor(Color.parseColor("#008000")); // Dark Green
                    break;
                default: // Neutral
                    contactNickText.setTextColor(Color.parseColor("#FF6200EE")); //Purple_500
                    break;
            }
            infoButton.setOnClickListener(v -> infoListener.onInfoClick(contact));
            itemView.setOnLongClickListener(v -> {
                longListener.onItemLongClick(contact);
                return true;
            });
        }
    }
}