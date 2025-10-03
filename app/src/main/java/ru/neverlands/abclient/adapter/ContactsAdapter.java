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

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {

    private List<Contact> contacts;
    private final OnInfoClickListener onInfoClickListener;
    private final OnWarStatusClickListener onWarStatusClickListener;
    private final OnItemLongClickListener onItemLongClickListener;

    public interface OnInfoClickListener {
        void onInfoClick(Contact contact);
    }

    public interface OnWarStatusClickListener {
        void onWarStatusClick(Contact contact);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Contact contact);
    }

    public ContactsAdapter(List<Contact> contacts, OnInfoClickListener onInfoClickListener, OnWarStatusClickListener onWarStatusClickListener, OnItemLongClickListener onItemLongClickListener) {
        this.contacts = contacts;
        this.onInfoClickListener = onInfoClickListener;
        this.onWarStatusClickListener = onWarStatusClickListener;
        this.onItemLongClickListener = onItemLongClickListener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_list_item_v2, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = contacts.get(position);
        holder.bind(contact, onInfoClickListener, onWarStatusClickListener, onItemLongClickListener);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void updateContacts(List<Contact> newContacts) {
        this.contacts = newContacts;
        notifyDataSetChanged();
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
                clanIcon.setVisibility(View.GONE);
            }

            if (contact.warLogNumber != null && !contact.warLogNumber.equals("0") && !contact.warLogNumber.isEmpty()) {
                warStatusText.setVisibility(View.VISIBLE);
                warStatusText.setOnClickListener(v -> warListener.onWarStatusClick(contact));
            } else {
                warStatusText.setVisibility(View.GONE);
            }

            contactNickText.setText(contact.nick);
            infoButton.setOnClickListener(v -> infoListener.onInfoClick(contact));
            itemView.setOnLongClickListener(v -> {
                longListener.onItemLongClick(contact);
                return true;
            });
        }
    }
}
