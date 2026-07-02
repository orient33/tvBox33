package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;

public abstract class Spider {
    public void init(Context context, String extend) {}
    public abstract String homeContent(boolean filter);
    public String homeVideoContent() { return ""; }
    public abstract String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend);
    public abstract String detailContent(List<String> ids);
    public abstract String searchContent(String key, boolean quick, String pg);
    public abstract String playerContent(String flag, String id, List<String> vipFlags);
    public String proxyLocal(HashMap<String, String> params) { return ""; }
    public String proxy(HashMap<String, String> params) { return ""; }
    public void cancelByTag(String tag) {}
    public void destroy() {}
}
