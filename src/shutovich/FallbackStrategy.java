package shutovich;

/**
 * Created by U on 1/26/2016.
 */
public interface FallbackStrategy {

    boolean needsFallback(GameField field);
    Action chooseOptimalAction(GameField field);
}
