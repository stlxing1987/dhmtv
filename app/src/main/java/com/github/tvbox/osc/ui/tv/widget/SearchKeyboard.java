package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.github.tvbox.osc.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchKeyboard extends FrameLayout {
    private RecyclerView mRecyclerView;
    private List<String> keys = Arrays.asList("远程搜索", "删除", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0");
    private boolean lettersOnly;
    private List<Keyboard> keyboardList = new ArrayList<>();
    private KeyboardAdapter keyboardAdapter;
    private OnSearchKeyListener searchKeyListener;
    private final OnFocusChangeListener focusChangeListener = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View itemView, boolean hasFocus) {
            if (itemView != null && itemView != mRecyclerView) {
                itemView.setSelected(hasFocus);
            }
        }
    };

    public SearchKeyboard(@NonNull Context context) {
        this(context, null);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_keyborad, this);
        mRecyclerView = view.findViewById(R.id.mRecyclerView);
        GridLayoutManager manager = new GridLayoutManager(getContext(), 6);
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                if (child.isFocusable() && child.getOnFocusChangeListener() == null) {
                    child.setOnFocusChangeListener(focusChangeListener);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View child) {
            }
        });
        rebuildKeys();
    }

    public void setLettersOnly(boolean lettersOnly) {
        if (this.lettersOnly == lettersOnly) {
            return;
        }
        this.lettersOnly = lettersOnly;
        rebuildKeys();
    }

    private void rebuildKeys() {
        keyboardList.clear();
        for (int i = 0; i < keys.size(); i++) {
            if (lettersOnly && i < 2) {
                continue;
            }
            keyboardList.add(new Keyboard(1, keys.get(i)));
        }
        if (keyboardAdapter == null) {
            keyboardAdapter = new KeyboardAdapter(keyboardList);
            mRecyclerView.setAdapter(keyboardAdapter);
            keyboardAdapter.setSpanSizeLookup(new BaseQuickAdapter.SpanSizeLookup() {
                @Override
                public int getSpanSize(GridLayoutManager gridLayoutManager, int position) {
                    if (!lettersOnly) {
                        if (position == 0 || position == 1) {
                            return 3;
                        }
                    }
                    return 1;
                }
            });
            keyboardAdapter.setOnItemClickListener((adapter, view, position) -> {
                Keyboard keyboard = (Keyboard) adapter.getItem(position);
                if (searchKeyListener != null && keyboard != null) {
                    searchKeyListener.onSearchKey(position, keyboard.getKey());
                }
            });
        } else {
            keyboardAdapter.setNewData(keyboardList);
        }
    }

    static class Keyboard implements MultiItemEntity {
        private final int itemType;
        private String key;

        private Keyboard(int itemType, String key) {
            this.itemType = itemType;
            this.key = key;
        }

        @Override
        public int getItemType() {
            return itemType;
        }

        public String getKey() {
            return key;
        }
    }

    private static class KeyboardAdapter extends BaseMultiItemQuickAdapter<Keyboard, BaseViewHolder> {
        private KeyboardAdapter(List<Keyboard> data) {
            super(data);
            addItemType(1, R.layout.item_keyboard);
        }

        @Override
        protected void convert(BaseViewHolder helper, Keyboard item) {
            helper.setText(R.id.keyName, item.key);
        }
    }

    public void setOnSearchKeyListener(OnSearchKeyListener listener) {
        searchKeyListener = listener;
    }

    public interface OnSearchKeyListener {
        void onSearchKey(int pos, String key);
    }
}
