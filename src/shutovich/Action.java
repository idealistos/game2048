package shutovich;

/**
 * Created by U on 1/26/2016.
 */
class Action {
    GameField field;
    double penalty;
    Direction direction;

    Action(GameField field, double penalty, Direction direction) {
        this.field = field;
        this.penalty = penalty;
        this.direction = direction;
    }
}
