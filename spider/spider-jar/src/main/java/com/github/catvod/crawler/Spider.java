package com.github.catvod.crawler;

import android.content.Context;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Spider {
    public void init(Context context, String extend) throws Exception {}
    public String homeContent(boolean filter) throws Exception { return ""; }
    public String homeVideoContent() throws Exception { return ""; }
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception { return ""; }
    public String detailContent(List<String> ids) throws Exception { return ""; }
    public String searchContent(String key, boolean quick) throws Exception { return ""; }
    public String searchContent(String key, boolean quick, String pg) throws Exception { return ""; }
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception { return ""; }
    public boolean manualVideoCheck() throws Exception { return false; }
    public boolean isVideoFormat(String url) throws Exception { return false; }
    public Object[] proxyLocal(HashMap<String, String> params) throws Exception { return new Object[]{}; }
    public Object[] proxyLocal(Map<String, String> params) throws Exception { return new Object[]{}; }
    public Object[] proxy(HashMap<String, String> params) throws Exception { return proxyLocal(params); }
    public Object[] proxy(Map<String, String> params) throws Exception { return proxyLocal(params); }
    public void cancelByTag(String tag) {}
    public void destroy() {}
}
