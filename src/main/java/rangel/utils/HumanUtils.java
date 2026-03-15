package rangel.utils;

import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.Human;

/**
 * 人类工具类
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class HumanUtils {

    /**
     * 伤害类型:坍塌
     */
    private static final String KEY_TYPE_COLLAPSE = "collapse";

    /**
     * 伤害类型:掩埋
     */
    private static final String KEY_TYPE_BURY = "bury";

    /**
     * 伤害类型: 火灾
     */
    private static final String KEY_TYPE_FIRE = "fire";

    /**
     * 人类因处于倒塌的建筑中而受到的伤害
     */
    private static final DamageType DAMAGE_COLLAPSE = new DamageType(KEY_TYPE_COLLAPSE);

    /**
     * 人类因被埋在地下而受到的伤害
     */
    private static final DamageType DAMAGE_BURY = new DamageType(KEY_TYPE_BURY);

    /**
     * 人类因处于燃烧的建筑中而受到的伤害(已经不再使用)
     */
    private static final DamageType DAMAGE_FIRE = new DamageType(KEY_TYPE_FIRE);


    /**
     * 获得指定人类估计的生存时间
     *
     * @param human 人类
     * @return 估计的生存时间
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public static int getEstimatedSurvivalTime(@NotNull Human human) {
        double hp = human.getHP();
        double damage = human.getDamage();
        int time = 0;
        while (hp > 0) {
            double collapseDamage = DAMAGE_BURY.progress(damage);
            double fireDamage = DAMAGE_COLLAPSE.progress(damage);
            damage += collapseDamage + fireDamage;
            hp -= damage;
            time++;
        }
        return time;
    }


    /**
     * 伤害类型
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see misc.DamageType
     */
    private static class DamageType {

        /**
         * 伤害系数k
         */
        private final double k;

        /**
         * 伤害系数l
         */
        private final double l;

        /**
         * 伤害噪声, 用于模拟伤害的不确定性
         */
        private final double n;


        /**
         * {@link DamageType}的构造函数
         *
         * @param type 伤害类型
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public DamageType(String type) {
            this.k = ConfigUtils.getDouble("misc.injury." + type + ".k", 0);
            this.l = ConfigUtils.getDouble("misc.injury." + type + ".l", 0);
            //原本是使用GaussianGenerator生成一个随机数,考虑到性能因素,这里直接使用均值代替
            this.n = ConfigUtils.getDouble("misc.injury." + type + ".noise.mean", 0);
        }


        /**
         * 计算伤害的增量
         *
         * @param damage 当前伤害
         * @return 伤害增量
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public double progress(double damage) {
            return k * damage * damage + l + n;
            //***from config.yaml***
            //k=0.00035,l=0.01,n=0.1
        }

    }

}
