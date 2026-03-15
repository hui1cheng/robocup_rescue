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
import rangel.module.algorithm.data.DataModule;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * 警察的路径规划算法
 * <p>
 * 调用流程:{@link #setFrom(EntityID)} -> {@link #setDestination(Collection)} -> {@link #calc()} -> {@link #getResult()}
 *
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelPathPlanningPolice extends PathPlanning {

    /**
     * 计算主干道的算法模块
     */
    private final DataModule trunkRoad;

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
     * {@link RangelPathPlanningPolice}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="https://roozen.top">Roozen</a>
     */
    public RangelPathPlanningPolice(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.from = null;
        this.destination = null;
        this.result = null;

        this.trunkRoad = moduleManager.getModule("RangelPathPlanning.TrunkRoad");
        this.registerModule(this.trunkRoad);
    }


    /**
     * 预计算时执行的方法
     * <p>
     * 仅重写了这个方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="https://roozen.top">Roozen</a>
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
     * @author <a href="https://roozen.top">Roozen</a>
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
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public PathPlanning preparate() {
        super.preparate();
        return this;
    }


    /**
     * 每个回合都会执行这个方法来更新agent所持有的信息
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public PathPlanning updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        return this;
    }


    /**
     * 设置起点
     *
     * @param entityID 起点的EntityID
     * @return this
     * @author <a href="https://roozen.top">Roozen</a>
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
     * @author <a href="https://roozen.top">Roozen</a>
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
     * @author <a href="https://roozen.top">Roozen</a>
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
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public List<EntityID> getResult() {
        return this.result;
    }


    /**
     * 获得某个区域所有的邻居两两构成的边({@link Pair}<{@link EntityID},{@link EntityID}>)
     *
     * @param area 区域
     * @return 边的两个端点的EntityID对的集合
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @NotNull
    private Set<Pair<EntityID, EntityID>> getEdgePairs(@NotNull Area area) {
        List<EntityID> neighbors = area.getNeighbours();
        int n = neighbors.size();

        Set<Pair<EntityID, EntityID>> edgePairs = new HashSet<>();
        for (int i = 0; i < n - 1; ++i) {
            for (int j = i + 1; j < n; ++j) {
                edgePairs.add(new Pair<>(neighbors.get(i), neighbors.get(j)));
            }
        }

        EntityID areaID = area.getID();
        EntityID positionID = this.agentInfo.getPosition();
        if (areaID.equals(positionID)) {
            for (EntityID neighbor : neighbors) {
                edgePairs.add(new Pair<>(neighbor, null));
            }
        }
        return edgePairs;
    }


    /**
     * 判断是否是主干道
     *
     * @param entityID 节点的EntityID
     * @return true: 是主干道 || false: 不是主干道
     * @author <a href="https://roozen.top">Roozen</a>
     */

    private boolean isTrunkRoad(EntityID entityID) {
        int time = this.agentInfo.getTime();
        if (time < 30) {
            return false;
        }
        return this.trunkRoad.calc().getBoolean(entityID);
    }


    /**
     * 节点类,继承自{@link AbstractNode}
     *
     * @author <a href="https://roozen.top">Roozen</a>
     * @see AbstractNode
     */
    private class Node extends AbstractNode {

        /**
         * @see AbstractNode#AbstractNode(AbstractNode, EntityID)  AbstractNode
         */
        public Node(Node from, EntityID currentID) {
            super(from, currentID);
        }


        /**
         * @see AbstractNode#getG()
         */
        @Override
        protected void calcGH(AbstractNode from, EntityID currentID) {
            //如果父节点为null,说明该点是起始点,G为0
            if (from == null) {
                this.g = 0;
            } else {
                int distance = RangelPathPlanningPolice.this.worldInfo.getDistance(from.getID(), currentID);
                // 起点的坐标
                if (!RangelPathPlanningPolice.this.isTrunkRoad(from.getID())) {
                    distance *= 3;
                }
                this.g = from.getG() + distance;

            }
            this.h = worldInfo.getDistance(currentID, destination);
        }


        /**
         * @see AbstractNode#getNeighbors()
         */
        @Override
        @Nullable
        public Collection<AbstractNode> getNeighbors() {
            StandardEntity entity = RangelPathPlanningPolice.this.worldInfo.getEntity(this.getID());
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
