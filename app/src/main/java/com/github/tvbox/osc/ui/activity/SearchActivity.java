package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.adapter.SearchHotAdapter;
import com.github.tvbox.osc.ui.adapter.SearchSourceFilterAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PinyinSearchHelper;
import com.github.tvbox.osc.util.SearchHistoryHelper;
import com.github.tvbox.osc.util.SearchHotHelper;
import com.github.tvbox.osc.util.SearchSuggestHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private static final int MODE_BROWSE = 0;
    private static final int MODE_RESULT = 1;

    private LinearLayout llLayout;
    private LinearLayout llHistoryPanel;
    private LinearLayout llSourcePanel;
    private LinearLayout llCenterPanel;
    private LinearLayout llHotPanel;
    private LinearLayout llResultPanel;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    private TvRecyclerView mGridViewHistory;
    private TvRecyclerView mGridViewHot;
    private TvRecyclerView mGridViewSource;
    private TextView tvSearchCount;
    private TextView tvClearHistory;

    SourceViewModel sourceViewModel;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private TextView tvRemote;
    private TextView tvDelete;
    private TextView tvAddress;
    private ImageView ivQRCode;
    private SearchKeyboard keyboard;

    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private PinyinAdapter historyAdapter;
    private SearchHotAdapter hotAdapter;
    private SearchSourceFilterAdapter sourceFilterAdapter;

    private String searchTitle = "";
    private final ArrayList<String> searchCorpus = new ArrayList<>();
    private List<String> searchKeywords = new ArrayList<>();
    private List<SearchHotHelper.Section> hotSections = new ArrayList<>();
    private final List<Movie.Video> allSearchResults = new ArrayList<>();
    private String selectedSourceKey = null;
    private int displayMode = MODE_BROWSE;

    private List<Runnable> pauseRunnable = null;
    private ExecutorService searchExecutorService = null;
    private AtomicInteger allRunCount = new AtomicInteger(0);

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (displayMode == MODE_RESULT && !allSearchResults.isEmpty()) {
            showResultMode();
            filterAndDisplayResults();
        }
        if (pauseRunnable != null && !pauseRunnable.isEmpty()) {
            searchExecutorService = Executors.newFixedThreadPool(getSearchThreadCount());
            allRunCount.set(pauseRunnable.size());
            for (Runnable runnable : pauseRunnable) {
                searchExecutorService.execute(runnable);
            }
            pauseRunnable.clear();
            pauseRunnable = null;
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvClear = findViewById(R.id.tvClear);
        mGridView = findViewById(R.id.mGridView);
        keyboard = findViewById(R.id.keyBoardRoot);

        searchAdapter = new SearchAdapter();
        mGridView.setHasFixedSize(true);
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    stopSearchExecutor();
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    jumpActivity(DetailActivity.class, bundle);
                }
            }
        });

        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                triggerSearch();
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                etSearch.setText("");
                showDefaultHot();
            }
        });

        initTvView();
    }

    private void initTvView() {
        llHistoryPanel = findViewById(R.id.llHistoryPanel);
        llSourcePanel = findViewById(R.id.llSourcePanel);
        llCenterPanel = findViewById(R.id.llCenterPanel);
        llHotPanel = findViewById(R.id.llHotPanel);
        llResultPanel = findViewById(R.id.llResultPanel);
        tvRemote = findViewById(R.id.tvRemote);
        tvDelete = findViewById(R.id.tvDelete);
        tvSearchCount = findViewById(R.id.tvSearchCount);
        tvClearHistory = findViewById(R.id.tvClearHistory);
        keyboard.setLettersOnly(true);

        mGridViewHistory = findViewById(R.id.mGridViewHistory);
        mGridViewHistory.setHasFixedSize(true);
        mGridViewHistory.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        historyAdapter = new PinyinAdapter();
        historyAdapter.setNewData(SearchHistoryHelper.getHistory());
        mGridViewHistory.setAdapter(historyAdapter);
        historyAdapter.setOnItemClickListener((adapter, view, position) -> {
            String word = historyAdapter.getItem(position);
            etSearch.setText(word);
            search(word);
        });

        mGridViewHot = findViewById(R.id.mGridViewHot);
        mGridViewHot.setHasFixedSize(true);
        hotAdapter = new SearchHotAdapter();
        V7GridLayoutManager hotLayoutManager = new V7GridLayoutManager(this.mContext, 4);
        hotLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return hotAdapter.getItemViewType(position) == SearchHotAdapter.TYPE_HEADER ? 4 : 1;
            }
        });
        mGridViewHot.setLayoutManager(hotLayoutManager);
        mGridViewHot.setAdapter(hotAdapter);
        hotAdapter.setOnItemClickListener((adapter, view, position) -> {
            SearchHotAdapter.HotItem item = hotAdapter.getItem(position);
            if (item != null && item.getItemType() == SearchHotAdapter.TYPE_WORD) {
                etSearch.setText(item.text);
                search(item.text);
            }
        });

        mGridViewSource = findViewById(R.id.mGridViewSource);
        mGridViewSource.setHasFixedSize(true);
        mGridViewSource.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        sourceFilterAdapter = new SearchSourceFilterAdapter();
        mGridViewSource.setAdapter(sourceFilterAdapter);
        sourceFilterAdapter.setOnItemClickListener((adapter, view, position) -> {
            SearchSourceFilterAdapter.FilterItem item = sourceFilterAdapter.getItem(position);
            if (item == null) {
                return;
            }
            sourceFilterAdapter.setSelectedPosition(position);
            selectedSourceKey = item.sourceKey;
            filterAndDisplayResults();
        });

        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 5));

        tvRemote.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new RemoteDialog(mContext).show();
        });
        tvDelete.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            String text = etSearch.getText().toString().trim();
            if (!text.isEmpty()) {
                etSearch.setText(text.substring(0, text.length() - 1));
            }
            if (etSearch.getText().length() > 0) {
                loadRec(etSearch.getText().toString().trim());
            } else {
                showDefaultHot();
            }
        });
        tvClearHistory.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            SearchHistoryHelper.clear();
            historyAdapter.setNewData(new ArrayList<>());
        });

        keyboard.setOnSearchKeyListener((pos, key) -> {
            String text = etSearch.getText().toString().trim();
            text += key;
            etSearch.setText(text);
            if (text.length() > 0) {
                loadRec(text);
            }
        });

        setLoadSir(llResultPanel);
        showBrowseMode();
    }

    private void handleLegacyKeyboardInput(int pos, String key) {
        if (pos > 1) {
            String text = etSearch.getText().toString().trim();
            text += key;
            etSearch.setText(text);
            if (text.length() > 0) {
                loadRec(text);
            }
        } else if (pos == 1) {
            String text = etSearch.getText().toString().trim();
            if (text.length() > 0) {
                text = text.substring(0, text.length() - 1);
                etSearch.setText(text);
            }
            if (text.length() > 0) {
                loadRec(text);
            }
        } else if (pos == 0) {
            new RemoteDialog(mContext).show();
        }
    }

    private void triggerSearch() {
        String wd = etSearch.getText().toString().trim();
        if (!TextUtils.isEmpty(wd)) {
            search(wd);
        } else {
            Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
    }

    private void showBrowseMode() {
        displayMode = MODE_BROWSE;
        llHistoryPanel.setVisibility(View.VISIBLE);
        llSourcePanel.setVisibility(View.GONE);
        llCenterPanel.setVisibility(View.VISIBLE);
        llHotPanel.setVisibility(View.VISIBLE);
        llResultPanel.setVisibility(View.GONE);
        refreshHistory();
    }

    private void showResultMode() {
        displayMode = MODE_RESULT;
        llHistoryPanel.setVisibility(View.GONE);
        llSourcePanel.setVisibility(View.VISIBLE);
        llCenterPanel.setVisibility(View.GONE);
        llHotPanel.setVisibility(View.GONE);
        llResultPanel.setVisibility(View.VISIBLE);
        mGridView.setVisibility(View.VISIBLE);
    }

    private void refreshHistory() {
        if (historyAdapter != null) {
            historyAdapter.setNewData(SearchHistoryHelper.getHistory());
        }
    }

    /**
     * 拼音联想：本地简拼匹配 + 腾讯接口补充
     */
    private void loadRec(String key) {
        if (TextUtils.isEmpty(key)) {
            showDefaultHot();
            return;
        }
        loadRecTv(key);
    }

    private void loadRecTv(String key) {
        showSuggestions(buildSuggestions(key));
        SearchSuggestHelper.fetch(key, new SearchSuggestHelper.Callback() {
            @Override
            public void onResult(List<String> titles) {
                LinkedHashSet<String> merged = new LinkedHashSet<>(buildSuggestions(key));
                merged.addAll(titles);
                ArrayList<String> list = new ArrayList<>(merged);
                addToCorpus(list);
                showSuggestions(list);
            }

            @Override
            public void onError() {
                showSuggestions(buildSuggestions(key));
            }
        });
    }

    private List<String> buildSuggestions(String key) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (PinyinSearchHelper.isLatinInput(key)) {
            merged.addAll(PinyinSearchHelper.matchLatin(key, searchCorpus));
        } else {
            merged.addAll(PinyinSearchHelper.matchText(key, searchCorpus));
        }
        merged.addAll(filterHotWords(key));
        return new ArrayList<>(merged);
    }

    private List<String> filterHotWords(String key) {
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        if (hotSections == null) {
            return new ArrayList<>();
        }
        for (SearchHotHelper.Section section : hotSections) {
            if (section.words == null) {
                continue;
            }
            for (String word : section.words) {
                if (TextUtils.isEmpty(word)) {
                    continue;
                }
                String core = PinyinSearchHelper.extractCoreTitle(word);
                if (PinyinSearchHelper.isLatinInput(key)) {
                    String jp = PinyinSearchHelper.toJianpin(core);
                    String upper = key.trim().toUpperCase();
                    if (jp.startsWith(upper) || PinyinSearchHelper.jianpinSubsequence(jp, upper)) {
                        matched.add(core);
                    }
                } else if (core.contains(key)) {
                    matched.add(core);
                }
            }
        }
        return new ArrayList<>(matched);
    }

    private void showSuggestions(List<String> words) {
        if (displayMode == MODE_RESULT || hotAdapter == null) {
            return;
        }
        List<SearchHotAdapter.HotItem> items = new ArrayList<>();
        for (String word : words) {
            items.add(new SearchHotAdapter.HotItem(SearchHotAdapter.TYPE_WORD, word));
        }
        hotAdapter.setNewData(items);
    }

    private void showDefaultHot() {
        if (displayMode == MODE_RESULT || hotAdapter == null) {
            return;
        }
        if (hotSections != null && !hotSections.isEmpty()) {
            hotAdapter.setSections(hotSections);
        }
    }

    private void addToCorpus(List<String> words) {
        if (words == null) {
            return;
        }
        for (String word : words) {
            if (!TextUtils.isEmpty(word) && !searchCorpus.contains(word)) {
                searchCorpus.add(word);
            }
        }
    }

    private void initData() {
        loadHotSections();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            if (etSearch != null && !TextUtils.isEmpty(title)) {
                etSearch.setText(title);
            }
            showLoading();
            search(title);
        }
    }

    private void loadHotSections() {
        ArrayList<String> fallback = new ArrayList<>();
        fallback.add("完美世界");
        fallback.add("镖人");
        fallback.add("爱情有烟火");
        fallback.add("庆余年");
        fallback.add("斗罗大陆");
        addToCorpus(fallback);
        showSuggestions(fallback);
        SearchHotHelper.fetchSections(sections -> runOnUiThread(() -> {
            hotSections = sections;
            addHotWordsToCorpus(sections);
            if (displayMode == MODE_BROWSE && TextUtils.isEmpty(etSearch.getText().toString().trim())) {
                showDefaultHot();
            }
        }));
    }

    private void addHotWordsToCorpus(List<SearchHotHelper.Section> sections) {
        if (sections == null) {
            return;
        }
        for (SearchHotHelper.Section section : sections) {
            addToCorpus(section.words);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            search(title);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void search(String title) {
        cancel();
        showLoading();
        SearchHistoryHelper.add(title);
        this.searchTitle = title;

        showResultMode();
        refreshHistory();
        allSearchResults.clear();
        selectedSourceKey = null;
        searchAdapter.setNewData(new ArrayList<>());
        updateSourceFilterList();

        if (PinyinSearchHelper.isLatinInput(title)) {
            List<String> keywords = PinyinSearchHelper.resolveSearchKeywords(title, searchCorpus);
            if (keywords.isEmpty()) {
                resolveLatinFromRemote(title);
                return;
            }
            this.searchKeywords = keywords;
            searchResult();
            return;
        }
        this.searchKeywords = new ArrayList<>();
        this.searchKeywords.add(title);
        searchResult();
    }

    private void resolveLatinFromRemote(final String latin) {
        SearchSuggestHelper.fetch(latin, new SearchSuggestHelper.Callback() {
            @Override
            public void onResult(List<String> titles) {
                addToCorpus(titles);
                searchKeywords = titles;
                searchResult();
            }

            @Override
            public void onError() {
                onLatinResolveFailed(latin);
            }
        });
    }

    private void onLatinResolveFailed(String latin) {
        List<String> local = PinyinSearchHelper.matchLatin(latin, searchCorpus);
        if (!local.isEmpty()) {
            searchKeywords = local;
            searchResult();
            return;
        }
        showEmpty();
        Toast.makeText(mContext, "未识别简拼「" + latin + "」，请从热门或历史中选择片名", Toast.LENGTH_LONG).show();
    }

    private void searchResult() {
        stopSearchExecutor();
        allRunCount.set(0);
        searchExecutorService = Executors.newFixedThreadPool(getSearchThreadCount());
        List<SourceBean> searchRequestList = new ArrayList<>(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (bean.isSearchable()) {
                siteKey.add(bean.getKey());
            }
        }
        if (searchKeywords == null || searchKeywords.isEmpty()) {
            searchKeywords = new ArrayList<>();
            searchKeywords.add(searchTitle);
        }
        allRunCount.set(siteKey.size() * searchKeywords.size());
        if (allRunCount.get() <= 0) {
            showEmpty();
            Toast.makeText(mContext, "没有可搜索的源", Toast.LENGTH_SHORT).show();
            return;
        }
        for (String keyword : searchKeywords) {
            for (String key : siteKey) {
                searchExecutorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        sourceViewModel.getSearch(key, keyword);
                    }
                });
            }
        }
    }

    private void searchData(AbsXml absXml) {
        searchDataTv(absXml);
    }

    private void searchDataTv(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null
                && !absXml.movie.videoList.isEmpty()) {
            Set<String> exists = new HashSet<>();
            for (Movie.Video old : allSearchResults) {
                exists.add(old.sourceKey + "|" + old.id + "|" + old.name);
            }
            for (Movie.Video video : absXml.movie.videoList) {
                if (video != null && video.name != null && !video.name.isEmpty()
                        && video.id != null && !video.id.isEmpty()) {
                    String dedupeKey = video.sourceKey + "|" + video.id + "|" + video.name;
                    if (!exists.contains(dedupeKey)) {
                        exists.add(dedupeKey);
                        allSearchResults.add(video);
                    }
                }
            }
            showSuccess();
            updateSourceFilterList();
            filterAndDisplayResults();
        }

        int count = allRunCount.decrementAndGet();
        if (count <= 0) {
            if (allSearchResults.isEmpty()) {
                showEmpty();
                if (searchTitle != null && PinyinSearchHelper.isLatinInput(searchTitle)) {
                    Toast.makeText(mContext, "简拼「" + searchTitle + "」未搜到结果", Toast.LENGTH_LONG).show();
                }
            } else {
                showSuccess();
            }
            stopSearchExecutor();
        }
    }

    private void updateSourceFilterList() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Movie.Video video : allSearchResults) {
            counts.put(video.sourceKey, counts.getOrDefault(video.sourceKey, 0) + 1);
        }
        List<SearchSourceFilterAdapter.FilterItem> items = new ArrayList<>();
        items.add(new SearchSourceFilterAdapter.FilterItem(null, "全部显示", allSearchResults.size()));
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            SourceBean source = ApiConfig.get().getSource(entry.getKey());
            String name = source != null ? source.getName() : entry.getKey();
            items.add(new SearchSourceFilterAdapter.FilterItem(entry.getKey(), name, entry.getValue()));
        }
        sourceFilterAdapter.setNewData(items);
        sourceFilterAdapter.setSelectedPosition(0);
        selectedSourceKey = null;
        updateSearchCount(allSearchResults.size(), allSearchResults.size());
    }

    private void filterAndDisplayResults() {
        List<Movie.Video> filtered = new ArrayList<>();
        for (Movie.Video video : allSearchResults) {
            if (selectedSourceKey == null || selectedSourceKey.equals(video.sourceKey)) {
                filtered.add(video);
            }
        }
        searchAdapter.setNewData(filtered);
        updateSearchCount(filtered.size(), allSearchResults.size());
    }

    private void updateSearchCount(int shown, int total) {
        if (tvSearchCount != null) {
            tvSearchCount.setText("搜索(" + shown + "/" + total + ")");
        }
    }

    private void stopSearchExecutor() {
        try {
            if (searchExecutorService != null) {
                pauseRunnable = searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void cancel() {
        OkGo.getInstance().cancelTag("search");
        OkGo.getInstance().cancelTag("search_suggest");
    }

    private int getSearchThreadCount() {
        int count = Hawk.get(HawkConfig.SEARCH_THREAD, 16);
        return count < 1 ? 1 : count;
    }

    @Override
    public void onBackPressed() {
        if (displayMode == MODE_RESULT) {
            showBrowseMode();
            showSuccess();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && displayMode == MODE_RESULT) {
            onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel();
        stopSearchExecutor();
        EventBus.getDefault().unregister(this);
    }
}
