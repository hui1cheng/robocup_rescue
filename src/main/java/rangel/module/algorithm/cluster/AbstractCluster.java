package rangel.module.algorithm.cluster;

import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 抽象聚类
 *
 * @param <T> 聚类成员的类型
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public abstract class AbstractCluster<T> {

    /**
     * 聚类中心
     */
    protected final Point centroid;

    /**
     * 聚类成员
     */
    protected final Set<T> members;


    /**
     * {@link AbstractCluster}的无参构造方法
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AbstractCluster() {
        this.centroid = new Point(0, 0);
        this.members = new HashSet<>();
    }


    /**
     * {@link AbstractCluster}的指定初始成员的构造方法,会自动更新聚类中心
     *
     * @param members 聚类成员
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AbstractCluster(Collection<T> members) {
        this.centroid = new Point(0, 0);
        this.members = new HashSet<>(members);
        this.updateCentroid();
    }


    /**
     * 获得聚类中心
     *
     * @return 聚类中心
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public Point getCentroid() {
        return this.centroid;
    }


    /**
     * 获得聚类成员
     *
     * @return 聚类成员
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public Collection<T> getMembers() {
        return this.members;
    }


    /**
     * 添加聚类成员
     *
     * @param member 聚类成员
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public void add(T member) {
        this.members.add(member);
    }


    /**
     * 清空聚类成员
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public void clear() {
        this.members.clear();
    }


    /**
     * 更新聚类中心
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public abstract void updateCentroid();

}
