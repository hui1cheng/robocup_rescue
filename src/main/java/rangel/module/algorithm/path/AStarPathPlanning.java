package rangel.module.algorithm.path;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.PathPlanning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * 基于A*的路径规划算法
 * <p>
 * 调用流程:{@link #setFrom(EntityID)} -> {@link #setDestination(Collection)} -> {@link #calc()} -> {@link #getResult()}
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class AStarPathPlanning extends PathPlanning {

    /**
     * 起点的EntityID
     */
    private EntityID from;

    /**
     * 目的地的EntityID
     */
    private EntityID destination;

    /**
     * 计算出的路径的EntityID的列表
     */
    private List<EntityID> result;


    /**
     * {@link AStarPathPlanning}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param ScenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public AStarPathPlanning(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo ScenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, ScenarioInfo, moduleManager, developData);

        this.from = null;
        this.destination = null;
        this.result = null;
    }


    /**
     * 预计算时执行的方法
     * <p>
     * 仅重写了这个方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }


    /**
     * 预计算模式的初始化处理方法
     * <p>
     * 仅重写了这个方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     * <p>
     * 仅重写了这个方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning preparate() {
        super.preparate();
        return this;
    }


    /**
     * 每个回合都会执行这个方法来更新agent所持有的信息
     * <p>
     * 仅重写了这个方法
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }


    /**
     * 设置起点
     *
     * @param entityID 起点的EntityID
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning setFrom(EntityID entityID) {
        this.from = entityID;
        return this;
    }


    /**
     * 设置目的地
     *
     * @param targets 目标的EntityID的集合
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning setDestination(Collection<EntityID> targets) {
        if (targets == null) {
            this.destination = null;
            return this;
        }

        //从目标中找到一个距离当前位置最近的目的地
        EntityID me = this.agentInfo.getID();
        this.destination = targets
                .stream()
                .min(Comparator.comparing(entityID -> this.worldInfo.getDistance(entityID, me)))
                .orElse(null);
        return this;
    }


    /**
     * 计算路径
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public PathPlanning calc() {
        AbstractNode startNode = new Node(null, this.from);
        Map<EntityID, AbstractNode> nodeMap = new HashMap<>();
        Queue<AbstractNode> nodeQueue = new PriorityQueue<>(Comparator.comparingDouble(AbstractNode::getF));
        nodeQueue.add(startNode);

        while (!nodeQueue.isEmpty() && !nodeMap.containsKey(this.destination)) {
            AbstractNode node = nodeQueue.poll();
            EntityID entityID = node.getID();
            if (nodeMap.containsKey(entityID)) {
                continue;
            }
            nodeMap.put(entityID, node);
            nodeQueue.addAll(node.getNeighbors());
        }

        LinkedList<AbstractNode> nodePath = new LinkedList<>();
        AbstractNode nextNode = nodeMap.get(this.destination);
        while (nextNode != null) {
            nodePath.addFirst(nextNode);
            nextNode = nodeMap.get(nextNode.getParent());
        }

        this.result = nodePath
                .stream()
                .map(AbstractNode::getID)
                .toList();
        return this;
    }


    /**
     * 获取路径
     *
     * @return 路径的EntityID的列表
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public List<EntityID> getResult() {
        return this.result;
    }


    /**
     * 节点类,继承自{@link AbstractNode}
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see AbstractNode
     */
    private class Node extends AbstractNode {

        /**
         * @see AbstractNode#AbstractNode(AbstractNode, EntityID)  AbstractNode
         */
        public Node(Node from, @NotNull EntityID currentID) {
            super(from, currentID);
        }


        /**
         * @see AbstractNode#getG()
         */
        @Override
        public void calcGH(AbstractNode from, EntityID currentID) {
            //如果父节点为null,说明该点是起始点,G为0
            if (from == null) {
                this.g = 0;
            } else {
                this.g = from.getG() + AStarPathPlanning.this.worldInfo.getDistance(from.getID(), currentID);
            }
            this.h = AStarPathPlanning.this.worldInfo.getDistance(currentID, destination);
        }


        /**
         * @see AbstractNode#getNeighbors()
         */
        @Override
        @Nullable
        public Collection<AbstractNode> getNeighbors() {
            StandardEntity entity = AStarPathPlanning.this.worldInfo.getEntity(this.getID());
            if (entity instanceof Area area) {
                return area.getNeighbours()
                        .stream()
                        .map(entityID -> new Node(this, entityID))
                        .map(AbstractNode.class::cast)
                        .toList();
            }
            return null;
        }

    }

}