package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ApiHistoryDialog extends BaseDialog {
    private TvRecyclerView tvRecyclerView;
    private int selectedPosition;

    public ApiHistoryDialog(@NonNull @NotNull Context context) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_api_history);
        setupOutsideDismiss(findViewById(R.id.dialogRoot), findViewById(R.id.dialogContent));
        tvRecyclerView = findViewById(R.id.list);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    public void setAdapter(ApiHistoryDialogAdapter adapter, List<String> data, int select) {
        selectedPosition = Math.max(0, Math.min(select, data == null || data.isEmpty() ? 0 : data.size() - 1));
        adapter.setData(data, selectedPosition);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(selectedPosition);
        tvRecyclerView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectedPosition = position;
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                View nameView = itemView.findViewById(R.id.tvName);
                if (nameView != null) {
                    nameView.performClick();
                }
            }
        });
        requestListFocus();
    }

    public void setAdapter(ApiHistoryDialogAdapter.SelectDialogInterface sourceBeanSelectDialogInterface, List<String> data, int select) {
        ApiHistoryDialogAdapter adapter = new ApiHistoryDialogAdapter(sourceBeanSelectDialogInterface);
        setAdapter(adapter, data, select);
    }

    private void requestListFocus() {
        tvRecyclerView.post(() -> {
            tvRecyclerView.setSelectedPosition(selectedPosition);
            tvRecyclerView.scrollToPosition(selectedPosition);
            tvRecyclerView.requestFocus();
        });
    }

    @Override
    public void show() {
        super.show();
        requestListFocus();
    }
}
