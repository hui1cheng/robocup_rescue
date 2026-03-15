package rangel.module.complex.fire;


import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionRescue;
import adf.core.agent.communication.MessageManager;
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
import adf.core.component.module.complex.HumanDetector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import rangel.module.algorithm.data.DataModule;
import rangel.utils.HumanUtils;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.HIGH;
import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.NORMAL;
import static rangel.module.communication.RangelMessage.HELP_CLEAR;
import static rangel.module.communication.RangelMessage.HELP_RESCUE;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.FIRE_BRIGADE;

/**
 * 消防队的人类检测器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelFireHumanDetector extends HumanDetector {

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
     * 自身的职责范围内实体的EntityID的集合
     */
    private final Set<EntityID> responsibleZoneEntityIDs;

    /**
     * 接收到的需要救援的平民的EntityID的集合
     */
    private final Set<EntityID> receivedBuriedCivilianIDs;

    /**
     * 不需要自身救援的平民的EntityID的集合
     */
    private final Set<EntityID> ignoredBuriedCivilianIDs;

    /**
     * 自身感知到的需要救援的平民 <br>
     * key: 需要救援的平民的位置的EntityID <br>
     * value: 该位置需要救援的人类的EntityID的集合
     */
    private final Map<EntityID, Set<EntityID>> perceivedBuriedCivilianPositionMap;

    /**
     * 自身感知到的需要救援的agent的EntityID的集合
     */
    private final Set<EntityID> perceivedBuriedAgentIDs;

    /**
     * 消防队的位置 <br>
     * key: 消防队的位置的EntityID <br>
     * value: 该位置的消防队的EntityID的集合
     */
    private final Map<EntityID, Set<EntityID>> fireBrigadePositionMap;

    /**
     * 前几个回合周围消防员的数量
     */
    private final LinkedList<Integer> surroundingFireBrigadeNumbers;

    /**
     * 探测结果的EntityID
     */
    private EntityID result = null;

    /**
     * 自身被障碍物挡住时的等待时间步长
     */
    private int waitingTimes = 0;


    /**
     * {@link RangelFireHumanDetector}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelFireHumanDetector(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.responsibleZoneEntityIDs = new HashSet<>();
        this.receivedBuriedCivilianIDs = new HashSet<>();
        this.ignoredBuriedCivilianIDs = new HashSet<>();
        this.perceivedBuriedCivilianPositionMap = new HashMap<>();
        this.perceivedBuriedAgentIDs = new HashSet<>();
        this.fireBrigadePositionMap = new HashMap<>();
        this.surroundingFireBrigadeNumbers = new LinkedList<>();

        this.clustering = moduleManager.getModule("RangelFireHumanDetector.Clustering", "adf.impl.module.algorithm.KMeansClustering");
        this.immovableHuman = moduleManager.getModule("RangelFireHumanDetector.ImmovableHuman");
        this.blockedHuman = moduleManager.getModule("RangelFireHumanDetector.BlockedHuman");

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
    public HumanDetector precompute(PrecomputeData precomputeData) {
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
    public HumanDetector resume(PrecomputeData precomputeData) {
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
    public HumanDetector preparate() {
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
    public HumanDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        if (this.responsibleZoneEntityIDs.isEmpty()) {
            this.updateResponsibleZoneEntityIDs();
        }

        this.receiveMessage(messageManager);
        this.sendMessage(messageManager);

        this.updateReceivedBuriedCivilianIDs();
        this.updatePerceivedBuriedCivilianPositionMap();
        this.updatePerceivedBuriedAgentIDs();
        this.updateFireBrigadePositionMap();
        this.updateSurroundingFireBrigadeNumbers();
        return this;
    }


    /**
     * 计算探测结果
     * <p>
     * <ol>
     *     <li>先从感知到的需要救援的agent中选择一个作为目标
     *     <li>如果1得到的结果为空,则从感知到的需要救援的平民的位置中选择一个作为目标
     *     <li>如果2得到的结果为空,则从接收到的需要救援的平民中选择一个作为目标
     * </ol>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public HumanDetector calc() {
        // 如果消防员在一个地方等待超过两回合，则放弃等待，选择其他目标，警察清理障碍后会重新通知消防员
        if (this.waitingTimes >= 2 && this.result != null) {
            this.waitingTimes = 0;
            this.perceivedBuriedAgentIDs.remove(this.result);
            this.perceivedBuriedCivilianPositionMap.remove(this.worldInfo.getPosition(this.result));
            this.receivedBuriedCivilianIDs.remove(this.result);
            this.ignoredBuriedCivilianIDs.add(this.result);
        }
        //先从感知到的需要救援的agent中选择一个作为目标
        this.result = this.selectTargetFromPerceivedAgent();
        if (this.result != null) {
            return this;
        }

        //如果上一步得到的结果为空,则从感知到的需要救援的平民的位置中选择一个作为目标
        this.result = this.selectTargetFromPerceivedCivilian();
        if (this.result != null) {
            return this;
        }

        //如果上一步得到的结果为空,则从接收到的需要救援的平民中选择一个作为目标
        this.result = this.selectTargetFromReceivedCivilian();
        return this;
    }


    /**
     * 获得当前的探测结果
     *
     * @return 当前的探测结果的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public EntityID getTarget() {
        return this.result;
    }


    /**
     * 更新自身的责任区({@link #responsibleZoneEntityIDs})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateResponsibleZoneEntityIDs() {
        this.clustering.calc();
        int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        Collection<EntityID> ids = this.clustering.getClusterEntityIDs(clusterIndex);
        this.responsibleZoneEntityIDs.addAll(ids);
    }


    /**
     * 更新接收到的需要救援的平民的EntityID集合({@link #receivedBuriedCivilianIDs})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateReceivedBuriedCivilianIDs() {
        this.receivedBuriedCivilianIDs
                .stream()
                .filter(entityID -> !this.needRescue(entityID))
                .forEach(this.ignoredBuriedCivilianIDs::add);
        this.receivedBuriedCivilianIDs.removeAll(this.ignoredBuriedCivilianIDs);
    }


    /**
     * 更新感知到的需要救援的平民的位置信息({@link #perceivedBuriedCivilianPositionMap})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updatePerceivedBuriedCivilianPositionMap() {
        this.perceivedBuriedCivilianPositionMap.clear();
        this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .filter(civilian -> !(this.worldInfo.getPosition(civilian) instanceof Refuge))
                .forEach(civilian -> {
                    EntityID civilianID = civilian.getID();
                    EntityID positionID = civilian.getPosition();
                    if (this.needRescue(civilianID)) {
                        this.perceivedBuriedCivilianPositionMap.computeIfAbsent(positionID, k -> new HashSet<>()).add(civilianID);
                    }
                });
    }


    /**
     * 更新感知到的需要救援的agent的EntityID集合({@link #perceivedBuriedAgentIDs})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updatePerceivedBuriedAgentIDs() {
        this.perceivedBuriedAgentIDs.clear();
        this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .filter(human -> human.getStandardURN() != CIVILIAN)
                .filter(human -> !(this.worldInfo.getPosition(human.getID()) instanceof Refuge))
                .filter(this::needRescue)
                .map(Human::getID)
                .forEach(this.perceivedBuriedAgentIDs::add);
    }


    /**
     * 更新感知到的消防队员的位置信息({@link #fireBrigadePositionMap})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateFireBrigadePositionMap() {
        this.fireBrigadePositionMap.clear();
        this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(FireBrigade.class::isInstance)
                .map(FireBrigade.class::cast)
                .filter(fireBrigade -> !(this.worldInfo.getPosition(fireBrigade.getID()) instanceof Refuge))
                .filter(fireBrigade -> !this.needRescue(fireBrigade))
                .forEach(fireBrigade -> {
                    EntityID fireBrigadeID = fireBrigade.getID();
                    EntityID positionID = fireBrigade.getPosition();
                    this.fireBrigadePositionMap.computeIfAbsent(positionID, k -> new HashSet<>()).add(fireBrigadeID);
                });
    }


    /**
     * 更新周围的消防队员数量({@link #surroundingFireBrigadeNumbers})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateSurroundingFireBrigadeNumbers() {
        // 获取并记录感知范围内的消防队数量
        int totalFireBrigadeNumber = fireBrigadePositionMap.values()
                .stream()
                .mapToInt(Collection::size)
                .sum();
        this.surroundingFireBrigadeNumbers.addLast(totalFireBrigadeNumber);
        if (surroundingFireBrigadeNumbers.size() > 10) {
            surroundingFireBrigadeNumbers.removeFirst();
        }
    }


    /**
     * 接收消息
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void receiveMessage(@NotNull MessageManager messageManager) {
        messageManager.getReceivedMessageList(MessageFireBrigade.class)
                .stream()
                .map(MessageFireBrigade.class::cast)
                .filter(messageFireBrigade -> messageFireBrigade.getAction() == HELP_RESCUE)
                .map(MessageFireBrigade::getTargetID)
                .filter(this::needRescue)
                .filter(this::isClosest)
                .forEach(receivedBuriedCivilianIDs::add);
        messageManager.getReceivedMessageList(MessagePoliceForce.class)
                .stream()
                .map(MessagePoliceForce.class::cast)
                .filter(messagePoliceForce -> messagePoliceForce.getAction() == HELP_RESCUE)
                .map(MessagePoliceForce::getTargetID)
                .filter(this::needRescue)
                .filter(this::isClosest)
                .forEach(receivedBuriedCivilianIDs::add);
        messageManager.getReceivedMessageList(MessageAmbulanceTeam.class)
                .stream()
                .map(MessageAmbulanceTeam.class::cast)
                .filter(messageAmbulanceTeam -> messageAmbulanceTeam.getAction() == HELP_RESCUE)
                .map(MessageAmbulanceTeam::getTargetID)
                .filter(this::needRescue)
                .filter(this::isClosest)
                .forEach(receivedBuriedCivilianIDs::add);
        messageManager.getReceivedMessageList(MessageCivilian.class)
                .stream()
                .map(MessageCivilian.class::cast)
                .map(MessageCivilian::getAgentID)
                .filter(this::needRescue)
                .forEach(receivedBuriedCivilianIDs::add);
    }

    private boolean isClosest(EntityID target) {
        EntityID entityID = this.worldInfo.getEntitiesOfType(FIRE_BRIGADE)
                .stream()
                .filter(FireBrigade.class::isInstance)
                .map(StandardEntity::getID)
                .sorted(Comparator.comparing(id -> this.worldInfo.getDistance(target, id)))
                .findFirst()
                .get();
        if (entityID != null && entityID.equals(this.agentInfo.getID())) {
            return true;
        }
        return false;
    }


    /**
     * 发送消息
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private void sendMessage(MessageManager messageManager) {
        //感知范围内的障碍
        Set<Blockade> blockades = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(e -> e instanceof Blockade)
                .map(Blockade.class::cast)
                .collect(Collectors.toSet());

        FireBrigade me = (FireBrigade) this.agentInfo.me();
        EntityID positionID = this.agentInfo.getPosition();

        // 如果自己被困在障碍里
        if (this.isBlocked(blockades) || (this.isUnreachable() && !this.isBuried())) {
            this.waitingTimes++;
            messageManager.addMessage(new MessageFireBrigade(true, HIGH, me, HELP_CLEAR, positionID));
            messageManager.addMessage(new MessageFireBrigade(false, HIGH, me, HELP_CLEAR, positionID));
            return;
        }

        //如果自己被埋藏
        if (this.isBuried()) {
            messageManager.addMessage(new MessageFireBrigade(true, HIGH, me, HELP_RESCUE, this.agentInfo.getID()));
            messageManager.addMessage(new MessageFireBrigade(false, HIGH, me, HELP_RESCUE, this.agentInfo.getID()));
            return;
        }

        if (this.agentInfo.getTime() >= 2) {
            //上一回合执行的动作
            Action lastAction = this.agentInfo.getExecutedAction(this.agentInfo.getTime() - 1);

            // 如果执行的动作是救援
            if (lastAction instanceof ActionRescue) {

                StandardEntity entity = this.worldInfo.getEntity(this.result);
                if (entity instanceof Civilian civilian) {
                    if (civilian.isHPDefined() && civilian.getDamage() > 0) {
                        messageManager.addMessage(new MessageCivilian(true, NORMAL, civilian));
                        messageManager.addMessage(new MessageCivilian(false, NORMAL, civilian));
                    }
                }
            }
        }
    }


    /**
     * 从接收到的需要救援的平民({@link #receivedBuriedCivilianIDs})中选择一个作为目标
     *
     * @return 需要救援的平民的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @Nullable EntityID selectTargetFromReceivedCivilian() {
        return this.receivedBuriedCivilianIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .min(Comparator
                        .comparingInt(this::getPriority)
                        .thenComparingDouble(human -> {
                            EntityID entityID = human.getID();
                            Pair<Integer, Integer> location = this.worldInfo.getLocation(entityID);
                            Objects.requireNonNull(location);
                            Pair<Integer, Integer> center = this.getResponsibleZoneCenter();
                            int outOfArea = (this.responsibleZoneEntityIDs.contains(entityID) ? 0 : Math.abs(center.first() - location.first()) + Math.abs(center.second() - location.second()));
                            double cost1 = Math.sqrt(Math.abs(this.agentInfo.getX() - location.first()) + Math.abs(this.agentInfo.getY() - location.second()));
                            double cost2 = this.getEstimatedSurvivalTime(human) - (human.getDamage() * human.getBuriedness());
                            return cost1 + cost2 + outOfArea;
                        })
                )
                .map(Human::getID)
                .orElse(null);
    }


    /**
     * 从感知到的需要救援的Agent({@link #perceivedBuriedAgentIDs})中选择一个作为目标
     *
     * @return 需要救援的Agent的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @Nullable EntityID selectTargetFromPerceivedAgent() {
        return this.perceivedBuriedAgentIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .min(Comparator
                        .comparing(this::getPriority)
                        .thenComparing(Human::getBuriedness)
                )
                .map(Human::getID)
                .orElse(null);
    }


    /**
     * 从感知到的需要救援的平民({@link #perceivedBuriedCivilianPositionMap})中选择一个作为目标
     *
     * @return 需要救援的平民的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @Nullable EntityID selectTargetFromPerceivedCivilian() {
        EntityID agentPositionID = this.agentInfo.getPosition();

        //先从自身所在的位置寻找需要救援的平民
        Collection<EntityID> buriedCivilianIDs = perceivedBuriedCivilianPositionMap.get(agentPositionID);
        Set<EntityID> fireBrigadeIDs = this.fireBrigadePositionMap.get(agentPositionID);
        if (buriedCivilianIDs != null) {
            buriedCivilianIDs = buriedCivilianIDs
                    .stream()
                    .sorted(Comparator.comparing(EntityID::getValue))
                    .toList();
            int count = 0;
            for (EntityID buriedCivilianID : buriedCivilianIDs) {
                if (this.needJoinRescue(fireBrigadeIDs, count, buriedCivilianID)) {
                    return buriedCivilianID;
                } else {
                    this.ignoredBuriedCivilianIDs.add(buriedCivilianID);
                }
                count++;
            }
            perceivedBuriedCivilianPositionMap.remove(agentPositionID);
        }

        //再从自身以外的位置寻找需要救援的平民
        for (Map.Entry<EntityID, Set<EntityID>> buriedCivilianEntry : perceivedBuriedCivilianPositionMap.entrySet()) {
            buriedCivilianIDs = buriedCivilianEntry.getValue();
            if (buriedCivilianIDs != null) {
                buriedCivilianIDs = buriedCivilianIDs
                        .stream()
                        .sorted(Comparator.comparing(EntityID::getValue))
                        .toList();
                fireBrigadeIDs = this.fireBrigadePositionMap.get(buriedCivilianEntry.getKey());
                int fireBrigadeNumber = Math.max((fireBrigadeIDs == null) ? 1 : fireBrigadeIDs.size() + 1, Collections.max(this.surroundingFireBrigadeNumbers));
                int count = 0;
                for (EntityID buriedCivilianID : buriedCivilianIDs) {
                    if (this.canRescue(buriedCivilianID, fireBrigadeNumber)) {
                        if (this.needJoinRescue(fireBrigadeIDs, count, null)) {
                            return buriedCivilianID;
                        } else {
                            this.ignoredBuriedCivilianIDs.add(buriedCivilianID);
                        }
                        count++;
                    }
                }
            }
        }

        return null;
    }


    /**
     * 判断自身是否被障碍阻挡
     *
     * @param blockades 障碍的集合
     * @return true:被障碍阻挡 || false:未被障碍阻挡
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isBlocked(@NotNull Set<Blockade> blockades) {
        for (Blockade blockade : blockades) {
            if (blockade == null) {
                continue;
            }
            int[] apexes = blockade.getApexes();
            Coordinate[] cd = new Coordinate[apexes.length / 2 + 1];
            for (int i = 0; i < apexes.length / 2; i++) {
                cd[i] = new Coordinate(apexes[i * 2], apexes[i * 2 + 1]);
            }
            cd[apexes.length / 2] = cd[0];
            GeometryFactory gf = new GeometryFactory();
            Geometry gm = gf.createPolygon(cd);
            Coordinate a = new Coordinate(this.agentInfo.getX(), this.agentInfo.getY());
            Point p = gf.createPoint(a);
            if (gm.contains(p)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 判断自己是否被埋藏
     *
     * @return true:被埋藏 || false:未被埋藏
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isBuried() {
        Human me = (Human) this.agentInfo.me();
        return this.agentInfo.getPositionArea() instanceof Building && me.isBuriednessDefined() && me.getBuriedness() > 0;
    }


    /**
     * 判断自身是否不能移动
     *
     * @return true:不能移动 || false:能移动
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isUnreachable() {
        if (this.result == null) {
            return false;
        }
        EntityID agentID = this.agentInfo.getID();
        return this.immovableHuman.calc().getBoolean(agentID) || this.blockedHuman.calc().getBoolean(agentID);
    }


    /**
     * 判断参数指定的对象是否需要救援({@linkplain  adf.core.agent.action.fire.ActionRescue ActionRescue})
     *
     * @param entityID 对象的EntityID
     * @return true:可以救援 || false:不可以救援
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean needRescue(EntityID entityID) {
        if (this.worldInfo.getEntity(entityID) instanceof Human human) {
            if (human.isHPDefined() && human.isBuriednessDefined()) {
                return human.getHP() > 0 && human.getBuriedness() > 0;
            }
        }
        return false;
    }


    /**
     * 判断参数指定的对象是否需要救援({@linkplain  adf.core.agent.action.fire.ActionRescue ActionRescue})
     *
     * @param human 对象人类({@link Human})
     * @return true:可以救援 || false:不可以救援
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean needRescue(@NotNull Human human) {
        return this.needRescue(human.getID());
    }


    /**
     * 判断自身是否需要加入救援
     *
     * @param fireBrigades 目标位置的消防队员的EntityID集合
     * @param n            选定任务第 n 个
     * @param entityID     救助対象的EntityID
     * @return true:需要加入救援 || false:不需要加入救援
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean needJoinRescue(Set<EntityID> fireBrigades, int n, EntityID entityID) {
        if (fireBrigades == null) {
            return true;
        }
        fireBrigades.add(this.agentInfo.getID());
        int fireBrigadeRequiredNumber = ((entityID == null ? 3 : this.getFireBrigadeRequiredNumber(entityID)) + (int) (this.agentInfo.getTime() * 0.01)) * (n + 1);
        if (fireBrigadeRequiredNumber > 0) {
            return fireBrigades.stream()
                    .sorted(Comparator.comparing(EntityID::getValue))
                    .limit(fireBrigades.size())
                    .limit(fireBrigadeRequiredNumber)
                    .collect(Collectors.toSet())
                    .contains(this.agentInfo.getID());
        } else {
            return false;
        }
    }


    /**
     * 判断目标是否可以及时获救
     *
     * @param entityID          目标的EntityID
     * @param fireBrigadeNumber 消防队数量
     * @return true:能及时获救 || false:不能及时获救
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean canRescue(EntityID entityID, int fireBrigadeNumber) {
        StandardEntity entity = this.worldInfo.getEntity(entityID);
        if (entity instanceof Human human) {
            if (human.getBuriedness() > 0 && human.getHP() > 0) {
                return (this.getEstimatedSurvivalTime(human) - human.getBuriedness() / fireBrigadeNumber) > 0;
            }
        }
        return false;
    }


    /**
     * 获得目标人类的优先级,数字越小优先级越高
     *
     * @param human 目标人类({@link Human})
     * @return 优先级
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private int getPriority(@NotNull Human human) {
        int priority;
        StandardEntityURN standardURN = human.getStandardURN();
        switch (standardURN) {
            case FIRE_BRIGADE -> priority = 1;
            case POLICE_FORCE -> priority = 2;
            case AMBULANCE_TEAM -> priority = 3;
            default -> priority = Integer.MAX_VALUE;
        }
        return priority;
    }


    /**
     * 获得当前责任区的中心点
     *
     * @return 中心点的坐标对
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Contract(" -> new")
    private @NotNull Pair<Integer, Integer> getResponsibleZoneCenter() {
        //分别为x、y坐标的和值
        int[] total = new int[]{0, 0};
        this.responsibleZoneEntityIDs
                .stream()
                .map(this.worldInfo::getLocation)
                .filter(Objects::nonNull)
                .forEach(location -> {
                    total[0] += location.first();
                    total[1] += location.second();
                });
        int n = this.responsibleZoneEntityIDs.size();
        return new Pair<>(total[0] / n, total[1] / n);
    }


    /**
     * 获得救援参数指定的代理所需的最大消防队成员数
     *
     * @param entityID 目标人类的EntityID
     * @return 营救所需的最大消防队人数
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private int getFireBrigadeRequiredNumber(EntityID entityID) {
        if (this.worldInfo.getEntity(entityID) instanceof Human human) {
            int buried = human.getBuriedness();
            int time = this.getEstimatedSurvivalTime(human);
            int fireBrigadeNumber = 1;
            while (time - buried / fireBrigadeNumber <= 0) {
                ++fireBrigadeNumber;
            }
            return fireBrigadeNumber + 1;
        }
        return 3;
    }


    /**
     * 获得指定人类估计的生存时间
     *
     * @param human 人类
     * @return 估计的生存时间
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private int getEstimatedSurvivalTime(@NotNull Human human) {
        return HumanUtils.getEstimatedSurvivalTime(human);
    }

}
