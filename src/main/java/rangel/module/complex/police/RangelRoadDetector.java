package rangel.module.complex.police;


import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.core.agent.communication.standard.bundle.information.MessageCivilian;
import adf.core.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.core.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.algorithm.PathPlanning;
import adf.core.component.module.complex.RoadDetector;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Geometry;
import rangel.module.algorithm.data.DataModule;
import rangel.utils.ConfigUtils;
import rangel.utils.GeometryUtils;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.HIGH;
import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.NORMAL;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static rangel.module.communication.RangelMessage.HELP_CLEAR;
import static rangel.module.communication.RangelMessage.HELP_RESCUE;
import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 警察的道路探测算法
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
@Slf4j
public class RangelRoadDetector extends RoadDetector {

    /**
     * 路径规划算法
     */
    private final PathPlanning pathPlanning;

    /**
     * 确定每个代理的责任区域的聚类模块
     */
    private final Clustering clustering;

    /**
     * 计算主干道的模块
     */
    private final DataModule trunkRoad;

    /**
     * 计算被困在障碍里的人的算法模块
     */
    private final DataModule blockedHuman;

    /**
     * agent的体型半径
     */
    private final double agentRadius;

    /**
     * 自身的职责范围内实体的EntityID的集合
     */
    private final Set<EntityID> responsibleZoneEntityIDs;

    /**
     * 候选目标 <br>
     * key:目标实体的EntityID <br>
     * value:优先级（1（高）〜 8（低））
     */
    private final Map<EntityID, Integer> candidateTargetMap;

    /**
     * 已完成的目标的EntityID的集合
     */
    private final Set<EntityID> completedTargets;

    /**
     * 探测目标的EntityID
     */
    private EntityID result;

    /**
     * 当自身被掩埋时，是否已经呼叫消防队救援
     */
    private boolean isCalledFire = false;

    /**
     * {@link RangelRoadDetector}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelRoadDetector(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.result = null;

        this.responsibleZoneEntityIDs = new HashSet<>();
        this.candidateTargetMap = new HashMap<>();
        this.completedTargets = new HashSet<>();

        this.agentRadius = ConfigUtils.getDouble("humanRadius", 500.0);

        this.pathPlanning = moduleManager.getModule("RangelRoadDetector.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.clustering = moduleManager.getModule("RangelRoadDetector.Clustering", "adf.impl.module.algorithm.KMeansClustering");
        this.trunkRoad = moduleManager.getModule("RangelRoadDetector.TrunkRoad");
        this.blockedHuman = moduleManager.getModule("RangelRoadDetector.BlockedHuman");

        this.registerModule(this.pathPlanning);
        this.registerModule(this.clustering);
        this.registerModule(this.trunkRoad);
        this.registerModule(this.blockedHuman);
    }


    /**
     * 获得当前的探测目标
     *
     * @return 当前的探测目标的EntityID
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
    public RoadDetector precompute(PrecomputeData precomputeData) {
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
    public RoadDetector resume(PrecomputeData precomputeData) {
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
    public RoadDetector preparate() {
        super.preparate();
        return this;
    }


    /**
     * 每个回合都会执行这个方法来更新agent所持有的信息
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public RoadDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        StandardEntity position = this.worldInfo.getPosition(this.agentInfo.getID());
        if (position instanceof Road) {
            final Area area = (Area) position;
            // 当前区域不存在障碍
            if (area.getBlockades() == null || area.getBlockades().isEmpty()) {
                // 从候选目标中移除该区域
                this.candidateTargetMap.remove(position.getID());
            }
        }

        // 当行动目标候选者从负责的集群中死亡时更新集群
        if (this.responsibleZoneEntityIDs.isEmpty()) {
            this.clustering.calc();
            this.updateResponsibleZoneEntityIDs(false);
            this.updateCandidateTargetMap();
        }

        //如果代理找到埋藏代理，它会在自己的集群中添加埋藏代理集群。
        this.updateCompletedTargets();
        this.putRequestsWithPerception();
        this.putNeighborBlockadesWithPerception();

        // 接收和发送消息
        this.receiveMessage(messageManager);
        this.sendMessage(messageManager);

        // 获取与当前警察处于同一区域的警察，并按id排序
        List<EntityID> surroundingPolices = this.worldInfo.getEntitiesOfType(POLICE_FORCE)
                .stream()
                .map(PoliceForce.class::cast)
                .filter(policeForce -> policeForce.getPosition().equals(this.agentInfo.getPosition()))
                .map(PoliceForce::getID)
                .sorted(Comparator.comparing(EntityID::getValue))
                .toList();
        // 获取排序后当前警察的索引
        int index = surroundingPolices.indexOf(this.agentInfo.getID());
        log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",周围警察" + surroundingPolices + ",index:" + index + ",上回合目标:" + this.result);

        // 索引值大于1的警察移除上一回合的目标及其周围的目标
        if (index > 1) {
            this.candidateTargetMap.remove(this.result);
            StandardEntity entity = this.worldInfo.getEntity(this.result);
            if (entity instanceof Road road) {
                road.getNeighbours()
                        .stream()
                        .filter(this.candidateTargetMap::containsKey)
                        .forEach(this.candidateTargetMap::remove);
            }
            log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",周围警察大于2,index:" + index + ",删除目标:" + this.result);
        }
        return this;
    }


    /**
     * 在每个步骤中，代理从方法任务中提取五个高优先级目标，代理使用这些目标来确定行动目标。
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public RoadDetector calc() {
        this.result = null;
        if (this.agentInfo.getTime() < this.scenarioInfo.getKernelAgentsIgnoreuntil()) {
            return this;
        }

        this.result = this.candidateTargetMap.keySet()
                .stream()
                .filter(t -> !this.completedTargets.contains(t))
                .sorted((entityA, entityB) -> {
                    int result;
                    //先比较优先级
                    Integer priorityA = this.candidateTargetMap.get(entityA);
                    Integer priorityB = this.candidateTargetMap.get(entityB);
                    result = Integer.compare(priorityA, priorityB);
                    //如果优先级相同，再比较欧式距离
                    if (result == 0) {
                        int distanceA = this.worldInfo.getDistance(this.agentInfo.getID(), entityA);
                        int distanceB = this.worldInfo.getDistance(this.agentInfo.getID(), entityB);
                        result = Integer.compare(distanceA, distanceB);
                    }
                    return result;
                })
                .limit(5)
                .min((entityA, entityB) -> {
                    int result;
                    //先比较优先级
                    Integer priorityA = this.candidateTargetMap.get(entityA);
                    Integer priorityB = this.candidateTargetMap.get(entityB);
                    result = Integer.compare(priorityA, priorityB);
                    //如果优先级相同，再比较曼哈顿距离
                    if (result == 0) {
                        double distanceA = this.getManhattanDistance(this.agentInfo.getPosition(), entityA);
                        double distanceB = this.getManhattanDistance(this.agentInfo.getPosition(), entityB);
                        result = Double.compare(distanceA, distanceB);
                    }
                    return result;
                })
                .orElse(null);

        if (this.result == null) {
            this.updateResponsibleZoneEntityIDs(true);
            this.updateCandidateTargetMap();
        }
        return this;
    }


    /**
     * 计算两点间的曼哈顿距离
     * <p>
     * {@link WorldInfo#getDistance(EntityID, EntityID)}方法计算的是欧式距离 <br>
     * 曼哈顿距离使用寻路算法({@link PathPlanning})可以计算出更为精确的距离，但是会消耗更多的计算资源
     *
     * @param from        起点
     * @param destination 终点
     * @return 两点间的曼哈顿距离
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private double getManhattanDistance(EntityID from, EntityID destination) {
        List<EntityID> path = this.pathPlanning
                .setFrom(from)
                .setDestination(destination)
                .calc()
                .getResult();
        double distance = 0;
        for (int i = 1; i < path.size(); ++i) {
            distance += this.worldInfo.getDistance(path.get(i - 1), path.get(i));
        }
        return distance;
    }


    /**
     * 更新自身的责任区({@link #responsibleZoneEntityIDs})
     *
     * @param isExpand true:扩展责任区 || false:初始化责任区
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateResponsibleZoneEntityIDs(boolean isExpand) {
        int clusterNumber = this.clustering.getClusterNumber();
        int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        if (!isExpand) {
            int index = this.clustering.getClusterIndex(this.agentInfo.getID());
            Collection<EntityID> ids = this.clustering.getClusterEntityIDs(index);
            this.responsibleZoneEntityIDs.addAll(ids);
        } else {
            //原来的责任区的大小
            int originalSize = this.responsibleZoneEntityIDs.size();
            for (int i = 1; i < clusterNumber; ++i) {
                Collection<EntityID> ids = this.clustering.getClusterEntityIDs(clusterIndex + i * clusterNumber);
                this.responsibleZoneEntityIDs.addAll(ids);
                //如果扩展成功则退出循环
                if (this.responsibleZoneEntityIDs.size() > originalSize) {
                    break;
                }
            }
        }
    }


    /**
     * 更新候选目标({@link #candidateTargetMap})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateCandidateTargetMap() {
        //将集群中包含的实体注册为候选动作目标
        this.responsibleZoneEntityIDs.forEach(entityID -> this.putTask(entityID, 8));

        //将作为优先道路的区域登记为行动目标候选
        this.responsibleZoneEntityIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID)
                .filter(this.responsibleZoneEntityIDs::contains)
                .forEach(entityID -> this.putTask(entityID, 7));

        //将责任范围内指定为主干道的地区登记为行动对象候补
        this.trunkRoad.calc().getData()
                .stream()
                .filter(this.responsibleZoneEntityIDs::contains)
                .map(this.worldInfo::getEntity)
                .filter(Road.class::isInstance)
                .map(StandardEntity::getID)
                .forEach(entityID -> this.putTask(entityID, 6));

        //将灾害救援队作为初始位置的建筑物登记为行动目标候选
        this.worldInfo.getEntityIDsOfType(
                        FIRE_BRIGADE,
                        POLICE_FORCE,
                        AMBULANCE_TEAM
                )
                .stream()
                .filter(entityID -> !entityID.equals(this.agentInfo.getID()))
                .map(entityID -> this.worldInfo.getPosition(1, entityID))
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID)
                .filter(this.responsibleZoneEntityIDs::contains)
                .forEach(entityID -> this.putTask(entityID, 5));

        //将责任范围内的难民登记为行动目标候选人
        this.responsibleZoneEntityIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Refuge.class::isInstance)
                .map(StandardEntity::getID)
                .forEach(entityID -> this.putTask(entityID, 0));
    }


    /**
     * 将作为 PF 的行动目标候选者的实体 ID 与优先级一起注册
     *
     * @param task     作为 PF 的行动目标的候选实体的实体 ID
     * @param priority 具有任务优先级的整数（1（高）到 8（低））
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void putTask(EntityID task, int priority) {
        int current = this.candidateTargetMap.getOrDefault(task, Integer.MAX_VALUE);
        this.candidateTargetMap.put(task, Math.min(current, priority));
    }


    /**
     * 更新已完成的目标({@link #completedTargets})
     * <p>
     * 对于视野内的区域，将不是任务目标的道路注册为已完成
     * 可以通过也标记为已完成
     * 尚未完全清除封锁的道路也标记为已完成
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateCompletedTargets() {
        EntityID position = this.agentInfo.getPosition();

        //视野内的实体中，已完成任务的道路
        Map<EntityID, EntityID> focused = new HashMap<>();
        for (EntityID entityID : this.worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = this.worldInfo.getEntity(entityID);
            if (entity instanceof Area) {
                EntityID value = null;
                if (!entityID.equals(position) && entity instanceof Building) {
                    List<EntityID> path = this.pathPlanning
                            .setFrom(this.agentInfo.getPosition())
                            .setDestination(entityID)
                            .calc()
                            .getResult();

                    if (path.isEmpty() || !path.get(0).equals(this.agentInfo.getPosition())) {
                        path.add(0, this.agentInfo.getPosition());
                    }

                    for (int i = path.size() - 1; i >= 0; --i) {
                        EntityID id = path.get(i);
                        if (this.worldInfo.getEntity(id) instanceof Road) {
                            value = id;
                            break;
                        }
                    }
                } else {
                    value = entityID;
                }

                if (this.isRoadCompleted(value)) {
                    focused.put(entityID, value);
                }
            }
        }

        Set<EntityID> scope = new HashSet<>();
        Queue<EntityID> queue = new LinkedList<>();
        queue.add(position);

        while (!queue.isEmpty()) {
            final EntityID id = queue.remove();
            if (!focused.containsKey(id)) {
                continue;
            }
            if (scope.contains(id)) {
                continue;
            }

            scope.add(id);
            if (this.worldInfo.getEntity(id) instanceof Area area) {
                queue.addAll(area.getNeighbours());
            }
        }

        focused.keySet()
                .stream()
                .filter(key -> scope.contains(focused.get(key)))
                .forEach(this.completedTargets::add);
    }


    /**
     * 判断对象道路是否可以通过
     *
     * @param id 目标道路的EntityID
     * @return 是否可以通过
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isRoadCompleted(EntityID id) {
        if (id == null) {
            return false;
        }

        if (this.worldInfo.getEntity(id) instanceof Area area) {
            if (!area.isBlockadesDefined()) {
                return true;
            }
            Collection<Blockade> blockades = area.getBlockades()
                    .stream()
                    .map(this.worldInfo::getEntity)
                    .map(Blockade.class::cast)
                    .collect(toList());

            Geometry passable = GeometryUtils.getPassableGeometryInArea(area, blockades);

            List<Line2D> edges = area.getEdges()
                    .stream()
                    .filter(Edge::isPassable)
                    .map(Edge::getLine)
                    .toList();

            int n = edges.size();
            for (int i = 0; i < n; ++i) {
                for (int j = i + 1; j < n; ++j) {
                    Line2D l1 = edges.get(i);
                    Line2D l2 = edges.get(j);
                    if (l1.getDirection().getLength() < agentRadius * 2) {
                        continue;
                    }
                    if (l2.getDirection().getLength() < agentRadius * 2) {
                        continue;
                    }
                    if (!GeometryUtils.isPassable(passable, l1, l2)) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }


    /**
     * 将阻碍 FB 和 AT 在可视范围内的行动的障碍注册为行动目标候选（优先级为 2）
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void putRequestsWithPerception() {
        for (EntityID entityID : this.worldInfo.getChanged().getChangedEntities()) {
            if (!this.blockedHuman.calc().getData().contains(entityID)) {
                continue;
            }

            StandardEntity entity = this.worldInfo.getEntity(entityID);
            if (!(entity instanceof final Human human)) {
                continue;
            }
            if (entity.getStandardURN() == POLICE_FORCE) {
                continue;
            }
            if (entity.getStandardURN() == CIVILIAN) {
                continue;
            }

            EntityID position = human.getPosition();

            this.putTask(position, 2);
            this.completedTargets.remove(position);
        }
    }


    /**
     * 将可能阻碍视野内人类行动的障碍物注册为行动目标候选者（优先级为 2）
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void putNeighborBlockadesWithPerception() {
        Collection<Human> humans = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .filter(human -> !(this.worldInfo.getPosition(human) instanceof AmbulanceTeam))
                .filter(human -> !(human instanceof PoliceForce))
                .collect(toSet());

        for (Human human : humans) {
            if (this.worldInfo.getPosition(human) instanceof Area area) {
                area.getNeighbours()
                        .stream()
                        .map(this.worldInfo::getEntity)
                        .filter(Area.class::isInstance)
                        .map(Area.class::cast)
                        .filter(neighbourArea -> !(this.completedTargets.contains(neighbourArea.getID())))
                        .filter(Area::isBlockadesDefined)
                        .map(Area::getID)
                        .forEach(neighbor -> this.putTask(neighbor, 2));
            }
        }
    }


    /**
     * 整理agent收到的消息，用{@link #handleMessage(StandardMessage, int)}处理每条消息
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private void receiveMessage(@NotNull MessageManager messageManager) {
        // 警察不可能被困在障碍物里


        // 当不存在警察中心代理的情况下，接收其他被掩埋警察发出的请求清理信息
        if (scenarioInfo.getScenarioAgentsPo() == 0) {
            messageManager.getReceivedMessageList(MessagePoliceForce.class)
                    .stream()
                    .map(MessagePoliceForce.class::cast)
                    .filter(messagePoliceForce -> messagePoliceForce.getAction() == HELP_CLEAR)
                    .forEach(messagePoliceForce -> this.handleMessage(messagePoliceForce, 1));
        }

        // 收到消息:救护队被困在瓦砾中
        messageManager.getReceivedMessageList(MessageAmbulanceTeam.class)
                .stream()
                .map(MessageAmbulanceTeam.class::cast)
                .filter(messageAmbulanceTeam -> messageAmbulanceTeam.getAction() == HELP_CLEAR)
                .forEach(messageAmbulanceTeam -> this.handleMessage(messageAmbulanceTeam, 0));

        // 收到消息:消防队被困在瓦砾中
        messageManager.getReceivedMessageList(MessageFireBrigade.class)
                .stream()
                .map(MessageFireBrigade.class::cast)
                .filter(messageFireBrigade -> messageFireBrigade.getAction() == HELP_CLEAR)
                .forEach(messageFireBrigade -> this.handleMessage(messageFireBrigade, 1));

    }


    /**
     * 处理收到的消息
     *
     * @param message 消息
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private void handleMessage(StandardMessage message, int priority) {
        EntityID sender = null;
        EntityID targetPosition;
        int myAction = -1;
        boolean isTargetDefined = false;

        if (message instanceof MessageFireBrigade messageFireBrigade) {
            sender = messageFireBrigade.getSenderID();
            targetPosition = messageFireBrigade.getTargetID();
            myAction = messageFireBrigade.getAction();
            isTargetDefined = messageFireBrigade.isTargetDefined();
        } else if (message instanceof MessageAmbulanceTeam messageAmbulanceTeam) {
            sender = messageAmbulanceTeam.getSenderID();
            targetPosition = messageAmbulanceTeam.getTargetID();
            myAction = messageAmbulanceTeam.getAction();
            isTargetDefined = messageAmbulanceTeam.isTargetDefined();
        } else if (message instanceof MessagePoliceForce messagePoliceForce) {
            sender = messagePoliceForce.getSenderID();
            targetPosition = messagePoliceForce.getTargetID();
            myAction = messagePoliceForce.getAction();
            isTargetDefined = messagePoliceForce.isTargetDefined();
        } else {
            targetPosition = null;
        }


        if (myAction != HELP_CLEAR) {
            return;
        }
        if (!isTargetDefined) {
            return;
        }

        // 如果当前警察agent与消息发送者的距离小于语音频道的范围
        if (this.worldInfo.getDistance(this.agentInfo.getID(), sender) <= this.scenarioInfo.getRawConfig().getIntValue("comms.channels.0.range")) {
            this.putTask(targetPosition, 0);
            this.completedTargets.remove(targetPosition);
        }

        // 当不存在警察中心代理的情况下
        if (this.scenarioInfo.getScenarioAgentsPo() == 0) {
            // 判断自己是否是离目标最近的警察
            EntityID entityID = this.worldInfo.getEntitiesOfType(POLICE_FORCE)
                    .stream()
                    .filter(PoliceForce.class::isInstance)
                    .map(StandardEntity::getID)
                    .sorted(Comparator.comparing(id -> this.worldInfo.getDistance(targetPosition, id)))
                    .findFirst()
                    .get();
            if (entityID != null && entityID.equals(this.agentInfo.getID())) {
                // 将目标添加入职责范围
                this.responsibleZoneEntityIDs.add(targetPosition);
            }
        }

        // 如果目标位置不在自身的职责范围，不处理
        if (!this.responsibleZoneEntityIDs.contains(targetPosition)) return;

        this.putTask(targetPosition, priority);
        this.completedTargets.remove(targetPosition);
    }


    /**
     * 发送消息
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private void sendMessage(MessageManager messageManager) {
        // 警察不可能被困在障碍物里

        // 如果自己被掩埋时发送消息寻求帮助
        PoliceForce me = (PoliceForce) this.agentInfo.me();
        if (this.agentInfo.getPositionArea() instanceof Building && me.isBuriednessDefined() && me.getBuriedness() > 0) {
            // 获取当前所在的建筑物旁的有障碍的道路
            Set<Road> roads = this.agentInfo.getPositionArea()
                    .getNeighbours()
                    .stream()
                    .map(this.worldInfo::getEntity)
                    .filter(Road.class::isInstance)
                    .map(Road.class::cast)
                    .filter(road -> road.isBlockadesDefined() && road.getBlockades() != null && !road.getBlockades().isEmpty())
                    .collect(toSet());
            // 判断是否能够呼叫消防队来救援
            if (roads.size() == 0) {
                if (isCalledFire) {
                    return;
                }
                this.isCalledFire = true;
                messageManager.addMessage(new MessagePoliceForce(true, HIGH, me, HELP_RESCUE, this.agentInfo.getID()));
                messageManager.addMessage(new MessagePoliceForce(false, HIGH, me, HELP_RESCUE, this.agentInfo.getID()));
            } else {
                // 道路有障碍，先呼叫警察进行清理
                for (Road road : roads) {
                    messageManager.addMessage(new MessagePoliceForce(true, HIGH, me, HELP_CLEAR, road.getID()));
                    messageManager.addMessage(new MessagePoliceForce(false, HIGH, me, HELP_CLEAR, road.getID()));
                }
            }
            return;
        }

        // 自由移动时，感知范围内的所有平民
        Set<Civilian> civilians = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .collect(toSet());
        for (Civilian civilian : civilians) {
            // 如果平民被掩埋，则通知消防员救援
            if (this.worldInfo.getEntity(civilian.getPosition()) instanceof Building
                    && civilian.isBuriednessDefined() && civilian.getBuriedness() > 0) {
                messageManager.addMessage(new MessageCivilian(true, NORMAL, civilian));
                messageManager.addMessage(new MessageCivilian(false, NORMAL, civilian));
            }
        }
    }
}
