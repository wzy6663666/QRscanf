package com.qrscanf.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.qrscanf.app.ui.HomeFragment;
import com.qrscanf.app.ui.RecordsFragment;
import com.qrscanf.app.ui.ScanFragment;
import com.qrscanf.app.ui.SettingsFragment;
import com.qrscanf.app.ui.GenerateFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private FragmentManager fragmentManager;
    private boolean suppressNavSelection;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomNav = findViewById(R.id.bottom_nav);
        fragmentManager = getSupportFragmentManager();

        bottomNav.setOnItemSelectedListener(item -> {
            if (suppressNavSelection) {
                return true;
            }
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showHome();
                return true;
            }
            if (id == R.id.nav_scan) {
                openScan(false);
                return false;
            }
            if (id == R.id.nav_records) {
                openRecords(0);
                return true;
            }
            if (id == R.id.nav_settings) {
                openSettings();
                return true;
            }
            return false;
        });

        fragmentManager.addOnBackStackChangedListener(() -> {
            boolean secondary = fragmentManager.getBackStackEntryCount() > 0;
            bottomNav.setVisibility(secondary ? View.GONE : View.VISIBLE);
            if (!secondary) {
                syncNavWithCurrentFragment();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fragmentManager.getBackStackEntryCount() > 0) {
                    fragmentManager.popBackStack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                    .replace(R.id.content_container, new HomeFragment())
                    .commit();
        } else {
            syncNavWithCurrentFragment();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (fragmentManager.getBackStackEntryCount() == 0) {
            bottomNav.setVisibility(View.VISIBLE);
            syncNavWithCurrentFragment();
        }
    }

    private void syncNavWithCurrentFragment() {
        Fragment fragment = fragmentManager.findFragmentById(R.id.content_container);
        int selected;
        if (fragment instanceof HomeFragment) {
            selected = R.id.nav_home;
        } else if (fragment instanceof RecordsFragment) {
            selected = R.id.nav_records;
        } else if (fragment instanceof SettingsFragment) {
            selected = R.id.nav_settings;
        } else {
            return;
        }
        suppressNavSelection = true;
        bottomNav.setSelectedItemId(selected);
        suppressNavSelection = false;
    }

    public void showHome() {
        if (fragmentManager.findFragmentById(R.id.content_container) instanceof HomeFragment
                && fragmentManager.getBackStackEntryCount() == 0) {
            return;
        }
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.content_container, new HomeFragment())
                .commit();
        bottomNav.setVisibility(View.VISIBLE);
    }

    public void openScan(boolean continuous) {
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.fade_out,
                        R.anim.fade_in, R.anim.slide_out_right)
                .replace(R.id.content_container, ScanFragment.newInstance(continuous))
                .addToBackStack("scan")
                .commit();
        bottomNav.setVisibility(View.GONE);
    }

    public void openGenerate() {
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.fade_out,
                        R.anim.fade_in, R.anim.slide_out_right)
                .replace(R.id.content_container, new GenerateFragment())
                .addToBackStack("generate")
                .commit();
        bottomNav.setVisibility(View.GONE);
    }

    public void openRecords(int tab) {
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        RecordsFragment fragment = RecordsFragment.newInstance(tab);
        fragmentManager.beginTransaction()
                .replace(R.id.content_container, fragment)
                .commit();
        bottomNav.setVisibility(View.VISIBLE);
        suppressNavSelection = true;
        bottomNav.setSelectedItemId(R.id.nav_records);
        suppressNavSelection = false;
    }

    public void openSettings() {
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(R.id.content_container, new SettingsFragment())
                .commit();
        bottomNav.setVisibility(View.VISIBLE);
        suppressNavSelection = true;
        bottomNav.setSelectedItemId(R.id.nav_settings);
        suppressNavSelection = false;
    }

    public void setStatusBar(int color, boolean lightIcons) {
        getWindow().setStatusBarColor(color);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightIcons);
    }

    public void applyDefaultStatusBar() {
        setStatusBar(Color.parseColor("#5B6CF9"), false);
    }
}
