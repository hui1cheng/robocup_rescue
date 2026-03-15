package rangel.module.complex.ambulance;


import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.core.agent.communication.standard.bundle.information.MessageCivilian;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.complex.HumanDetector;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.HIGH;
import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.NORMAL;
import static java.util.stream.Collectors.toSet;
import static rangel.module.communication.RangelMessage.HELP_CLEAR;
import static rangel.module.communication.RangelMessage.HELP_RESCUE;

/**
 * 救护队的人类探测器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelAmbulanceHumanDetector extends HumanDetector {

    /**
     * 确定每个代理的责任区域的聚类模块
     */
    private final Clustering clustering;

    /**
     * 自身的职责范围内实体的EntityID的集合
     */
    private final Set<EntityID> responsibleZoneEntityIDs;

    /**
     * 目标平民 <br>
     * key: 平民的EntityID <br>
     * value: 是否可以搬运
     */
    private final Map<EntityID, Boolean> targetCivilianMap;

    /**
     * 探测结果的EntityID
     */
    private EntityID result;


    public RangelAmbulanceHumanDetector(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.result = null;

        this.responsibleZoneEntityIDs = new HashSet<>();
        this.targetCivilianMap = new HashMap<>();

        this.clustering = moduleManager.getModule("RangelAmbulanceHumanDetector.Clustering", "adf.impl.module.algorithm.KMeansClustering");

        this.registerModule(this.clustering);
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
            this.clustering.calc();
            this.updateResponsibleZoneEntityIDs();
        }

        this.receiveMessage(messageManager);
        this.sendMessage(messageManager);

        this.updateTargetCivilianMap();
        return this;
    }


    /**
     * 计算探测结果
     * <p>
     * <ol>
     *     <li>如果担架上有人,则以担架上的人作为探测结果
     *     <li>从目标平民({@link #targetCivilianMap})中选择一个距离最近的平民作为探测结果
     *     <li>如果2得到的结果为null,则从感知范围内随机选择一个可搬运的平民作为探测结果
     * </ol>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public HumanDetector calc() {
        Human onboard = this.agentInfo.someoneOnBoard();
        if (onboard != null) {
            this.result = onboard.getID();
            return this;
        }

        //从目标平民中选择一个距离最近的平民作为探测结果
        this.result = this.targetCivilianMap.entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .filter(entityID -> !(this.worldInfo.getPosition(entityID) instanceof Refuge))
                .min(Comparator.comparingDouble(entityID -> this.worldInfo.getDistance(this.agentInfo.getID(), entityID)))
                .orElse(null);
        if (this.result != null) {
            return this;
        }

        this.result = this.responsibleZoneEntityIDs.stream()
                .filter(entityID -> this.worldInfo.getEntity(entityID) instanceof Civilian)
                .findAny()
                .orElse(null);

        //如果上一步得到的结果为空,则从感知范围内随机选择一个可搬运的平民作为探测结果
//        this.result = this.worldInfo.getChanged().getChangedEntities()
//                .stream()
//                .filter(entityID -> this.worldInfo.getEntity(entityID) instanceof Civilian)
//                .filter(entityID -> !(this.worldInfo.getPosition(entityID) instanceof Refuge))
//                .filter(this::canLoad)
//                .findAny()
//                .orElse(null);
        return this;
    }


    /**
     * 更新自身的责任区({@link #responsibleZoneEntityIDs})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateResponsibleZoneEntityIDs() {
        int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        Collection<EntityID> ids = this.clustering.getClusterEntityIDs(clusterIndex);
        this.responsibleZoneEntityIDs.addAll(ids);
    }


    /**
     * 发送消息
     * <p>
     * <ul>
     *     <li>当自身被障碍阻挡时,向附近的警察请求ACTION_CLEAR
     *     <li>当自身被埋藏时,向附近的消防队请求ACTION_RESCUE
     * </ul>
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private void sendMessage(MessageManager messageManager) {
        //感知范围内的所有障碍
        Set<Blockade> blockades = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(e -> e instanceof Blockade)
                .map(Blockade.class::cast)
                .collect(Collectors.toSet());

        AmbulanceTeam me = (AmbulanceTeam) this.agentInfo.me();
        // 如果自己被障碍阻挡
        if (this.isBlocked(blockades)) {
            boolean priority = this.agentInfo.someoneOnBoard() == null;
            messageManager.addMessage(new MessageAmbulanceTeam(true, priority ? NORMAL : HIGH, me, HELP_CLEAR, this.agentInfo.getPosition()));
            messageManager.addMessage(new MessageAmbulanceTeam(false, priority ? NORMAL : HIGH, me, HELP_CLEAR, this.agentInfo.getPosition()));
        }
        //如果自己被埋藏
        if (this.isBuried()) {
            messageManager.addMessage(new MessageAmbulanceTeam(true, HIGH, me, HELP_RESCUE, this.agentInfo.getID()));
            messageManager.addMessage(new MessageAmbulanceTeam(false, HIGH, me, HELP_RESCUE, this.agentInfo.getID()));
        }

        // 感知范围内的所有平民
        Set<Civilian> civilians = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .collect(toSet());
        for (Civilian civilian : civilians) {
            // 如果平民被埋藏，则通知消防员
            if (this.worldInfo.getEntity(civilian.getPosition()) instanceof Building
                    && civilian.isBuriednessDefined() && civilian.getBuriedness() > 0) {
                messageManager.addMessage(new MessageAmbulanceTeam(true, NORMAL, me, HELP_RESCUE, civilian.getID()));
                messageManager.addMessage(new MessageAmbulanceTeam(false, NORMAL, me, HELP_RESCUE, civilian.getID()));
            }
        }
    }


    /**
     * 接收消息
     * <p>
     * 仅接收来自平民的消息({@link MessageCivilian})
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void receiveMessage(@NotNull MessageManager messageManager) {
        messageManager.getReceivedMessageList(MessageCivilian.class)
                .stream()
                .map(MessageCivilian.class::cast)
                .forEach(messageCivilian -> {
                    EntityID agentID = messageCivilian.getAgentID();
                    this.targetCivilianMap.put(agentID, this.canLoad(agentID));
                });
    }


    /**
     * 更新目标平民({@link #targetCivilianMap})
     * <p>
     * <ol>
     *     <li>从目标平民({@link #targetCivilianMap})中移除已经被救助的平民
     *     <li>更新目标平民({@link #targetCivilianMap})中的平民的可搬运状态({@link #targetCivilianMap}的value)
     * </ol>
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateTargetCivilianMap() {
        //感知范围内的所有平民
        Set<EntityID> changedCivilianEntityIDs = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(AbstractEntity::getID)
                .collect(Collectors.toSet());

        //非目标平民
        Set<EntityID> nonTarget = this.targetCivilianMap.entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .map(this.worldInfo::getEntity)
                .filter(Civilian.class::isInstance)
                .map(Civilian.class::cast)
                .filter(civilian -> this.agentInfo.getPosition().equals(civilian.getPosition()) && !changedCivilianEntityIDs.contains(civilian.getID()))
                .map(Civilian::getID)
                .collect(Collectors.toSet());
        this.targetCivilianMap.keySet().removeAll(nonTarget);

        //更新目标平民的可搬运状态
        this.targetCivilianMap.entrySet().forEach(entry -> entry.setValue(this.canLoad(entry.getKey())));
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
     * 判断自身是否可以搬运({@linkplain  adf.core.agent.action.ambulance.ActionLoad ActionLoad})指定的平民
     * <p>
     * 有一下三个条件:
     * <ul>
     *     <li>对象的还活着,即hp>0
     *     <li>对象受伤了,即damage>=0
     *     <li>对象未被掩埋,即buriedness=0
     * </ul>
     *
     * @param entityID 平民的EntityID
     * @return true:可以搬运 || false:不可以搬运
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean canLoad(EntityID entityID) {
        if (this.worldInfo.getEntity(entityID) instanceof Civilian civilian) {
            if (civilian.isHPDefined() && civilian.isDamageDefined() && civilian.isBuriednessDefined()) {
                return civilian.getHP() > 0 && civilian.getDamage() >= 0 && civilian.getBuriedness() == 0;
            }
        }
        return false;
    }
}
