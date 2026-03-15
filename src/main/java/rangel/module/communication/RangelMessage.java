package rangel.module.communication;

/**
 * 自定义的消息
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public interface RangelMessage {

    /**
     * 请求救援
     */
    int HELP_RESCUE=5;

    /**
     * 请求清理
     */
    int HELP_CLEAR=6;

    /**
     * 最佳避难所
     */
    int BEST_REFUGE = 7;

}
