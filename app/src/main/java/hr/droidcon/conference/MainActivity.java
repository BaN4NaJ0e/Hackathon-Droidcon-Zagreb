package hr.droidcon.conference;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.ChangeBounds;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import hr.droidcon.conference.adapters.MainAdapter;
import hr.droidcon.conference.adapters.MainTabAdapter;
import hr.droidcon.conference.objects.Conference;
import hr.droidcon.conference.timeline.Session;
import hr.droidcon.conference.timeline.Speaker;
import hr.droidcon.conference.timeline.TimelineAPI;
import hr.droidcon.conference.utils.PreferenceManager;
import hr.droidcon.conference.utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


/**
 * Main activity of the application, list all conferences slots into a listView
 *
 * @author Arnaud Camus
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private List<Conference> mConferences = new ArrayList<>();
    private Toolbar mToolbar;

    private int mTimeout = 10 * 60 * 1000; //  10 mins timeout for refreshing data

    private List<Speaker> mSpeakers;

    private static final String EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION";
    private static final String EXTRA_CUSTOM_TABS_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR";
    public static final String EXTRA_CUSTOM_TABS_EXIT_ANIMATION_BUNDLE =
            "android.support.customtabs.extra.EXIT_ANIMATION_BUNDLE";

    @Bind(R.id.main_tab_layout)
    TabLayout mainTabLayout;

    @Bind(R.id.main_view_pager)
    ViewPager mainViewPager;

    private MainTabAdapter mainTabAdapter;

    /**
     * Enable to share views across activities with animation on Android 5.0 Lollipop
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupLollipop() {
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
        getWindow().setSharedElementExitTransition(new ChangeBounds());
        getWindow().setSharedElementEnterTransition(new ChangeBounds());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Utils.isLollipop()) {
            setupLollipop();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar != null) {
            mToolbar.setTitle(getString(R.string.app_name));
            setSupportActionBar(mToolbar);
        }

        initTabs();

        // TODO: LOADING SPINNER
        // reading API moved to onResume
//        readCalendarAPI();

        trackOpening();
    }

    private void initTabs() {
        mainTabLayout.setTabTextColors(Color.parseColor("#64FFFFFF"), Color.WHITE);
        mainTabLayout.addTab(mainTabLayout.newTab()
                .setText("DAY 1"));
        mainTabLayout.addTab(mainTabLayout.newTab()
                .setText("DAY 2"));
        mainTabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        mainViewPager.setCurrentItem(((BaseApplication) getApplication()).getSelectedTab());

        mainViewPager.addOnPageChangeListener(
                new TabLayout.TabLayoutOnPageChangeListener(mainTabLayout));

        mainTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mainViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();

        readCalendarAPI();
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setToolbarElevation(int elevation) {
        if (Utils.isLollipop()) {
            mToolbar.setElevation(elevation);
        }
    }

    /**
     * fetches speakers and sessions and making a list of {@link Conference}.
     */
    public void readCalendarAPI() {
        // fetch speakers and sessions

        SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(this);


        if (prefs.getLong(Constants.PREFS_TIMEOUT_REFRESH, 0) + mTimeout <
                System.currentTimeMillis()) {
            Log.d("REFRESH", "5 minutes have passed, refreshing content");
            fetchSpeakers();
        }

        if (!getCachedContent()) {
            Log.d(TAG, "no cached content found! refreshing content");
            fetchSpeakers();
        }
    }

    private void fetchSpeakers() {
        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(Constants.SIMVELOP_ENDPOINT)
                .build();

        TimelineAPI timelineAPI = retrofit.create(TimelineAPI.class);

        Call<List<Speaker>> getSpeakers = timelineAPI.getSpeakers();

        getSpeakers.enqueue(new Callback<List<Speaker>>() {
            @Override
            public void onResponse(Call<List<Speaker>> call, Response<List<Speaker>> response) {

                if (response.isSuccessful()) {
                    mSpeakers = response.body();
                    fetchSessions();
                } else {
                    getCachedContent();
                }
            }

            @Override
            public void onFailure(Call<List<Speaker>> call, Throwable t) {
                Log.e("TAG", t.getMessage());
                getCachedContent();
                Toast.makeText(MainActivity.this, "No internet connection :(",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void fetchSessions() {
        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(Constants.SIMVELOP_ENDPOINT)
                .build();

        TimelineAPI timelineAPI = retrofit.create(TimelineAPI.class);

        Call<List<Session>> getSessions = timelineAPI.getSessions();
        final SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(this);

        getSessions.enqueue(new Callback<List<Session>>() {
            @Override
            public void onResponse(Call<List<Session>> call, Response<List<Session>> response) {

                if (response.isSuccessful()) {
                    mConferences.clear();

                    for (Session session : response.body()) {
                        addSession(session);
                    }

                    updateMainAdapterSessions();
                    cacheSessions();
                    prefs.edit()
                            .putLong(Constants.PREFS_TIMEOUT_REFRESH, System.currentTimeMillis())
                            .apply();
                } else {
                    getCachedContent();
                }
            }

            @Override
            public void onFailure(Call<List<Session>> call, Throwable t) {
                Log.e("TAG","" +  t.getMessage());
                getCachedContent();
                Toast.makeText(MainActivity.this, "No internet connection :(", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMainAdapterSessions() {
        mainTabAdapter = new MainTabAdapter(
                getSupportFragmentManager(),
                mainTabLayout.getTabCount(),
                mConferences
        );
        mainViewPager.setAdapter(mainTabAdapter);

        mainViewPager.setCurrentItem(((BaseApplication) getApplication()).getSelectedTab());

        mainViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                ((BaseApplication) getApplication()).setSelectedTab(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    private void cacheSessions() {

        SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(this);

        Gson gson = new Gson();
        String json = gson.toJson(mConferences);

        prefs.edit()
                .putString(Constants.PREFS_SESSIONS_CACHE, json)
                .apply();
    }

    private void addSession(Session session) {

        String imageURL = "";
        for (String speakerUID : session.getSpeakerUIDs()) {
            Speaker speaker = findSpeakerByUID(speakerUID);
            if (speaker != null) {
                imageURL = speaker.getImage();
            }
        }

        mConferences.add(new Conference(session, imageURL));
    }

    private Speaker findSpeakerByUID(String speakerUID) {

        for (Speaker speaker : mSpeakers) {
            if (speaker.getUid()
                    .equals(speakerUID)) {
                return speaker;
            }
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_more) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (id == R.id.action_news_twitter) {
            openUrl("https://mobile.twitter.com/droidconzg");
        } else if (id == R.id.action_news_fb) {
            openUrl("https://facebook.com/droidconzg/");
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Track how many times the Activity is launched and send a push notification {@link
     * hr.droidcon.conference.utils.SendNotification} to ask the user for feedback on the event.
     */
    private void trackOpening() {
        PreferenceManager prefManager =
                new PreferenceManager(getSharedPreferences("MyPref", Context.MODE_PRIVATE));
        long nb = prefManager.openingApp()
                .getOr(0L);
        prefManager.openingApp()
                .put(++nb)
                .apply();

        if (nb == 10) {
            // SendNotification.feedbackForm(this);
        }
    }

    private boolean getCachedContent() {

        SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.contains(Constants.PREFS_SESSIONS_CACHE)) {


            if (!mConferences.isEmpty()) {
                return false;
            }

            Gson gson = new Gson();
            String json = prefs.getString(Constants.PREFS_SESSIONS_CACHE, "");


            Type type = new TypeToken<List<Conference>>() {
            }.getType();

            mConferences = gson.fromJson(json, type);
            updateMainAdapterSessions();

            return true;
        } else {
            return false;
        }
    }
    private void openUrl(String url) {

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Bundle extras = new Bundle();
            extras.putBinder(EXTRA_CUSTOM_TABS_SESSION,
                    new ChromeBinder() /* Set to null for no session */);
            intent.putExtras(extras);
        }

        intent.putExtra(EXTRA_CUSTOM_TABS_TOOLBAR_COLOR, ContextCompat.getColor(this, R.color.colorPrimary));
        startActivity(intent);
    }
}
