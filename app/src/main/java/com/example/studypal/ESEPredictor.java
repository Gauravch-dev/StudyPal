package com.example.studypal;

import android.content.Context;
import android.content.res.AssetManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ESEPredictor {

    public static class PostParams {
        public Double k;
        public Double mid;
        @SerializedName("max_out")
        public Double maxOut;
        public Double max;
    }
    public static class Postprocess {
        public String type;
        public PostParams params;
    }
    public static class Weights {
        public double intercept;
        public Map<String, Double> coef = new HashMap<>();
        @SerializedName("features_order")
        public String[] featuresOrder;
        public String target;
        public Postprocess postprocess;
    }

    private final Weights W;

    private ESEPredictor(Weights w) { this.W = w; }

    public static ESEPredictor load(Context ctx) throws Exception {
        String json = readAsset(ctx.getAssets(), "weights.json");
        Weights w = new Gson().fromJson(json, Weights.class);
        if (w == null || w.coef == null) throw new IllegalStateException("Invalid weights.json");
        return new ESEPredictor(w);
    }

    private static String readAsset(AssetManager am, String name) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = am.open(name);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    private double applyPostprocess(double yRaw) {
        double y = Math.max(0.0, Math.min(100.0, yRaw));
        if (W.postprocess == null || W.postprocess.type == null) return y;
        String type = W.postprocess.type;
        PostParams p = W.postprocess.params;
        if ("soft_ceiling".equals(type)) {
            double k   = (p != null && p.k != null) ? p.k : 0.08;
            double mid = (p != null && p.mid != null) ? p.mid : 50.0;
            double max = (p != null && p.maxOut != null) ? p.maxOut : 99.5;
            double s = sigmoid(k * (y - mid));
            return Math.max(0.0, Math.min(max, max * s));
        } else if ("hard_cap".equals(type)) {
            double cap = (p != null && p.max != null) ? p.max : 98.0;
            return Math.max(0.0, Math.min(cap, y));
        }
        return y;
    }

    /** Predict ESE percent (0..100). Inputs: mse% 0..100, cia 0..20, progress% 0..100 */
    public double predict(double msePercent, double ciaAvg, double progressPercent) {
        double mse  = Math.max(0.0, Math.min(100.0, msePercent));
        double cia  = Math.max(0.0, Math.min(20.0,  ciaAvg));
        double prog = Math.max(0.0, Math.min(100.0, progressPercent));

        double y = W.intercept;
        if (W.coef.containsKey("mse_percent"))      y += W.coef.get("mse_percent") * mse;
        if (W.coef.containsKey("cia_avg"))          y += W.coef.get("cia_avg") * cia;
        if (W.coef.containsKey("progress_percent")) y += W.coef.get("progress_percent") * prog;
        if (W.coef.containsKey("mse_percent cia_avg"))          y += W.coef.get("mse_percent cia_avg") * (mse * cia);
        if (W.coef.containsKey("mse_percent progress_percent")) y += W.coef.get("mse_percent progress_percent") * (mse * prog);
        if (W.coef.containsKey("cia_avg progress_percent"))     y += W.coef.get("cia_avg progress_percent") * (cia * prog);

        double yPost = applyPostprocess(y);
        return Math.round(yPost * 10.0) / 10.0;
    }
}