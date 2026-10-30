package local.mediacentre.probe;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class StationCatalog {
    static JSONArray load(Context context) throws Exception {
        try (InputStream input = context.getAssets().open("h0-stations.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new JSONArray(output.toString("UTF-8"));
        }
    }

    static JSONObject find(Context context, String id) throws Exception {
        JSONArray items = load(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (item.getString("id").equals(id)) return item;
        }
        throw new IllegalArgumentException("Unknown saved station: " + id);
    }
}
