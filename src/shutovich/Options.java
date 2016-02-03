package shutovich;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Created by U on 12/22/2015.
 */
public class Options {
    int trapFactor = 23;
    double minGoodPenalty = 13.2;
    int cornerMovesPenalty = 13700;
    int largeNumberCountPenalty = 13;
    double emptyCellPenalty = 1.41;
    double endGamePenalty = 1.4e7;
    double combinationPower = 1.0;

    double fallbackThresholdFactor = 1.0;
    double predictCombinationPower = 1.0;
    double closeFallbackWeight = 2.0;
    double endGamePredict = 3.0;

/* p = 0.2:

    int trapFactor = 80;
    double minGoodPenalty = 0.06;
    int cornerMovesPenalty = 26;
    int largeNumberCountPenalty = 23;
    double emptyCellPenalty = 0.72;
    double endGamePenalty = 1e6;
 */

/*     int trapFactor = 1000;
    double minGoodPenalty = 2.0;
    int cornerMovesPenalty = 1;
    int largeNumberCountPenalty = 10;
    double emptyCellPenalty = 0.01;
*/

    List<String> getHeader() {
        List<String> result = new ArrayList<>();
        JsonElement element = new Gson().toJsonTree(this);
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            result.add(entry.getKey());
        }
        return result;
    }

    List<String> getValues() {
        List<String> result = new ArrayList<>();
        JsonElement element = new Gson().toJsonTree(this);
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            result.add(entry.getValue().toString());
        }
        return result;
    }

    Options scaleOptions(double[] factors) {
        JsonElement element = new Gson().toJsonTree(this);
        int i = 0;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
            entry.setValue(new JsonPrimitive(value.getAsDouble() * factors[i++]));
        }
        return new Gson().fromJson(element, Options.class);
    }

    @Override
    public String toString() {
        List<String> header = getHeader();
        List<String> values = getValues();
        assert(header.size() == values.size());
        return IntStream.range(0, header.size()).mapToObj(i -> header.get(i) + ": " + values.get(i))
                .collect(Collectors.joining(", "));
    }


}
