package com.example.tonbo_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class FunctionListFragment extends Fragment {
    private RecyclerView recyclerView;
    private FunctionAdapter adapter;
    private ArrayList<HomeFunction> functionList;
    private String currentLanguage;
    private VibrationManager vibrationManager;
    private TTSManager ttsManager;
    
    public interface OnFunctionClickListener {
        void onFunctionClick(HomeFunction function);
    }
    
    private OnFunctionClickListener clickListener;
    
    public static FunctionListFragment newInstance(String language, ArrayList<HomeFunction> functions) {
        FunctionListFragment fragment = new FunctionListFragment();
        Bundle args = new Bundle();
        args.putString("language", language);
        args.putSerializable("functions", functions);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentLanguage = getArguments().getString("language");
            functionList = (ArrayList<HomeFunction>) getArguments().getSerializable("functions");
        }
        vibrationManager = VibrationManager.getInstance(getContext());
        ttsManager = TTSManager.getInstance(getContext());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_function_list, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
    }
    
    private void setupRecyclerView() {
        // 創建監聽器
        FunctionAdapter.OnItemClickListener adapterListener = new FunctionAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(HomeFunction function) {
                vibrationManager.vibrateClick();
                String announcement = (currentLanguage.equals("english") ? "Starting " : "正在啟動") + function.getName();
                ttsManager.speak(announcement, announcement, true);
                
                if (clickListener != null) {
                    clickListener.onFunctionClick(function);
                }
            }
            
            @Override
            public void onItemFocus(HomeFunction function) {
                vibrationManager.vibrateFocus();
                String cantoneseText = "當前焦點：" + function.getName() + "，" + function.getDescription();
                String englishText = "Current focus: " + function.getName() + ", " + function.getDescription();
                ttsManager.speak(cantoneseText, englishText);
            }
        };
        
        adapter = new FunctionAdapter(functionList, adapterListener);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }
    
    public void setOnFunctionClickListener(OnFunctionClickListener listener) {
        this.clickListener = listener;
    }
}

