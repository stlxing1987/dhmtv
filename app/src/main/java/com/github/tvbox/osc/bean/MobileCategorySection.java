package com.github.tvbox.osc.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MobileCategorySection implements Serializable {
    public MovieSort.SortData sort;
    public List<Movie.Video> videos = new ArrayList<>();
    public boolean loading;

    public MobileCategorySection(MovieSort.SortData sort) {
        this.sort = sort;
        this.loading = true;
    }
}
