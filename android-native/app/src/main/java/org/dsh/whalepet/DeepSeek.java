package org.dsh.whalepet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeepSeek {
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String SYSTEM =
            "你是 DeepSeek 的鲸鱼娘：蓝色双马尾、白色女仆装、围裙上有小鲸鱼。"
          + "说话可爱、简短、有元气，自称「本鲸」，回答不超过 80 字，不要说教。";

    public static String chat(String key, JSONArray history) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", "deepseek-chat");
        JSONArray msgs = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", SYSTEM);
        msgs.put(sys);
        for (int i = 0; i < history.length(); i++) msgs.put(history.get(i));
        body.put("messages", msgs);
        body.put("stream", false);
        body.put("max_tokens", 300);

        HttpURLConnection c = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String text = drain(in);
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " " + text);
        JSONObject resp = new JSONObject(text);
        return resp.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim();
    }

    private static String drain(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }
}
