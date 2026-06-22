package com.example.tonbo_app;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.ArrayList;

public class FunctionPagerAdapter extends FragmentStateAdapter {
    private ArrayList<ArrayList<HomeFunction>> functionPages;
    private String currentLanguage;
    
    public FunctionPagerAdapter(FragmentActivity activity, ArrayList<ArrayList<HomeFunction>> functionPages, String currentLanguage) {
        super(activity);
        this.functionPages = functionPages;
        this.currentLanguage = currentLanguage;
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return FunctionListFragment.newInstance(currentLanguage, functionPages.get(position));
    }
    
    @Override
    public int getItemCount() {
        return functionPages.size();
    }
}

