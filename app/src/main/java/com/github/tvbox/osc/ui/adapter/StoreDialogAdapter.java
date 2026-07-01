package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.StoreBean;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StoreDialogAdapter extends ListAdapter<StoreBean, StoreDialogAdapter.StoreViewHolder> {

    public interface StoreDialogInterface {
        void click(StoreBean store);

        void del(StoreBean store);
    }

    private final List<StoreBean> data = new ArrayList<>();
    private String selectedUrl = "";
    private final StoreDialogInterface dialogInterface;

    public StoreDialogAdapter(StoreDialogInterface dialogInterface) {
        super(new DiffUtil.ItemCallback<StoreBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull StoreBean oldItem, @NonNull @NotNull StoreBean newItem) {
                return oldItem.url.equals(newItem.url);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull StoreBean oldItem, @NonNull @NotNull StoreBean newItem) {
                return oldItem.name.equals(newItem.name) && oldItem.url.equals(newItem.url);
            }
        });
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<StoreBean> newData, String currentUrl) {
        data.clear();
        if (newData != null) {
            data.addAll(newData);
        }
        selectedUrl = currentUrl == null ? "" : currentUrl;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new StoreViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_store, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        StoreBean store = data.get(position);
        String display = store.name;
        if (selectedUrl.equals(store.url)) {
            display = "√ " + display;
        }
        ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(display);
        holder.itemView.findViewById(R.id.tvName).setOnClickListener(v -> {
            if (selectedUrl.equals(store.url)) {
                return;
            }
            int oldIndex = findIndexByUrl(selectedUrl);
            selectedUrl = store.url;
            if (oldIndex >= 0) {
                notifyItemChanged(oldIndex);
            }
            notifyItemChanged(position);
            dialogInterface.click(store);
        });
        holder.itemView.findViewById(R.id.tvDel).setOnClickListener(v -> {
            if (selectedUrl.equals(store.url)) {
                return;
            }
            int index = data.indexOf(store);
            data.remove(store);
            notifyItemRemoved(index);
            dialogInterface.del(store);
        });
    }

    private int findIndexByUrl(String url) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).url.equals(url)) {
                return i;
            }
        }
        return -1;
    }

    static class StoreViewHolder extends RecyclerView.ViewHolder {
        StoreViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
