package rangel.module.algorithm.cluster;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.algorithm.StaticClustering;
import io.github.yufeixuan.algorithms.hungarian.optimization.HungarianAlgorithm;
import org.jetbrains.annotations.NotNull;
import rangel.utils.ConfigUtils;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;
import java.util.*;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 基于KMeans++算法的聚类模块
 * <p>
 * 用于将地图上的区域({@link Area})划分到不同的聚类,并将agent分配到不同的聚类中
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class KMeansPlusClustering extends StaticClustering {

    /**
     * 用于存取agent分配结果({@link #assignedAgentMap})的关键字
     */
    private static final String KEY_CLUSTER_ASSIGN = "clustering.assign.";

    /**
     * 用于存取聚类结果({@link #areaClusters})的关键字
     */
    private static final String KEY_CLUSTER_ENTITY = "clustering.entities.";

    /**
     * 聚类在预计算时的重复计算次数
     */
    private static final int REPEAT_PRECOMPUTE = ConfigUtils.getInteger("clustering.repeatPrecompute", 20);

    /**
     * 聚类在无预计算时的重复计算次数
     */
    private static final int REPEAT_PREPARATE = ConfigUtils.getInteger("clustering.repeatPreparate", 30);

    /**
     * 划分好的区域聚类的列表
     */
    private final List<AreaCluster> areaClusters;

    /**
     * 用于存储agent分配结果的map <br>
     * key:agent的EntityID <br>
     * value:agent所在的聚类的index
     */
    private final Map<EntityID, Integer> assignedAgentMap = new HashMap<>();

    /**
     * 期望划分的聚类数量
     */
    private final int clusterSize;


    /**
     * {@link KMeansPlusClustering}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     地图信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public KMeansPlusClustering(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        StandardEntityURN agentURN = this.agentInfo.me().getStandardURN();
        switch (agentURN) {
            case FIRE_BRIGADE -> this.clusterSize = this.scenarioInfo.getScenarioAgentsFb();
            case POLICE_FORCE -> this.clusterSize = this.scenarioInfo.getScenarioAgentsPf();
            case AMBULANCE_TEAM -> this.clusterSize = this.scenarioInfo.getScenarioAgentsAt();
            default -> this.clusterSize = 5;
        }
        this.areaClusters = new ArrayList<>(this.clusterSize);
    }


    /**
     * 预计算时执行的方法
     * <p>
     * 计算好聚类({@link #areaClusters})和agent分配结果({@link #assignedAgentMap}),并其存储到{@link PrecomputeData}中
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }

        this.divideAreasIntoClusters(REPEAT_PRECOMPUTE);
        this.assignAgentsToClusters();

        for (EntityID agentID : this.assignedAgentMap.keySet()) {
            int clusterIndex = this.assignedAgentMap.get(agentID);
            List<EntityID> areaIDs = this.areaClusters.get(clusterIndex).getMembers()
                    .stream()
                    .map(StandardEntity::getID)
                    .toList();
            precomputeData.setEntityIDList(KEY_CLUSTER_ENTITY + clusterIndex, areaIDs);
            precomputeData.setEntityID(KEY_CLUSTER_ASSIGN + clusterIndex, agentID);
        }

        return this;
    }


    /**
     * 预计算模式的初始化处理方法
     * <p>
     *     <ul>
     *         <li>使用从{@link PrecomputeData}中读取的预计算数据初始化聚类({@link #areaClusters})
     *         <li>使用从{@link PrecomputeData}中读取的预计算数据初始化agent分配结果({@link #assignedAgentMap})
     *     </ul>
     * 从参数{@link PrecomputeData}中读取聚类结果和agent分配结果,给{@link #areaClusters}和{@link #assignedAgentMap}赋值
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }

        for (int clusterindex = 0; clusterindex < this.clusterSize; clusterindex++) {
            List<Area> areas = precomputeData.getEntityIDList(KEY_CLUSTER_ENTITY + clusterindex)
                    .stream()
                    .map(this.worldInfo::getEntity)
                    .filter(Objects::nonNull)
                    .filter(Area.class::isInstance)
                    .map(Area.class::cast)
                    .toList();
            this.areaClusters.add(new AreaCluster(areas));

            EntityID agentID = precomputeData.getEntityID(KEY_CLUSTER_ASSIGN + clusterindex);
            this.assignedAgentMap.put(agentID, clusterindex);
        }

        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     * <p>
     * <ul>
     *     <li>直接调用{@link #divideAreasIntoClusters(int)}方法,计算好聚类({@link #areaClusters})
     *     <li>直接调用{@link #assignAgentsToClusters()}方法,计算好agent分配结果({@link #assignedAgentMap})
     * </ul>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }

        this.divideAreasIntoClusters(REPEAT_PREPARATE);
        this.assignAgentsToClusters();

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
    public Clustering updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }


    /**
     * 计算
     * <p>
     * 仅重写了这个方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering calc() {
        return this;
    }


    /**
     * 获得聚类的数量
     *
     * @return 聚类的数量
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public int getClusterNumber() {
        return this.clusterSize;
    }


    /**
     * 获得指定agent所属聚类的索引
     *
     * @param agent agent的{@link StandardEntity}
     * @return 所在聚类的索引
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public int getClusterIndex(@NotNull StandardEntity agent) {
        return this.getClusterIndex(agent.getID());
    }


    /**
     * 获得指定agent所属聚类的索引
     *
     * @param agentID agent的{@link EntityID}
     * @return 所在聚类的索引
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public int getClusterIndex(EntityID agentID) {
        if (!this.assignedAgentMap.containsKey(agentID)) {
            return -1;
        }
        return this.assignedAgentMap.get(agentID);
    }


    /**
     * 获得指定索引的聚类中的所有区域({@link Area})的{@link StandardEntity}
     *
     * @param clusterIndex 簇的索引
     * @return 聚类中的所有区域实体的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Collection<StandardEntity> getClusterEntities(int clusterIndex) {
        if (clusterIndex < 0 || clusterIndex >= this.clusterSize) {
            return null;
        }

        return this.getClusterEntityIDs(clusterIndex)
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Objects::nonNull)
                .toList();
    }


    /**
     * 获得指定索引的聚类中的所有区域({@link Area})的EntityID
     *
     * @param clusterIndex 聚类的索引
     * @return 聚类中的所有区域的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Collection<EntityID> getClusterEntityIDs(int clusterIndex) {
        if (clusterIndex < 0 || clusterIndex >= this.clusterSize) {
            return null;
        }
        return this.areaClusters.get(clusterIndex).getMembers()
                .stream()
                .map(StandardEntity::getID)
                .toList();
    }


    /**
     * 将区域划分为集群
     * <p>
     * 将地图上的所有区域({@link Area})划分为指定数量({@link #clusterSize})的集群,结果保存在{@link #areaClusters}中
     *
     * @param repeat 重复计算的次数
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void divideAreasIntoClusters(int repeat) {
        List<Area> areas = this.worldInfo.getEntitiesOfType(
                        ROAD,
                        HYDRANT,
                        BUILDING,
                        REFUGE,
                        GAS_STATION,
                        AMBULANCE_CENTRE,
                        FIRE_STATION,
                        POLICE_OFFICE)
                .stream()
                .sorted(Comparator.comparing(entity -> entity.getID().getValue()))
                .map(Area.class::cast)
                .toList();

        List<AreaCluster> areaClusters = KMeansPlusAlgorithm.calcCluster(repeat, areas, this.clusterSize);
        this.areaClusters.addAll(areaClusters);
    }


    /**
     * 将代理分配给集群
     * <p>
     * 使用匈牙利算法({@link HungarianAlgorithm})将所有的agent分配到划分好的集群({@link #areaClusters})中,结果保存在{@link #assignedAgentMap}中
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void assignAgentsToClusters() {
        StandardEntityURN agentURN = this.agentInfo.me().getStandardURN();
        List<StandardEntity> agents = this.worldInfo.getEntitiesOfType(agentURN)
                .stream()
                .sorted(Comparator.comparing(agent -> agent.getID().getValue()))
                .toList();

        double[][] costs = new double[this.clusterSize][this.clusterSize];
        for (int i = 0; i < this.clusterSize; ++i) {
            Human agent = (Human) agents.get(i);
            double agentX = agent.getX();
            double agentY = agent.getY();

            for (int j = 0; j < this.clusterSize; ++j) {
                Point centroid = this.areaClusters.get(j).getCentroid();
                double centroidX = centroid.getX();
                double centroidY = centroid.getY();
                costs[i][j] = Math.hypot(centroidX - agentX, centroidY - agentY);
            }
        }

        int[] result = new HungarianAlgorithm(costs).execute();
        for (int i = 0; i < clusterSize; i++) {
            EntityID entityID = agents.get(i).getID();
            this.assignedAgentMap.put(entityID, result[i]);
        }
    }

}
