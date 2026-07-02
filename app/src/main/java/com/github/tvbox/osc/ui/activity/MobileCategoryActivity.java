package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MobileUiHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MobileCategoryActivity extends BaseActivity {
    public static final String EXTRA_SORT = "sort";

    private MovieSort.SortData sortData;
    private AbsSortXml absSortXml;
    private RecyclerView rvCategoryGrid;
    private ProgressBar progressLoading;
    private GridAdapter gridAdapter;
    private SourceViewModel sourceViewModel;
    private int page = 1;
    private int maxPage = 1;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_mobile_category;
    }

    @Override
    protected void init() {
        sortData = (MovieSort.SortData) getIntent().getSerializableExtra(EXTRA_SORT);
        if (sortData == null) {
            finish();
            return;
        }
        TextView title = findViewById(R.id.tvCategoryTitle);
        title.setText(sortData.name);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        progressLoading = findViewById(R.id.progressLoading);
        rvCategoryGrid = findViewById(R.id.rvCategoryGrid);
        gridAdapter = new GridAdapter();
        rvCategoryGrid.setLayoutManager(new GridLayoutManager(this, MobileUiHelper.getHomeGridColumns(this)));
        rvCategoryGrid.setAdapter(gridAdapter);
        gridAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            Movie.Video video = gridAdapter.getItem(position);
            if (video == null) {
                return;
            }
            if (!TextUtils.isEmpty(video.id)) {
                Bundle bundle = new Bundle();
                bundle.putString("id", video.id);
                bundle.putString("sourceKey", video.sourceKey);
                jumpActivity(DetailActivity.class, bundle);
            } else if (!TextUtils.isEmpty(video.name)) {
                MobileUiHelper.openSearch(this, video.name);
            }
        });
        rvCategoryGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || page > maxPage) {
                    return;
                }
                GridLayoutManager lm = (GridLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= gridAdapter.getItemCount() - 4) {
                    sourceViewModel.getList(sortData, page);
                }
            }
        });

        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                progressLoading.setVisibility(View.GONE);
                if ("my0".equals(sortData.id)) {
                    return;
                }
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && !absXml.movie.videoList.isEmpty()) {
                    if (page == 1) {
                        gridAdapter.setNewData(absXml.movie.videoList);
                    } else {
                        gridAdapter.addData(absXml.movie.videoList);
                    }
                    page++;
                    maxPage = absXml.movie.pagecount;
                } else if (page == 1) {
                    gridAdapter.setNewData(new ArrayList<>());
                }
            }
        });
        sourceViewModel.sortResult.observe(this, absXml -> {
            absSortXml = absXml;
            if ("my0".equals(sortData.id)) {
                loadMyRecommend();
            }
        });

        progressLoading.setVisibility(View.VISIBLE);
        if ("my0".equals(sortData.id)) {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home != null && home.getKey() != null) {
                sourceViewModel.getSort(home.getKey());
            } else {
                loadMyRecommend();
            }
        } else {
            page = 1;
            maxPage = 1;
            sourceViewModel.getList(sortData, page);
        }
    }

    private void loadMyRecommend() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
        if (homeRec == 1 && absSortXml != null && absSortXml.videoList != null && !absSortXml.videoList.isEmpty()) {
            progressLoading.setVisibility(View.GONE);
            gridAdapter.setNewData(absSortXml.videoList);
            return;
        }
        if (homeRec == 2) {
            progressLoading.setVisibility(View.GONE);
            gridAdapter.setNewData(new ArrayList<>());
            return;
        }
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DATE);
            String today = String.format("%d%d%d", year, month, day);
            String requestDay = Hawk.get("home_hot_day", "");
            if (today.equals(requestDay)) {
                String json = Hawk.get("home_hot", "");
                if (!json.isEmpty()) {
                    progressLoading.setVisibility(View.GONE);
                    gridAdapter.setNewData(loadHots(json));
                    return;
                }
            }
            OkGo.<String>get("https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range="
                    + year + "," + year).execute(new AbsCallback<String>() {
                @Override
                public void onSuccess(Response<String> response) {
                    String netJson = response.body();
                    Hawk.put("home_hot_day", today);
                    Hawk.put("home_hot", netJson);
                    runOnUiThread(() -> {
                        progressLoading.setVisibility(View.GONE);
                        gridAdapter.setNewData(loadHots(netJson));
                    });
                }

                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    return response.body().string();
                }
            });
        } catch (Throwable th) {
            th.printStackTrace();
            progressLoading.setVisibility(View.GONE);
        }
    }

    private ArrayList<Movie.Video> loadHots(String json) {
        ArrayList<Movie.Video> result = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                vod.pic = obj.get("cover").getAsString();
                result.add(vod);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }
}
