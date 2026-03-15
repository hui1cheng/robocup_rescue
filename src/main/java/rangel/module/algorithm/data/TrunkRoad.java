package rangel.module.algorithm.data;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.PathPlanning;
import rangel.module.algorithm.cluster.AreaCluster;
import rangel.module.algorithm.cluster.KMeansPlusAlgorithm;
import rangel.utils.ConfigUtils;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;
import java.util.List;
import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 计算主干道的算法模块
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class TrunkRoad extends DataModule {

    /**
     * 用于存取主干道({@link #trunkRoads})的关键字
     */
    private static final String KEY_TRUNK_ROADS = "trunkRoads";

    /**
     * 主干道的数量
     */
    private static final int CLUSTER_SIZE = ConfigUtils.getInteger("dataModule.clusterSize", 10);

    /**
     * 路径规划算法模块
     */
    private final PathPlanning pathPlanning;

    /**
     * 主干道的EntityID的集合
     */
    private final Set<EntityID> trunkRoads;


    /**
     * {@link TrunkRoad}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public TrunkRoad(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.trunkRoads = new HashSet<>();

        this.pathPlanning = moduleManager.getModule("TrunkRoad.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.registerModule(this.pathPlanning);
    }


    /**
     * {@inheritDoc}
     * <p>
     * 判断是否是主干道
     *
     * @param entityID 要判断的实体的EntityID
     * @return true: 是主干道 || false: 不是主干道
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public boolean getBoolean(EntityID entityID) {
        return this.trunkRoads.contains(entityID);
    }


    /**
     * {@inheritDoc}
     * <p>
     * 获取主干道的EntityID的集合
     *
     * @return 主干道的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Collection<EntityID> getData() {
        return this.trunkRoads;
    }


    /**
     * 预计算时执行的方法
     * <p>
     * 在预计算阶段初始化主干道,并将计算出的主干道存入参数{@linkplain PrecomputeData precomputeData}中
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public DataModule precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }

        this.calc();
        precomputeData.setEntityIDList(KEY_TRUNK_ROADS, new ArrayList<>(this.trunkRoads));
        return this;
    }


    /**
     * 预计算模式的初始化处理方法
     * <p>
     * 从参数{@linkplain PrecomputeData precomputeData}中获取主干道,并添加到{@link #trunkRoads}中
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public DataModule resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }

        this.trunkRoads.addAll(precomputeData.getEntityIDList(KEY_TRUNK_ROADS));
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
    public DataModule preparate() {
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
    public DataModule updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }


    /**
     * 计算主干道({@link #trunkRoads})
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public DataModule calc() {
        if (this.trunkRoads.size() == 0) {
            List<Area> areas = this.worldInfo.getEntitiesOfType(
                            BUILDING,
                            REFUGE,
                            GAS_STATION,
                            AMBULANCE_CENTRE,
                            FIRE_STATION,
                            POLICE_OFFICE
                    )
                    .stream()
                    .sorted(Comparator.comparing(entity -> entity.getID().getValue()))
                    .map(Area.class::cast)
                    .toList();

            List<AreaCluster> areaClusters = KMeansPlusAlgorithm.calcCluster(10, areas, CLUSTER_SIZE);

            List<EntityID> result = new ArrayList<>();
            for (int i = 0; i < CLUSTER_SIZE; i++) {
                Point centroid = areaClusters.get(i).getCentroid();
                double centroidX = centroid.getX();
                double centroidY = centroid.getY();
                areaClusters.get(i).getMembers()
                        .stream()
                        .min(Comparator.comparing(area -> Math.hypot(area.getX() - centroidX, area.getY() - centroidY)))
                        .ifPresent(area -> result.add(area.getID()));
            }

            for (int i = 0; i < CLUSTER_SIZE; ++i) {
                for (int j = i + 1; j < CLUSTER_SIZE; ++j) {
                    List<EntityID> path = this.pathPlanning
                            .setFrom(result.get(i))
                            .setDestination(result.get(j))
                            .calc()
                            .getResult();
                    this.trunkRoads.addAll(path);
                }
            }
        }
        return this;
    }
}
