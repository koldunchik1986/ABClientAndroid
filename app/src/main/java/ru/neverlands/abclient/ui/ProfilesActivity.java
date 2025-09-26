package ru.neverlands.abclient.ui;

import android.app.Activity;
import android.os.Bundle;

// TODO: Заменить на androidx.appcompat.app.AppCompatActivity
// import androidx.recyclerview.widget.RecyclerView;
// import com.google.android.material.floatingactionbutton.FloatingActionButton;

import ru.neverlands.abclient.R;
import ru.neverlands.abclient.profile.ProfileManager;

public class ProfilesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);

        // RecyclerView recyclerView = findViewById(R.id.recycler_view_profiles);
        // FloatingActionButton fab = findViewById(R.id.fab_add_profile);

        // TODO: Инициализировать RecyclerView, Adapter и загрузить профили
        // List<UserConfig> profiles = ProfileManager.getInstance().loadAllProfiles(this);
        // ProfilesAdapter adapter = new ProfilesAdapter(profiles);
        // recyclerView.setAdapter(adapter);

        // fab.setOnClickListener(v -> {
        //     ProfileManager.getInstance().createNewProfile(this);
        // });
    }
}
