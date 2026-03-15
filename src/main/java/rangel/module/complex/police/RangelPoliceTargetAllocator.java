package rangel.module.complex.police;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.centralized.MessageReport;
import adf.core.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.core.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.core.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.complex.PoliceTargetAllocator;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

import static rangel.module.communication.RangelMessage.HELP_CLEAR;

/**
 * 警察的目标分配器
 * <p>
 * 调用流程: {@link #calc()} -> {@link #getResult()}
 *
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelPoliceTargetAllocator extends PoliceTargetAllocator {

    /**
     * 目标区域
     */
    private Set<EntityID> targetArea;
    /**
     * 优先目标区域
     */
    private Set<EntityID> priorityArea;
    /**
     * 当前中心代理的索引
     */
    private int index;
    /**
     * 结果集 <br>
     * key: 警察id <br>
     * value: 目标id
     */
    private Map<EntityID, EntityID> result;
    /**
     * 已发送的
     */
    private Map<EntityID, Integer> alreadySend;
    /**
     * 无法移动的警察
     */
    private Set<EntityID> cannotMovePolices;

    /**
     * {@link RangelPoliceTargetAllocator}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param ScenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="https://roozen.top">Roozen</a>
     */
    public RangelPoliceTargetAllocator(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.targetArea = new HashSet<>();
        this.priorityArea = new HashSet<>();
        this.result = new HashMap<>();
        this.alreadySend = new HashMap<>();
        this.cannotMovePolices = new HashSet<>();
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
    public PoliceTargetAllocator resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }

        // 将警察的中心代理按照id排序，得到当前中心代理的索引值
        this.index = this.worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_OFFICE)
                .stream()
                .filter(PoliceOffice.class::isInstance)
                .map((e) -> e.getID().getValue())
                .sorted()
                .collect(Collectors.toList())
                .indexOf(this.agentInfo.getID().getValue());

        // 获取所有的避难所
        List<Refuge> refuges = this.worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE)
                .stream()
                .filter(Refuge.class::isInstance)
                .map(Refuge.class::cast)
                .collect(Collectors.toList());
        // 获取警察中心代理的数量
        int scenarioAgentsPo = scenarioInfo.getScenarioAgentsPo();

        // 根据当前中心代理的索引值分配目标
        for (int i = index; i < refuges.size(); i += scenarioAgentsPo) {
            refuges.get(i)
                    .getNeighbours()
                    .stream()
                    .map((id) -> this.worldInfo.getEntity(id))
                    .filter(Road.class::isInstance)
                    .forEach(e -> this.priorityArea.add(e.getID()));
        }
        return this;
    }

    /**
     * 获取结果集
     *
     * @return 结果集
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public Map<EntityID, EntityID> getResult() {
        return this.result;
    }

    /**
     * 计算结果集
     *
     * @return this
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public PoliceTargetAllocator calc() {
        if (!this.priorityArea.isEmpty()) {
            EntityID target = this.priorityArea.stream().findFirst().get();
            this.priorityArea.remove(target);
            EntityID closestPolice = getClosestPolice(target);
            this.result.put(closestPolice, target);
            this.alreadySend.put(closestPolice, 0);
        }
        return this;
    }

    /**
     * 获取距离目标最近的警察（欧氏距离）
     *
     * @param target 目标
     * @return 距离目标最近的警察
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private EntityID getClosestPolice(EntityID target) {
        return this.worldInfo.getEntitiesOfType(StandardEntityURN.POLICE_FORCE)
                .stream()
                .filter(PoliceForce.class::isInstance)
                .map(StandardEntity::getID)
                .sorted(Comparator.comparing(id -> this.worldInfo.getDistance(target, id)))
                .limit(5)
                .filter((id) -> !this.result.containsKey(id) && !this.cannotMovePolices.contains(id))
                .findFirst()
                .get();
    }

    /**
     * 每个回合都会执行这个方法来更新agent所持有的信息
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public PoliceTargetAllocator updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        Set<EntityID> removeKeys = new HashSet<>();

        // 当发送的消息次数大于1时，不再发送该消息
        for (Map.Entry<EntityID, Integer> alreadySendEntry : this.alreadySend.entrySet()) {
            EntityID key = alreadySendEntry.getKey();
            Integer value = alreadySendEntry.getValue();
            if (value >= 2) {
                removeKeys.add(key);
                continue;
            }
            this.alreadySend.put(key, value + 1);
        }

        // 接受来自警察的报告消息
        messageManager.getReceivedMessageList(MessageReport.class)
                .stream()
                .map(MessageReport.class::cast)
                .filter(messageReport -> messageReport.isDone())
                .forEach(messageReport -> {
                    removeKeys.add(messageReport.getSenderID());
                });

        // 剔除不再发送的消息
        for (EntityID removeKey : removeKeys) {
            this.result.remove(removeKey);
            this.alreadySend.remove(removeKey);
        }

        // 获取来自警察的请求清理消息
        messageManager.getReceivedMessageList(MessagePoliceForce.class)
                .stream()
                .map(MessagePoliceForce.class::cast)
                .filter(messagePoliceForce -> messagePoliceForce.getAction() == HELP_CLEAR)
                .filter(messagePoliceForce -> !this.cannotMovePolices.contains(messagePoliceForce.getAgentID()))
                .forEach(messagePoliceForce -> {
                    this.priorityArea.add(messagePoliceForce.getTargetID());
                    this.cannotMovePolices.add(messagePoliceForce.getAgentID());
                });
        // 获取来自救护队的请求清理消息
        messageManager.getReceivedMessageList(MessageAmbulanceTeam.class)
                .stream()
                .map(MessageAmbulanceTeam.class::cast)
                .filter(messageAmbulanceTeam -> messageAmbulanceTeam.getAction() == HELP_CLEAR)
                .filter(messageAmbulanceTeam -> !this.cannotMovePolices.contains(messageAmbulanceTeam.getAgentID()))
                .forEach(messageAmbulanceTeam -> {
                    this.priorityArea.add(messageAmbulanceTeam.getTargetID());
                    this.cannotMovePolices.add(messageAmbulanceTeam.getAgentID());
                });
        // 获取来自消防队的请求清理消息
        messageManager.getReceivedMessageList(MessageFireBrigade.class)
                .stream()
                .map(MessageFireBrigade.class::cast)
                .filter(messageFireBrigade -> messageFireBrigade.getAction() == HELP_CLEAR)
                .filter(messageFireBrigade -> !this.cannotMovePolices.contains(messageFireBrigade.getAgentID()))
                .forEach(messageFireBrigade -> {
                    this.priorityArea.add(messageFireBrigade.getTargetID());
                    this.cannotMovePolices.add(messageFireBrigade.getAgentID());
                });
        return this;
    }

}