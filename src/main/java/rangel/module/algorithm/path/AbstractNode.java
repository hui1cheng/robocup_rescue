package rangel.module.algorithm.path;

import org.jetbrains.annotations.NotNull;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

/**
 * 抽象的节点类
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @see AStarPathPlanning
 * @see RangelPathPlanning
 */
public abstract class AbstractNode {

    /**
     * 当前节点的EntityID
     */
    protected final EntityID currentID;

    /**
     * 父节点的EntityID
     */
    protected final EntityID parentID;

    /**
     * G评分-根据当前点与起始点的距离
     */
    protected double g;

    /**
     * H评分-根据当前点与目标点的距离
     */
    protected double h;


    /**
     * {@link AbstractNode}的构造函数
     *
     * @param from 父节点的{@link AbstractNode}
     * @param currentID   当前节点的{@link EntityID}
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AbstractNode(AbstractNode from, @NotNull EntityID currentID) {
        this.currentID = currentID;
        if (from == null) {
            this.parentID = null;
        } else {
            this.parentID = from.getID();
        }
        this.calcGH(from, currentID);
    }


    /**
     * 计算当前节点的G评分({@link #g})和H评分({@link #h})
     *
     * @param from 父节点的{@link AbstractNode}
     * @param currentID   当前节点的{@link EntityID}
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    protected abstract void calcGH(AbstractNode from, EntityID currentID);


    /**
     * 获得当前节点的邻居节点的集合
     *
     * @return {@link Collection}<{@link AbstractNode}> 邻居节点的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public abstract Collection<AbstractNode> getNeighbors();


    /**
     * 获得本节点的EntityID
     *
     * @return {@link EntityID} 本节点的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public EntityID getID() {
        return currentID;
    }


    /**
     * 获得父节点的EntityID
     *
     * @return {@link EntityID} 父节点的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public EntityID getParent() {
        return this.parentID;
    }


    /**
     * 获取当前点与起始点间的距离({@link #g})
     *
     * @return {@link Double} 当前点与起始点间的距离
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public double getG() {
        return g;
    }


    /**
     * 获取总评F,F=G+H,F越小,代表该节点的优先级越大
     *
     * @return {@link Double} 总评F
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public double getF() {
        return g + h;
    }

}
