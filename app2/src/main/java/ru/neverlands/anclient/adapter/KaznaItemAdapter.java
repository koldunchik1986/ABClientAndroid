package ru.neverlands.anclient.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

/**
 * RecyclerView adapter для вкладок `Все/Арты/Рары/Обычные`.
 *
 * Сделанные правки:
 * - рядом со строкой казны отображаются `KaznaItemDetails` из профильного кеша
 *   `info/<profile nick>/kazna/uids.txt`: картинка, свойства и fallback UID;
 * - details-кеш наполняется не здесь, а существующим inventory-пайплайном
 *   `InventoryParser.mainPhpInv(...)` / `syncKaznaItemDetailsCacheFromHtml(...)`;
 * - точное совпадение `KaznaItem.uid` остаётся главным ключом, а поиск по видимой
 *   сигнатуре применяется только для отображения свойств занятых/чужих строк;
 * - action-кнопки `Взять из казны` и `Пожертвовать` никогда не получают UID из
 *   fallback-details: они работают только с серверными `takeUrl/donateUrl`.
 *
 * Adapter не выполняет сетевых действий сам: он только сообщает Activity о
 * пользовательском действии. Так decision point остаётся в `KaznaActivity`, а
 * сеть и кеширование - в `KaznaManager`.
 */
public final class KaznaItemAdapter extends RecyclerView.Adapter<KaznaItemAdapter.ViewHolder> {
    public interface Listener {
        void onTakeClicked(KaznaItem item);
        void onDonateClicked(KaznaItem item);
        void onAddToSetRequested(KaznaItem item);
    }

    private final Listener listener;
    private final ArrayList<KaznaItem> items = new ArrayList<>();
    private final HashMap<String, KaznaItemDetails> detailsByUid = new HashMap<>();

    public KaznaItemAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<KaznaItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void submitDetails(Map<String, KaznaItemDetails> newDetails) {
        detailsByUid.clear();
        if (newDetails != null) {
            detailsByUid.putAll(newDetails);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_kazna, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KaznaItem item = items.get(position);
        holder.name.setText(item.displayName);
        holder.artifact.setText(item.hasArtifactCoefficient() ? item.artifactCoefficient : "");
        holder.artifact.setVisibility(item.hasArtifactCoefficient() ? View.VISIBLE : View.GONE);
        holder.meta.setText(buildMetaText(item));
        holder.meta.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                item.free ? R.color.teal_700 : R.color.colorTextSecondary));
        KaznaItemDetails details = findDetails(item);
        holder.uid.setText(buildUidText(item, details));
        bindDetails(holder, details);

        holder.take.setVisibility(item.hasTakeAction() ? View.VISIBLE : View.GONE);
        holder.donate.setVisibility(item.hasDonateAction() ? View.VISIBLE : View.GONE);
        holder.take.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTakeClicked(item);
            }
        });
        holder.donate.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDonateClicked(item);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onAddToSetRequested(item);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static String buildMetaText(KaznaItem item) {
        if (item == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!item.owner.isEmpty()) {
            sb.append("В-инвентаре: ").append(item.owner);
        }
        if (!item.durabilityText.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("Долговечность: ").append(item.durabilityText);
        }
        if (!item.status.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(item.status);
        }
        if (!item.categoryTitle.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(item.categoryTitle);
        }
        return sb.toString();
    }

    public static String buildUidText(KaznaItem item, KaznaItemDetails details) {
        if (item != null && item.hasUid()) {
            return "uid=" + item.uid;
        }
        if (details != null && !details.uid.isEmpty()) {
            return "uid=" + details.uid + " (из кеша инвентаря)";
        }
        return "uid не отдан сервером для этой строки";
    }

    private KaznaItemDetails findDetails(KaznaItem item) {
        return findDetails(item, detailsByUid);
    }

    public static KaznaItemDetails findDetails(KaznaItem item, Map<String, KaznaItemDetails> detailsByUid) {
        if (item == null) {
            return null;
        }
        // Казна и личный инвентарь могут отдавать разные UID для одинаковой вещи:
        // точный UID остаётся главным ключом, сигнатура нужна только как fallback.
        if (detailsByUid == null || detailsByUid.isEmpty()) {
            return null;
        }
        if (item.hasUid()) {
            KaznaItemDetails direct = detailsByUid.get(item.uid);
            if (direct != null) {
                return direct;
            }
        }
        return findDetailsByVisibleSignature(item, detailsByUid);
    }

    public static String resolveActionUid(KaznaItem item, Map<String, KaznaItemDetails> detailsByUid) {
        if (item != null && item.hasUid()) {
            return item.uid;
        }
        KaznaItemDetails details = findDetails(item, detailsByUid);
        return details == null ? "" : details.uid;
    }

    /**
     * Fallback-сопоставление по видимой сигнатуре вещи.
     *
     * Нужен для ситуации, когда строка казны `Взять из казны` имеет казённый UID
     * или вообще не имеет action-link, а похожая карточка уже была увидена в личном
     * инвентаре. Совпадение требует имени; для артов дополнительно проверяется
     * коэффициент, для всех вещей - долговечность, если она присутствует в properties.
     */
    private static KaznaItemDetails findDetailsByVisibleSignature(KaznaItem item, Map<String, KaznaItemDetails> detailsByUid) {
        String itemName = normalizeName(item.baseName.isEmpty() ? item.displayName : item.baseName);
        if (itemName.isEmpty()) {
            return null;
        }

        KaznaItemDetails best = null;
        int bestScore = 0;
        for (KaznaItemDetails details : detailsByUid.values()) {
            int score = scoreDetailsMatch(item, itemName, details);
            if (score > bestScore) {
                bestScore = score;
                best = details;
            }
        }
        return bestScore >= 100 ? best : null;
    }

    /**
     * Возвращает score совпадения без побочных эффектов.
     *
     * Порог `>= 100` выбран так, чтобы одного имени было недостаточно: нужно либо
     * подтверждение артового коэффициента, либо долговечность/полные свойства. Это
     * снижает риск показать свойства одноимённой, но другой вещи.
     */
    private static int scoreDetailsMatch(KaznaItem item, String itemName, KaznaItemDetails details) {
        if (details == null) {
            return 0;
        }
        String detailsName = normalizeName(details.name);
        if (detailsName.isEmpty() || !detailsName.equals(itemName)) {
            return 0;
        }

        String properties = normalizeSearchText(details.propertiesText);
        int score = 70;

        if (item.hasArtifactCoefficient()) {
            String coefficient = normalizeSearchText(item.artifactCoefficient);
            if (coefficient.isEmpty() || !properties.contains(coefficient)) {
                return 0;
            }
            score += 40;
        }

        if (!item.durabilityText.isEmpty()) {
            String durability = normalizeSearchText(item.durabilityText);
            if (!durability.isEmpty() && properties.contains(durability)) {
                score += 25;
            } else if (properties.contains("долговечность")) {
                return 0;
            }
        }

        if (details.hasProperties()) {
            score += 10;
        }
        if (details.hasImage()) {
            score += 5;
        }
        return score;
    }

    private static String normalizeName(String value) {
        return normalizeSearchText(value).replaceAll("(?<!\\d)[12]\\.\\d{2}(?!\\d)", "").trim();
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private void bindDetails(ViewHolder holder, KaznaItemDetails details) {
        if (details != null && details.hasImage()) {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image).load(details.imageUrl).into(holder.image);
        } else {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setVisibility(View.GONE);
        }

        if (details != null && details.hasProperties()) {
            holder.properties.setText("Свойства:\n" + details.propertiesText);
        } else {
            holder.properties.setText("Свойства: информация не известна");
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView artifact;
        final ImageView image;
        final TextView meta;
        final TextView uid;
        final TextView properties;
        final MaterialButton take;
        final MaterialButton donate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvKaznaItemName);
            artifact = itemView.findViewById(R.id.tvKaznaArtifact);
            image = itemView.findViewById(R.id.ivKaznaItemImage);
            meta = itemView.findViewById(R.id.tvKaznaMeta);
            uid = itemView.findViewById(R.id.tvKaznaUid);
            properties = itemView.findViewById(R.id.tvKaznaProperties);
            take = itemView.findViewById(R.id.btnKaznaTake);
            donate = itemView.findViewById(R.id.btnKaznaDonate);
        }
    }
}
