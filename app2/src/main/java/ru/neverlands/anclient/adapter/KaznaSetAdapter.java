package ru.neverlands.anclient.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ru.neverlands.anclient.R;
import ru.neverlands.anclient.model.KaznaItem;
import ru.neverlands.anclient.model.KaznaItemDetails;
import ru.neverlands.anclient.model.KaznaSet;
import ru.neverlands.anclient.model.KaznaSnapshot;

/**
 * Adapter локальных комплектов казны.
 *
 * Комплект хранит UID, а не копию HTML-строки. Это предотвращает stale-данные:
 * перед `Собрать` менеджер ищет свежий action-link по UID в текущем snapshot,
 * а UI карточки берёт свойства/картинку из уже существующего inventory-кеша.
 */
public final class KaznaSetAdapter extends RecyclerView.Adapter<KaznaSetAdapter.ViewHolder> {
    public interface Listener {
        void onCollectClicked(KaznaSet set);
        void onWearClicked(KaznaSet set);
        void onDeleteClicked(KaznaSet set);
        void onRemoveItemClicked(KaznaSet set, String uid);
    }

    private final Listener listener;
    private final ArrayList<KaznaSet> sets = new ArrayList<>();
    private final HashMap<String, KaznaItemDetails> detailsByUid = new HashMap<>();
    private KaznaSnapshot snapshot;

    public KaznaSetAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<KaznaSet> newSets) {
        sets.clear();
        if (newSets != null) {
            sets.addAll(newSets);
        }
        notifyDataSetChanged();
    }

    public void submitContext(@Nullable Map<String, KaznaItemDetails> details, @Nullable KaznaSnapshot snapshot) {
        detailsByUid.clear();
        if (details != null) {
            detailsByUid.putAll(details);
        }
        this.snapshot = snapshot;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_kazna_set, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KaznaSet set = sets.get(position);
        holder.name.setText(set.name);
        bindItems(holder.items, set);
        holder.collect.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCollectClicked(set);
            }
        });
        holder.wear.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWearClicked(set);
            }
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClicked(set);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sets.size();
    }

    private void bindItems(LinearLayout container, KaznaSet set) {
        container.removeAllViews();
        List<String> uids = set == null ? null : set.itemUids;
        if (uids == null || uids.isEmpty()) {
            TextView empty = createEmptyText(container, "Комплект пуст");
            container.addView(empty);
            return;
        }
        for (String uid : uids) {
            container.addView(createSetItemCard(container, set, uid));
        }
    }

    private View createSetItemCard(LinearLayout parent, KaznaSet set, String uid) {
        View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_kazna, parent, false);
        String safeUid = uid == null ? "" : uid.trim();
        KaznaItemDetails details = detailsByUid.get(safeUid);
        KaznaItem item = findSnapshotItem(safeUid, details);
        if (details == null) {
            details = findDetails(item, safeUid);
        }

        TextView name = card.findViewById(R.id.tvKaznaItemName);
        TextView artifact = card.findViewById(R.id.tvKaznaArtifact);
        ImageView image = card.findViewById(R.id.ivKaznaItemImage);
        TextView meta = card.findViewById(R.id.tvKaznaMeta);
        TextView uidText = card.findViewById(R.id.tvKaznaUid);
        TextView properties = card.findViewById(R.id.tvKaznaProperties);
        MaterialButton donate = card.findViewById(R.id.btnKaznaDonate);
        MaterialButton remove = card.findViewById(R.id.btnKaznaTake);

        name.setText(buildSetItemTitle(item, details));
        artifact.setText(item != null && item.hasArtifactCoefficient() ? item.artifactCoefficient : "");
        artifact.setVisibility(item != null && item.hasArtifactCoefficient() ? View.VISIBLE : View.GONE);
        meta.setText(buildInventoryOwnerText(item));
        meta.setTextColor(ContextCompat.getColor(
                parent.getContext(),
                item != null && item.free ? R.color.teal_700 : R.color.colorTextSecondary));
        uidText.setText(buildUidText(item, details, safeUid));
        bindImage(image, details);
        properties.setText(details != null && details.hasProperties()
                ? "Свойства:\n" + details.propertiesText
                : "Свойства: информация не известна");

        donate.setVisibility(View.GONE);
        remove.setVisibility(View.VISIBLE);
        remove.setText("✕");
        remove.setTextColor(Color.WHITE);
        remove.setTextSize(15f);
        remove.setMinWidth(0);
        remove.setMinHeight(0);
        remove.setInsetTop(0);
        remove.setInsetBottom(0);
        remove.setPadding(dp(parent, 10), 0, dp(parent, 10), 0);
        remove.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(parent.getContext(), R.color.colorDangerText)));
        remove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveItemClicked(set, safeUid);
            }
        });
        return card;
    }

    private TextView createEmptyText(LinearLayout parent, String textValue) {
        TextView text = new TextView(parent.getContext());
        text.setText(textValue);
        text.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.colorTextSecondary));
        text.setTextSize(13f);
        return text;
    }

    private KaznaItem findSnapshotItem(String uid, @Nullable KaznaItemDetails details) {
        if (snapshot == null) {
            return null;
        }
        KaznaItem direct = snapshot.findItemByUid(uid);
        if (direct != null) {
            return direct;
        }
        if (details == null || details.uid.isEmpty()) {
            return null;
        }
        for (KaznaItem candidate : snapshot.readonlyItems()) {
            KaznaItemDetails matched = KaznaItemAdapter.findDetails(candidate, detailsByUid);
            if (matched != null && details.uid.equals(matched.uid)) {
                return candidate;
            }
        }
        return null;
    }

    private KaznaItemDetails findDetails(@Nullable KaznaItem item, String uid) {
        KaznaItemDetails direct = detailsByUid.get(uid);
        if (direct != null) {
            return direct;
        }
        return item == null ? null : KaznaItemAdapter.findDetails(item, detailsByUid);
    }

    private void bindImage(ImageView image, @Nullable KaznaItemDetails details) {
        if (details != null && details.hasImage()) {
            image.setVisibility(View.VISIBLE);
            Glide.with(image).load(details.imageUrl).into(image);
        } else {
            Glide.with(image).clear(image);
            image.setVisibility(View.GONE);
        }
    }

    private String buildSetItemTitle(@Nullable KaznaItem item, @Nullable KaznaItemDetails details) {
        String title = firstNonEmpty(item == null ? "" : item.displayName, details == null ? "" : details.name, "Предмет");
        String engraving = extractEngraving(details);
        if (!engraving.isEmpty()) {
            title += " (" + engraving + ")";
        }
        return title;
    }

    private String buildInventoryOwnerText(@Nullable KaznaItem item) {
        if (item != null && !item.owner.isEmpty()) {
            return "В-инвентаре: " + item.owner;
        }
        return item == null ? "В-инвентаре: нет данных текущей казны" : "В-инвентаре: не указано";
    }

    private String buildUidText(@Nullable KaznaItem item, @Nullable KaznaItemDetails details, String uid) {
        if (item != null) {
            return KaznaItemAdapter.buildUidText(item, details);
        }
        if (details != null && !details.uid.isEmpty()) {
            return "uid=" + details.uid + " (из кеша инвентаря)";
        }
        return uid.isEmpty() ? "uid не известен" : "uid=" + uid;
    }

    private String extractEngraving(@Nullable KaznaItemDetails details) {
        if (details == null || !details.hasProperties()) {
            return "";
        }
        String[] lines = details.propertiesText.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line == null ? "" : line.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
            if (cleaned.toLowerCase(Locale.ROOT).startsWith("гравировка:")) {
                return cleaned.substring("Гравировка:".length()).trim();
            }
        }
        return "";
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private int dp(LinearLayout parent, int value) {
        return (int) (value * parent.getResources().getDisplayMetrics().density);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final LinearLayout items;
        final MaterialButton collect;
        final MaterialButton wear;
        final MaterialButton delete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvKaznaSetName);
            items = itemView.findViewById(R.id.layoutKaznaSetItems);
            collect = itemView.findViewById(R.id.btnKaznaSetCollect);
            wear = itemView.findViewById(R.id.btnKaznaSetWear);
            delete = itemView.findViewById(R.id.btnKaznaSetDelete);
        }
    }
}
