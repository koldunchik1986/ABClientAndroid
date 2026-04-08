package ru.neverlands.abclient.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.model.Contact;
import ru.neverlands.abclient.utils.ContactRenderHelper;

public class ContactsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static abstract class DisplayableItem {
        abstract public int getType();
        abstract public long getId();
    }

    public static class GroupHeaderItem extends DisplayableItem {
        public final String groupKey;
        public final String clanName;
        public final String clanIco;
        public final String clanLevel;
        public final int totalMemberCount;
        public final int onlineMemberCount;
        public final int groupClassId;
        public final boolean isNeutralGroup;
        public boolean isExpanded;

        public GroupHeaderItem(String clanName,
                               String clanIco,
                               String clanLevel,
                               int totalMemberCount,
                               int onlineMemberCount,
                               int groupClassId) {
            this(clanName, clanIco, clanLevel, totalMemberCount, onlineMemberCount, groupClassId, false, clanName);
        }

        public GroupHeaderItem(String clanName,
                               String clanIco,
                               String clanLevel,
                               int totalMemberCount,
                               int onlineMemberCount,
                               int groupClassId,
                               boolean isNeutralGroup,
                               String groupKey) {
            this.clanName = clanName;
            this.clanIco = clanIco;
            this.clanLevel = clanLevel;
            this.totalMemberCount = totalMemberCount;
            this.onlineMemberCount = onlineMemberCount;
            this.groupClassId = groupClassId;
            this.isNeutralGroup = isNeutralGroup;
            this.groupKey = (groupKey == null || groupKey.trim().isEmpty()) ? clanName : groupKey;
            this.isExpanded = true;
        }

        @Override
        public int getType() {
            return R.layout.list_item_contact_group_header;
        }

        @Override
        public long getId() {
            return groupKey.hashCode();
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

    private List<DisplayableItem> items;
    private final OnInfoClickListener onInfoClickListener;
    private final OnWarStatusClickListener onWarStatusClickListener;
    private final OnItemLongClickListener onItemLongClickListener;
    private final OnGroupClickListener onGroupClickListener;
    private final OnGroupLongClickListener onGroupLongClickListener;
    private final OnGroupClassIdChangeListener onGroupClassIdChangeListener;

    public interface OnInfoClickListener { void onInfoClick(Contact contact); }
    public interface OnWarStatusClickListener { void onWarStatusClick(Contact contact); }
    public interface OnItemLongClickListener { void onItemLongClick(Contact contact); }
    public interface OnGroupClickListener { void onGroupClick(GroupHeaderItem groupHeaderItem); }
    public interface OnGroupLongClickListener { void onGroupLongClick(GroupHeaderItem groupHeaderItem); }
    public interface OnGroupClassIdChangeListener { void onClassIdChanged(GroupHeaderItem group, int newClassId); }

    public ContactsAdapter(List<DisplayableItem> items,
                           OnInfoClickListener onInfoClickListener,
                           OnWarStatusClickListener onWarStatusClickListener,
                           OnItemLongClickListener onItemLongClickListener,
                           OnGroupClickListener onGroupClickListener,
                           OnGroupLongClickListener onGroupLongClickListener,
                           OnGroupClassIdChangeListener onGroupClassIdChangeListener) {
        this.items = items;
        this.onInfoClickListener = onInfoClickListener;
        this.onWarStatusClickListener = onWarStatusClickListener;
        this.onItemLongClickListener = onItemLongClickListener;
        this.onGroupClickListener = onGroupClickListener;
        this.onGroupLongClickListener = onGroupLongClickListener;
        this.onGroupClassIdChangeListener = onGroupClassIdChangeListener;
        setHasStableIds(true);
    }

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
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_list_item_v2, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof GroupHeaderViewHolder) {
            ((GroupHeaderViewHolder) holder).bind(
                    (GroupHeaderItem) items.get(position),
                    onGroupClickListener,
                    onGroupLongClickListener,
                    onGroupClassIdChangeListener
            );
            return;
        }
        if (holder instanceof ContactViewHolder) {
            ((ContactViewHolder) holder).bind(
                    ((ContactItem) items.get(position)).contact,
                    onInfoClickListener,
                    onWarStatusClickListener,
                    onItemLongClickListener
            );
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

    static class GroupHeaderViewHolder extends RecyclerView.ViewHolder {
        private final ImageView clanIconImageView;
        private final TextView clanNameTextView;
        private final TextView groupStatsTextView;
        private final ImageView expandIndicatorImageView;
        private final Spinner groupClassIdSpinner;

        GroupHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            clanIconImageView = itemView.findViewById(R.id.clanIconImageView);
            clanNameTextView = itemView.findViewById(R.id.clanNameTextView);
            groupStatsTextView = itemView.findViewById(R.id.groupStatsTextView);
            expandIndicatorImageView = itemView.findViewById(R.id.expandIndicatorImageView);
            groupClassIdSpinner = itemView.findViewById(R.id.groupClassIdSpinner);
        }

        void bind(final GroupHeaderItem group,
                  final OnGroupClickListener groupClickListener,
                  final OnGroupLongClickListener groupLongClickListener,
                  final OnGroupClassIdChangeListener classIdChangeListener) {
            clanNameTextView.setText(group.clanName);
            groupStatsTextView.setText(String.format("Level: %s Users: %d/%d",
                    group.clanLevel,
                    group.onlineMemberCount,
                    group.totalMemberCount));

            if (group.clanIco != null && !group.clanIco.isEmpty()) {
                clanIconImageView.setVisibility(View.VISIBLE);
                String clanIconUrl = "http://image.neverlands.ru/signs/" + group.clanIco;
                Glide.with(itemView.getContext()).load(clanIconUrl).into(clanIconImageView);
            } else {
                clanIconImageView.setVisibility(View.INVISIBLE);
            }

            expandIndicatorImageView.setImageResource(group.isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            setupSpinner(group, classIdChangeListener);

            itemView.setOnClickListener(v -> groupClickListener.onGroupClick(group));
            itemView.setOnLongClickListener(v -> {
                groupLongClickListener.onGroupLongClick(group);
                return true;
            });
        }

        private void setupSpinner(final GroupHeaderItem group, final OnGroupClassIdChangeListener classIdChangeListener) {
            Context context = itemView.getContext();
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context,
                    android.R.layout.simple_spinner_item,
                    new String[]{"Нейтрал", "Враг", "Друг"}
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            groupClassIdSpinner.setAdapter(adapter);
            groupClassIdSpinner.setSelection(group.groupClassId, false);
            groupClassIdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position != group.groupClassId) {
                        classIdChangeListener.onClassIdChanged(group, position);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final ImageView onlineStatusIndicator;
        private final ImageView inclinationIcon;
        private final ImageView clanIcon;
        private final ImageView autoAttackToolIcon;
        private final LinearLayout effectsContainer;
        private final TextView warStatusText;
        private final TextView contactNickText;
        private final TextView locationTextView;
        private final ImageButton infoButton;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            onlineStatusIndicator = itemView.findViewById(R.id.onlineStatusIndicator);
            inclinationIcon = itemView.findViewById(R.id.inclinationIcon);
            clanIcon = itemView.findViewById(R.id.clanIcon);
            autoAttackToolIcon = itemView.findViewById(R.id.autoAttackToolIcon);
            effectsContainer = itemView.findViewById(R.id.effectsContainer);
            warStatusText = itemView.findViewById(R.id.warStatusText);
            contactNickText = itemView.findViewById(R.id.contactNickText);
            locationTextView = itemView.findViewById(R.id.locationTextView);
            infoButton = itemView.findViewById(R.id.infoButton);
        }

        void bind(final Contact contact,
                  final OnInfoClickListener infoListener,
                  final OnWarStatusClickListener warListener,
                  final OnItemLongClickListener longListener) {
            onlineStatusIndicator.setColorFilter(contact.onlineStatus == 1 ? Color.GREEN : Color.RED);

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
                clanIcon.setVisibility(View.GONE);
            }

            if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
                warStatusText.setVisibility(View.VISIBLE);
                warStatusText.setOnClickListener(v -> warListener.onWarStatusClick(contact));
            } else {
                warStatusText.setVisibility(View.GONE);
            }

            contactNickText.setText(ContactRenderHelper.formatNickWithLevel(contact.nick, contact.playerLevel));
            contactNickText.setTextColor(ContactRenderHelper.resolveNickColor(contact.classId, contact.clanName));
            locationTextView.setText(contact.geoLocation);

            String toolIconUrl = getAutoAttackToolIconUrl(contact.toolId);
            if (toolIconUrl != null) {
                autoAttackToolIcon.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(toolIconUrl).into(autoAttackToolIcon);
            } else {
                autoAttackToolIcon.setVisibility(View.GONE);
            }

            renderEffectIcons(contact);

            infoButton.setOnClickListener(v -> infoListener.onInfoClick(contact));
            itemView.setOnLongClickListener(v -> {
                longListener.onItemLongClick(contact);
                return true;
            });
        }

        private void renderEffectIcons(Contact contact) {
            effectsContainer.removeAllViews();
            List<ContactRenderHelper.EffectState> effectStates =
                    ContactRenderHelper.parseEffectStatesCsv(contact.effectStates, contact.effectIds);
            if (effectStates == null || effectStates.isEmpty()) {
                effectsContainer.setVisibility(View.GONE);
                return;
            }

            Context context = itemView.getContext();
            for (ContactRenderHelper.EffectState effectState : effectStates) {
                if (effectState == null || effectState.id <= 0) {
                    continue;
                }
                ImageView icon = new ImageView(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16));
                params.setMarginStart(dp(context, 3));
                icon.setLayoutParams(params);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Glide.with(context)
                        .load(ContactRenderHelper.buildEffectIconUrl(effectState.id))
                        .into(icon);
                effectsContainer.addView(icon);

                TextView counter = new TextView(context);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                textParams.setMarginStart(dp(context, 2));
                counter.setLayoutParams(textParams);
                counter.setTextColor(Color.WHITE);
                counter.setTextSize(10);
                counter.setSingleLine(false);
                counter.setLines(2);
                counter.setText(ContactRenderHelper.formatEffectCounter(effectState));
                effectsContainer.addView(counter);
            }
            effectsContainer.setVisibility(effectsContainer.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }

        private int dp(Context context, int value) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }

        private String getAutoAttackToolIconUrl(int toolId) {
            switch (toolId) {
                case 1:
                    return "http://image.neverlands.ru/weapon/i_w28_26.gif";
                case 2:
                    return "http://image.neverlands.ru/weapon/i_w28_26.gif";
                case 3:
                    return "http://image.neverlands.ru/weapon/i_w28_24.gif";
                case 4:
                    return "http://image.neverlands.ru/weapon/i_w28_25.gif";
                case 5:
                    return "http://image.neverlands.ru/weapon/i_w28_86.gif";
                case 6:
                    return "http://image.neverlands.ru/weapon/i_w27_41.gif";
                case 7:
                    return "http://image.neverlands.ru/weapon/i_w27_52.gif";
                default:
                    return null;
            }
        }
    }
}
