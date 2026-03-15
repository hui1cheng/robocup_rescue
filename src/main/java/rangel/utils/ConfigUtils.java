package rangel.utils;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 配置文件工具类
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class ConfigUtils {

    /**
     * 从配置文件读取的配置映射
     */
    private static final Map<String, Object> configMap;

    static {
        try {
            InputStream inputStream = ConfigUtils.class.getResourceAsStream("/config.yaml");
            configMap = new Yaml().load(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("加载配置文件失败");
        }
    }


    /**
     * 从配置文件中读取对应类型的值,如果没有,则返回默认值
     *
     * @param key          配置项的key
     * @param defaultValue 默认值
     * @param <T>          配置项的类型
     * @return 配置项的值
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @SuppressWarnings("unchecked")
    private static <T> T getValue(@NotNull String key, T defaultValue) {
        String[] separatorKeys = key.split("\\.");
        Map<String, Object> tempMap = configMap;
        for (int i = 0; i < separatorKeys.length; i++) {
            String separatorKey = separatorKeys[i];
            Object object = tempMap.get(separatorKey);
            if (object == null) {
                return defaultValue;
            }
            if (i == separatorKeys.length - 1) {
                return (T) object;
            }
            tempMap = (Map<String, Object>) object;
        }
        return defaultValue;
    }


    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static Integer getInteger(String key, int defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static Double getDouble(String key, double defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static Boolean getBoolean(String key, boolean defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static String getString(String key, String defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static List<Integer> getIntegerList(String key, List<Integer> defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static List<Double> getDoubleList(String key, List<Double> defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static List<Boolean> getBooleanList(String key, List<Boolean> defaultValue) {
        return getValue(key, defaultValue);
    }

    /**
     * @see ConfigUtils#getValue(String, Object)
     */
    public static List<String> getStringList(String key, List<String> defaultValue) {
        return getValue(key, defaultValue);
    }

}
