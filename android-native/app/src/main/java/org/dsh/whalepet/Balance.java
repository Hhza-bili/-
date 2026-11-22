package org.dsh.whalepet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Balance {
    public static String fetch(String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection)
                new URL("https://api.deepseek.com/user/balance").openConnection();
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        String text = new String(out.toByteArray(), "UTF-8");
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        JSONObject o = new JSONObject(text);
        JSONArray infos = o.optJSONArray("balance_infos");
        if (infos == null || infos.length() == 0) return "余额 —";
        JSONObject info = infos.getJSONObject(0);
        for (int i = 0; i < infos.length(); i++) {
            if ("CNY".equals(infos.getJSONObject(i).optString("currency"))) {
                info = infos.getJSONObject(i);
                break;
            }
        }
        return "\u00a5" + info.optString("total_balance", "0");
    }
}
