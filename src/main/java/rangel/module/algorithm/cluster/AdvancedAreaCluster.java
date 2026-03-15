package rangel.module.algorithm.cluster;

import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.Area;

import java.util.Collection;

/**
 * 高阶聚类
 * <p>
 * 可由多个原始聚类扩展而来
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class AdvancedAreaCluster extends AreaCluster {

    /**
     * 此高阶聚类的阶级,代表了此聚类由多少个原始聚类扩展而来
     */
    private int rank;


    /**
     * {@link AdvancedAreaCluster}的构造函数
     *
     * @param members 此聚类的成员
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AdvancedAreaCluster(Collection<Area> members) {
        super(members);
        this.rank = 1;
    }


    /**
     * 构造一个已有的{@link AdvancedAreaCluster}的拷贝
     *
     * @param advancedAreaCluster 已有的{@link AdvancedAreaCluster}
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AdvancedAreaCluster(@NotNull AdvancedAreaCluster advancedAreaCluster) {
        super(advancedAreaCluster.getMembers());
        this.rank = advancedAreaCluster.getRank();
    }


    /**
     * 获取此聚类的阶级
     *
     * @return 此聚类的阶级, 代表了此聚类由多少个原始聚类扩展而来
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public int getRank() {
        return this.rank;
    }


    /**
     * 扩展聚类
     * <p>
     * <ul>
     *     <li>此聚类的成员添加上其它所有聚类的成员({@link #members})
     *     <li>此聚类的阶级加上其它所有聚类的阶级({@link #rank})
     * </ul>
     *
     * @param advancedAreaClusters 想要扩展到此聚类的其它聚类的集合
     * @return 扩展后的聚类
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AdvancedAreaCluster expand(@NotNull Collection<AdvancedAreaCluster> advancedAreaClusters) {
        for (AdvancedAreaCluster advancedAreaCluster : advancedAreaClusters) {
            this.rank += advancedAreaCluster.getRank();
            advancedAreaCluster.getMembers().forEach(this::add);
        }
        this.updateCentroid();
        return this;
    }

}

