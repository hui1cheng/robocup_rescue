package rangel.module.algorithm.cluster;

import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

/**
 * 划分区域的聚类
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @see AbstractCluster
 */
public class AreaCluster extends AbstractCluster<Area> {

    /**
     * {@link AreaCluster}的无参构造方法
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see AbstractCluster#AbstractCluster()  AbstractCluster
     */
    public AreaCluster() {
        super();
    }


    /**
     * {@link AreaCluster}的指定初始成员的构造方法
     *
     * @param members 聚类成员
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see AbstractCluster#AbstractCluster(Collection)  AbstractCluster
     */
    public AreaCluster(Collection<Area> members) {
        super(members);
    }


    /**
     * {@inheritDoc}
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see AbstractCluster#updateCentroid()
     */
    @Override
    public void updateCentroid() {
        if (!this.members.isEmpty()) {
            int sumX = 0;
            int sumY = 0;
            for (Area member : this.members) {
                sumX += member.getX();
                sumY += member.getY();
            }
            this.centroid.setLocation(sumX / this.members.size(), sumY / this.members.size());
        }
    }


    /**
     * 获得聚类的所有成员的EntityID的集合
     *
     * @return 聚类的所有成员的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public Collection<EntityID> getMemberIDs() {
        return this.members
                .stream()
                .map(Area::getID)
                .toList();
    }

}
