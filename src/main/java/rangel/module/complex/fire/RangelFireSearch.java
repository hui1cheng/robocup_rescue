package rangel.module.complex.fire;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.complex.Search;
import org.jetbrains.annotations.NotNull;
import rangel.module.algorithm.data.DataModule;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * 消防队的搜索算法
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelFireSearch extends Search {

    /**
     * 确定每个代理的责任区域的聚类模块
     */
    private final Clustering clustering;

    /**
     * 计算无法移动的人的算法模块
     */
    private final DataModule immovableHuman;

    /**
     * 计算被困在障碍里的人的算法模块
     */
    private final DataModule blockedHuman;

    /**
     * 自身责任区内的建筑的EntityID的集合
     */
    private final Set<EntityID> responsibleZoneBuildingIDs;

    /**
     * 已经搜索过的建筑的EntityID的集合
     */
    private final Set<EntityID> searchedBuildingIDs;

    /**
     * 未搜索过的建筑 <br>
     * key : 建筑物的EntityID <br>
     * value : 可能埋藏在该建筑的人的EntityID的集合
     */
    private final Map<EntityID, Set<EntityID>> unSearchedBuildingMap;

    /**
     * 延期搜索的建筑的EntityID的集合 <br>
     * 延期搜索的建筑在选择时的权重会降低,降低的程度由{@link #priorityReduction}决定
     */
    private final Set<EntityID> deferredSearchedBuildingIDs;

    /**
     * 优先级衰减值
     */
    private double priorityReduction;

    /**
     * 要搜索的实体的EntityID
     */
    private EntityID result;


    /**
     * {@link RangelFireSearch}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelFireSearch(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.priorityReduction = 0;
        this.result = null;

        this.responsibleZoneBuildingIDs = new HashSet<>();
        this.searchedBuildingIDs = new HashSet<>();
        this.unSearchedBuildingMap = new HashMap<>();
        this.deferredSearchedBuildingIDs = new HashSet<>();

        this.clustering = moduleManager.getModule("RangelFireSearch.Clustering", "adf.impl.module.algorithm.KMeansClustering");
        this.immovableHuman = moduleManager.getModule("RangelFireSearch.ImmovableHuman");
        this.blockedHuman = moduleManager.getModule("RangelFireSearch.BlockedHuman");

        this.registerModule(this.clustering);
        this.registerModule(this.immovableHuman);
        this.registerModule(this.blockedHuman);
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
    public Search precompute(PrecomputeData precomputeData) {
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
    public Search resume(PrecomputeData precomputeData) {
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
    public Search preparate() {
        super.preparate();
        return this;
    }


    /**
     * 每个回合都会执行这个方法来更新信息
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Search updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        if (this.responsibleZoneBuildingIDs.isEmpty()) {
            this.clustering.calc();
            this.updateResponsibleZoneBuildingIDs(false);
        }

        Human human = (Human) this.agentInfo.me();
        if (!this.isReachable() && human.getBuriedness() == 0) {
            this.deferredSearchedBuildingIDs.add(this.result);
        }

        this.updateSearchedBuildingIDs();
        this.updateUnSearchedBuildings();

        return this;
    }


    /**
     * 计算搜索目标
     * <p>
     * <ol>
     *     <li>如果已经探索了责任区内的超过90%的建筑,则扩展责任区
     *     <li>从掩埋着市民的建筑中选择一个最近的作为搜索目标
     * </ol>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Search calc() {
        if (this.searchedBuildingIDs.size() >= this.responsibleZoneBuildingIDs.size() * 0.9) {
            this.updateResponsibleZoneBuildingIDs(true);
        }

        this.result = selectTargetFromUnSearchedBuildings();
        return this;
    }


    /**
     * 获得当前的搜索结果
     *
     * @return 当前的搜索结果的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public EntityID getTarget() {
        return this.result;
    }


    /**
     * 更新自身的责任区({@link #responsibleZoneBuildingIDs})
     * <p>
     * 仅建筑会被添加到责任区
     *
     * @param isExpand true:扩展责任区 || false:初始化责任区
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateResponsibleZoneBuildingIDs(boolean isExpand) {
        int clusterNumber = this.clustering.getClusterNumber();
        int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());

        //是否扩展责任区
        if (!isExpand) {
            Collection<EntityID> clusterEntityIDs = this.clustering.getClusterEntityIDs(clusterIndex);
            if (clusterEntityIDs != null) {
                Set<EntityID> buildings = clusterEntityIDs
                        .stream()
                        .map(this.worldInfo::getEntity)
                        .filter(Building.class::isInstance)
                        .map(StandardEntity::getID)
                        .collect(toSet());
                this.responsibleZoneBuildingIDs.addAll(buildings);
            }
        } else {
            //原来的责任区的大小
            int originalSize = this.responsibleZoneBuildingIDs.size();
            for (int i = 1; i < clusterNumber; i++) {
                Collection<EntityID> clusterEntityIDs = this.clustering.getClusterEntityIDs(clusterIndex + i * clusterNumber);
                if (clusterEntityIDs != null) {
                    Set<EntityID> buildings = clusterEntityIDs
                            .stream()
                            .map(this.worldInfo::getEntity)
                            .filter(Building.class::isInstance)
                            .map(StandardEntity::getID)
                            .collect(toSet());
                    this.responsibleZoneBuildingIDs.addAll(buildings);
                    //如果扩展成功则退出循环
                    if (this.responsibleZoneBuildingIDs.size() > originalSize) {
                        break;
                    }
                }
            }
        }

        //初始化责任区或者扩展责任区后,需要重新计算优先级衰减值
        this.updatePriorityReduction();
    }


    /**
     * 更新已经搜索过的建筑物{@link #searchedBuildingIDs}, <br>
     * 包括自身当前所在的建筑物和安全的建筑物
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateSearchedBuildingIDs() {
        //当前的位置如果是建筑则将其添加到this.searchedBuildingIDs中
        EntityID position = this.agentInfo.getPosition();
        if (this.worldInfo.getEntity(position) instanceof Building) {
            this.searchedBuildingIDs.add(position);
        }

        //将已经探索过的建筑物添加到this.searchedBuildingIDs中
        this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(Building.class::cast)
                .filter(this::isPerceived)
                .map(Building::getID)
                .forEach(this.searchedBuildingIDs::add);

        //将安全的建筑即损坏程度为0的建筑添加到this.searchedBuildingIDs中
        this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(Building.class::cast)
                .filter(Building::isBrokennessDefined)
                .filter(building -> building.getBrokenness() == 0)
                .map(Building::getID)
                .forEach(this.searchedBuildingIDs::add);
    }


    /**
     * 更新未搜索的建筑({@link #unSearchedBuildingMap})
     * <p>
     * <ol>
     *     <li>将听觉范围内的建筑物添加到未搜索的建筑({@link #unSearchedBuildingMap})的key
     *     <li>将听到的需要帮助的市民添加到未搜索的建筑({@link #unSearchedBuildingMap})的key对应的value中
     *     <li>从未搜索的建筑({@link #unSearchedBuildingMap})的key中移除已经搜索过的建筑({@link #searchedBuildingIDs})
     *     <li>从未搜索的建筑({@link #unSearchedBuildingMap})的value中移除已经感知到的市民
     *     <li>从未搜索的建筑({@link #unSearchedBuildingMap})的value中移除救助完毕的市民
     * </ol>
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateUnSearchedBuildings() {
        //从配置文件中读取声音传播范围
        int voiceRange = this.scenarioInfo.getRawConfig().getIntValue("comms.channels.0.range");
        //听觉范围内的所有建筑
        Set<EntityID> buildingIDs = this.worldInfo.getObjectsInRange(this.agentInfo.getID(), voiceRange)
                .stream()
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID)
                .collect(toSet());

        //听到的需要帮助的市民
        Set<EntityID> civilians = new HashSet<>();
        //听到的所有声音
        Collection<Command> heard = this.agentInfo.getHeard();
        if (heard != null) {
            heard.stream()
                    .filter(AKSpeak.class::isInstance)
                    .map(AKSpeak.class::cast)
                    .filter(akSpeak -> akSpeak.getChannel() == 0)
                    .map(AKSpeak::getAgentID)
                    .map(this.worldInfo::getEntity)
                    .filter(Civilian.class::isInstance)
                    .map(StandardEntity::getID)
                    .forEach(civilians::add);
        }

        //将听到的需要帮助的市民以及听觉范围内的建筑添加到未搜索的建筑中
        buildingIDs.forEach(buildingEntityID -> this.unSearchedBuildingMap.computeIfAbsent(buildingEntityID, k -> new HashSet<>()).addAll(civilians));

        //从未搜索的建筑中移除已经搜索过的建筑
        this.unSearchedBuildingMap.keySet().removeAll(this.searchedBuildingIDs);

        //从未搜索的建筑中移除感知到的市民
        Set<EntityID> perceivedCivilianIDs = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(StandardEntity::getID)
                .collect(toSet());
        this.unSearchedBuildingMap.values().forEach(civilianIDs -> civilianIDs.removeAll(perceivedCivilianIDs));

        //从未搜索的建筑中移除救助完毕的市民
        Set<EntityID> rescuedCivilianIDs = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .filter(Civilian::isBuriednessDefined)
                .filter(civilian -> civilian.getBuriedness() <= 0)
                .map(Civilian::getID)
                .collect(toSet());
        this.unSearchedBuildingMap.values().forEach(civilianIDs -> civilianIDs.removeAll(rescuedCivilianIDs));
    }


    /**
     * 更新{@link #priorityReduction}, <br>
     * 使用簇的对角线长度作为优先级衰减值
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updatePriorityReduction() {
        List<Building> buildings = this.responsibleZoneBuildingIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(Building.class::cast)
                .toList();

        int minX = buildings.stream().mapToInt(Area::getX).min().orElse(0);
        int minY = buildings.stream().mapToInt(Area::getY).min().orElse(0);
        int maxX = buildings.stream().mapToInt(Area::getX).max().orElse(0);
        int maxY = buildings.stream().mapToInt(Area::getY).max().orElse(0);

        this.priorityReduction = Math.hypot(maxX - minX, maxY - minY);
    }


    /**
     * 从责任区内未搜索的建筑({@link #unSearchedBuildingMap})中选择一个建筑作为目标
     *
     * @return 目标建筑的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private EntityID selectTargetFromUnSearchedBuildings() {
        HashSet<EntityID> buildingIDs = new HashSet<>(this.responsibleZoneBuildingIDs);
        buildingIDs.removeAll(this.searchedBuildingIDs);
        return buildingIDs
                .stream()
                .max((buildingA, buildingB) -> {
                    int result;
                    //先比较建筑内需要帮助的人数,人数越多优先级越高
                    int humanNumberA = unSearchedBuildingMap.getOrDefault(buildingA, Collections.emptySet()).size();
                    int humanNumberB = unSearchedBuildingMap.getOrDefault(buildingB, Collections.emptySet()).size();
                    result = Integer.compare(humanNumberA, humanNumberB);
                    //如果人数相同,再比较自身距离建筑的距离,距离越近优先级越高
                    if (result == 0) {
                        double distanceA = worldInfo.getDistance(buildingA, agentInfo.getID()) + (deferredSearchedBuildingIDs.contains(buildingA) ? priorityReduction : 0.0);
                        double distanceB = worldInfo.getDistance(buildingB, agentInfo.getID()) + (deferredSearchedBuildingIDs.contains(buildingB) ? priorityReduction : 0.0);
                        result = Double.compare(distanceB, distanceA);
                    }
                    return result;
                })
                .orElse(null);
    }


    /**
     * 判断建筑是否已经感知到了
     *
     * @param building 建筑
     * @return true 已经感知 || false 未感知
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isPerceived(@NotNull Building building) {
        int max = this.scenarioInfo.getPerceptionLosMaxDistance();
        Line2D line = new Line2D(
                new Point2D(this.agentInfo.getX(), this.agentInfo.getY()),
                new Point2D(building.getX(), building.getY())
        );

        if (line.getDirection().getLength() >= max * 0.8) {
            return false;
        }
        for (Edge edge : building.getEdges()) {
            if (!edge.isPassable()) {
                continue;
            }

            Point2D intersection = GeometryTools2D.getSegmentIntersectionPoint(line, edge.getLine());
            if (intersection != null) {
                return true;
            }
        }

        return false;
    }


    /**
     * 判断自身是否可以到达
     *
     * @return true 可以到达 || false 不可到达
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isReachable() {
        if (this.result == null) {
            return false;
        }
        EntityID agentID = this.agentInfo.getID();
        return !(this.immovableHuman.calc().getBoolean(agentID) || this.blockedHuman.calc().getBoolean(agentID));
    }

}
