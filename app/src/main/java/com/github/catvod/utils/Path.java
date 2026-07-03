package com.github.catvod.utils;

import com.github.catvod.Init;

import java.io.File;

public class Path {

    public static File cache() {
        return Init.context().getCacheDir();
    }

    public static File py() {
        File dir = new File(cache(), "py");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File py(String name) {
        return new File(py(), name);
    }
}
