package com.example.tonbo_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class FunctionAdapter extends RecyclerView.Adapter<FunctionAdapter.FunctionViewHolder> {
    private ArrayList<HomeFunction> functionList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(HomeFunction function);
        void onItemFocus(HomeFunction function);
    }

    public FunctionAdapter(ArrayList<HomeFunction> functionList, OnItemClickListener listener) {
        this.functionList = functionList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FunctionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_function, parent, false);
        return new FunctionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FunctionViewHolder holder, int position) {
        HomeFunction function = functionList.get(position);
        holder.bind(function, listener);
    }

    @Override
    public int getItemCount() {
        return functionList.size();
    }

    public static class FunctionViewHolder extends RecyclerView.ViewHolder {
        private ImageView iconImageView;
        private TextView nameTextView;
        private TextView descriptionTextView;

        public FunctionViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.iconImageView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
        }

        public void bind(HomeFunction function, OnItemClickListener listener) {
            iconImageView.setImageResource(function.getIconResId());
            nameTextView.setText(function.getName());
            descriptionTextView.setText(function.getDescription());

            // 設置點擊監聽器
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onItemClick(function);
                    }
                }
            });

            // 設置焦點監聽器
            itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && listener != null) {
                        listener.onItemFocus(function);
                    }
                }
            });

            // 設置可獲取焦點，支持鍵盤導航
            itemView.setFocusable(true);
            itemView.setClickable(true);
        }
    }
}