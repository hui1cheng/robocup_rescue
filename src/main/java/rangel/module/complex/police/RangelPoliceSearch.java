package rangel.module.complex.police;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.information.MessageCivilian;
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
import static rescuecore2.standard.entities.StandardEntityURN.FIRE_BRIGADE;

/**
 * 警察的搜索算法
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelPoliceSearch extends Search {

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
     * 未搜索过的建筑 <br>
     * key : 建筑物的EntityID <br>
     * value : 可能埋藏在该建筑的人的EntityID的集合
     */
    private final Map<EntityID, Set<EntityID>> unSearchedBuildingMap;

    /**
     * 已经搜索过的建筑的EntityID的集合
     */
    private final Set<EntityID> searchedBuildingIDs;

    /**
     * 延期搜索的建筑的EntityID的集合 <br>
     * 延期搜索的建筑在选择时的权重会降低,降低的程度由{@link #priorityReduction}决定
     */
    private final Set<EntityID> deferredSearchedBuildingIDs;

    /**
     * 高优先级的建筑的EntityID的集合, <br>
     * 由位于该建筑内的消防队的数量决定
     */
    private final Set<EntityID> highPriorityBuildingIDs;

    /**
     * 掩埋市民的建筑 <br>
     * key: 被掩埋市民所在位置的EntityID <br>
     * value: 等待时间
     */
    private final Map<EntityID, Integer> buriedCivilianBuildingMap;

    /**
     * 优先级衰减值
     */
    private double priorityReduction;

    /**
     * 搜索目标的EntityID
     */
    private EntityID result;


    /**
     * {@link RangelPoliceSearch}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelPoliceSearch(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.priorityReduction = 0;
        this.result = null;

        this.responsibleZoneBuildingIDs = new HashSet<>();
        this.unSearchedBuildingMap = new HashMap<>();
        this.searchedBuildingIDs = new HashSet<>();
        this.deferredSearchedBuildingIDs = new HashSet<>();
        this.highPriorityBuildingIDs = new HashSet<>();
        this.buriedCivilianBuildingMap = new HashMap<>();

        this.clustering = moduleManager.getModule("RangelPoliceSearch.Clustering", "adf.impl.module.algorithm.KMeansClustering");
        this.immovableHuman = moduleManager.getModule("RangelPoliceSearch.ImmovableHuman");
        this.blockedHuman = moduleManager.getModule("RangelPoliceSearch.BlockedHuman");

        this.registerModule(this.clustering);
        this.registerModule(this.immovableHuman);
        this.registerModule(this.blockedHuman);
    }


    /**
     * 获得当前的搜索目标
     *
     * @return 当前的搜索目标的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public EntityID getTarget() {
        return this.result;
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
     * 每个回合都会执行这个方法来更新agent所持有的信息
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

        //如果责任区内建筑为空，则初始化责任区
        if (this.responsibleZoneBuildingIDs.isEmpty()) {
            this.clustering.calc();
            this.updateResponsibleZoneBuildingIDs(false);
        }

        //如果自身没有被埋但不能到达，将当前的搜索目标添加到this.delayed中
        Human human = (Human) this.agentInfo.me();
        if (this.isCannotReach() && human.getBuriedness() == 0) {
            this.deferredSearchedBuildingIDs.add(this.result);
        }

        this.updateSearchedBuildingIDs();
        this.updateUnSearchedBuildingMap();
        this.updateHighPriorityBuildingIDs();
        this.updateBuriedCivilianBuildingMap();

        this.receiveMessage(messageManager);

        return this;
    }


    /**
     * 计算搜索目标
     * <p>
     * <ol>
     *     <li>如果已搜索到职责范围内的90%，则扩展该集群
     *     <li>取责任区内未搜索过的建筑物,排序并取优先级最高的一个未搜索目标
     * </ol>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Search calc() {
        //如果已经探索了范围内90%的建筑物，则扩展范围
        if (this.searchedBuildingIDs.size() >= this.responsibleZoneBuildingIDs.size() * 0.9) {
            this.updateResponsibleZoneBuildingIDs(true);
        }

        //先从被掩埋的市民中选择一个最近建筑的作为搜索目标
        this.result = this.buriedCivilianBuildingMap.keySet()
                .stream()
                .filter(buildingID -> this.buriedCivilianBuildingMap.get(buildingID) == 0)
                .min((buildingA, buildingB) -> {
                    //先比较是否为高优先级建筑
                    boolean isHighPriorityA = highPriorityBuildingIDs.contains(buildingA);
                    boolean isHighPriorityB = highPriorityBuildingIDs.contains(buildingB);
                    if (!isHighPriorityA && isHighPriorityB) {
                        return -1;
                    }
                    if (isHighPriorityA && !isHighPriorityB) {
                        return 1;
                    }
                    //再比较距离
                    int distanceA = this.worldInfo.getDistance(buildingA, this.agentInfo.getID());
                    int distanceB = this.worldInfo.getDistance(buildingB, this.agentInfo.getID());
                    return Integer.compare(distanceA, distanceB);

                })
                .orElse(null);
        if (this.result != null) {
            return this;
        }

        //再从责任区内未搜索过的建筑物中选择一个最近建筑的作为搜索目标
        Set<EntityID> candidates = new HashSet<>(this.responsibleZoneBuildingIDs);
        candidates.removeAll(this.searchedBuildingIDs);
        this.result = candidates
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
        return this;
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
     * 更新已经搜索过的建筑物({@link #searchedBuildingIDs})
     * <p>
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
                .filter(this::isSearched)
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
     *     <li>从未搜索的建筑({@link #unSearchedBuildingMap})的value中移除救助完毕的市民
     * </ol>
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateUnSearchedBuildingMap() {
        //从配置文件中读取声音传播范围
        int voiceRange = this.scenarioInfo.getRawConfig().getIntValue("comms.channels.0.range");
        //听觉范围内的所有建筑
        Set<EntityID> buildings = this.worldInfo.getObjectsInRange(this.agentInfo.getID(), voiceRange)
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
        buildings.forEach(buildingEntityID ->
                this.unSearchedBuildingMap
                        .computeIfAbsent(buildingEntityID, key -> new HashSet<>())
                        .addAll(civilians));

        //从未搜索的建筑中移除已经搜索过的建筑
        this.unSearchedBuildingMap.keySet().removeAll(this.searchedBuildingIDs);

        //this.unSearchedBuildingMap的value移除救助完毕的市民
        Set<EntityID> rescuedCivilian = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .filter(Civilian::isBuriednessDefined)
                .filter(civilian -> civilian.getBuriedness() <= 0)
                .map(Civilian::getID)
                .collect(toSet());
        this.unSearchedBuildingMap.values().forEach(vs -> vs.removeAll(rescuedCivilian));
    }


    /**
     * 更新高优先级的建筑({@link #highPriorityBuildingIDs})
     * <p>
     * 将消防队所在建筑物的EntityID添加到highPriorityPosition作为动作目标候选
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateHighPriorityBuildingIDs() {
        this.highPriorityBuildingIDs.clear();
        this.worldInfo.getEntityIDsOfType(FIRE_BRIGADE)
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(FireBrigade.class::isInstance)
                .map(FireBrigade.class::cast)
                .filter(FireBrigade::isPositionDefined)
                .map(FireBrigade::getPosition)
                .map(this.worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID)
                .forEach(this.highPriorityBuildingIDs::add);
    }


    /**
     * 更新掩埋市民的建筑({@link #buriedCivilianBuildingMap})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateBuriedCivilianBuildingMap() {
        for (EntityID buildingID : this.buriedCivilianBuildingMap.keySet()) {
            int time = Math.max(this.buriedCivilianBuildingMap.get(buildingID) - 1, 0);
            this.buriedCivilianBuildingMap.put(buildingID, time);
        }

        //感知到的被掩埋的市民
        Set<Civilian> perceivedBuriedCivilian = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .filter(Civilian::isBuriednessDefined)
                .filter(civilian -> civilian.getBuriedness() > 0)
                .filter(civilian -> !(this.worldInfo.getEntity(civilian.getPosition()) instanceof Refuge))
                .collect(toSet());
        for (Civilian civilian : perceivedBuriedCivilian) {
            this.buriedCivilianBuildingMap.put(civilian.getPosition(), civilian.getBuriedness());
            if (civilian.isHPDefined() && civilian.getHP() <= 0) {
                this.buriedCivilianBuildingMap.remove(civilian.getPosition());
            }
        }

        EntityID agentPosition = this.agentInfo.getPosition();
        if (this.buriedCivilianBuildingMap.containsKey(agentPosition) && this.buriedCivilianBuildingMap.get(agentPosition) == 0) {
            this.buriedCivilianBuildingMap.remove(agentPosition);
        }
    }


    /**
     * 更新优先级衰减值({@link #priorityReduction})
     * <p>
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
     * 判断建筑是否已经搜索
     *
     * @param building 建筑
     * @return true 已经搜索 || false 未搜索
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isSearched(@NotNull Building building) {
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
     * 判断自身是否不能到达
     *
     * @return true:不能到达 || false:可以到达
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isCannotReach() {
        if (this.result == null) {
            return false;
        }
        EntityID agentID = this.agentInfo.getID();
        return this.immovableHuman.calc().getBoolean(agentID) || this.blockedHuman.calc().getBoolean(agentID);
    }


    /**
     * 接受消息
     * <p>
     * 从消息管理器({@link MessageManager})中获取消息,并从{@link MessageCivilian}获取到消息中的市民ID, <br>
     * 如果该平民在自身所在的簇中,则将该平民添加到对应的掩埋市民的建筑({@link #buriedCivilianBuildingMap})中
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void receiveMessage(@NotNull MessageManager messageManager) {
        messageManager.getReceivedMessageList(MessageCivilian.class)
                .stream()
                .filter(MessageCivilian.class::isInstance)
                .map(MessageCivilian.class::cast)
                .forEach(messageCivilian -> {
                    EntityID me = this.agentInfo.getID();
                    EntityID targetID = messageCivilian.getPosition();
                    int index = this.clustering.getClusterIndex(me);
                    Collection<EntityID> entities = this.clustering.getClusterEntityIDs(index);
                    if (entities.contains(targetID)) {
                        this.buriedCivilianBuildingMap.put(targetID, messageCivilian.getBuriedness() - 1);
                    }
                });
    }
}
