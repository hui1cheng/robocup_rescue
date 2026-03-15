package rangel.centralized.fire;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.centralized.CommandScout;
import adf.core.agent.communication.standard.bundle.centralized.MessageReport;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.centralized.CommandExecutor;
import adf.core.component.module.algorithm.PathPlanning;
import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * 消防队的侦察动作的命令执行器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelCommandExecutorScoutFire extends CommandExecutor<CommandScout> {

    /**
     * 未知动作
     */
    private static final int ACTION_UNKNOWN = -1;

    /**
     * 动作:侦察
     */
    private static final int ACTION_SCOUT = 1;

    /**
     * 路径规划算法
     */
    private final PathPlanning pathPlanning;

    /**
     * 侦察目标的EntityID的集合
     */
    private final Collection<EntityID> scoutTargets;

    /**
     * 命令类型
     */
    private int commandType;

    /**
     * 命令执行者的EntityID
     */
    private EntityID commanderID;


    /**
     * {@link RangelCommandExecutorScoutFire}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelCommandExecutorScoutFire(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.commandType = ACTION_UNKNOWN;
        this.commanderID = null;

        this.scoutTargets = new HashSet<>();

        this.pathPlanning = moduleManager.getModule("RangelCommandExecutorScoutAmbulance.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");

    }


    /**
     * 预计算时执行的方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandScout> precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        return this;
    }


    /**
     * 预计算模式的初始化处理方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandScout> resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandScout> preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
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
    public CommandExecutor<CommandScout> updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);

        if (this.isCommandCompleted()) {
            if (this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                this.commandType = ACTION_UNKNOWN;
                this.scoutTargets.clear();
                this.commanderID = null;
            }
        }
        return this;
    }


    /**
     * 设置命令
     *
     * @param command 侦察命令
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandScout> setCommand(@NotNull CommandScout command) {
        EntityID agentID = this.agentInfo.getID();
        EntityID positionID = this.agentInfo.getPosition();
        EntityID commandToID = command.getToID();
        EntityID commandTargetID = command.getTargetID();
        EntityID commandSenderID = command.getSenderID();

        if (command.isToIDDefined() && agentID.equals(commandToID)) {
            EntityID targetID = commandTargetID;
            if (targetID == null) {
                targetID = positionID;
            }
            this.commandType = ACTION_SCOUT;
            this.commanderID = commandSenderID;
            this.scoutTargets.clear();
            this.scoutTargets.addAll(
                    this.worldInfo.getObjectsInRange(targetID, command.getRange())
                            .stream()
                            .filter(entity -> entity instanceof Area && !(entity instanceof Refuge))
                            .map(AbstractEntity::getID)
                            .toList()
            );
        }
        return this;
    }


    /**
     * 计算应该执行的动作,将结果保存在{@link #result}中
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandScout> calc() {
        this.result = null;
        if (this.commandType == ACTION_SCOUT) {
            if (this.scoutTargets.isEmpty()) {
                return this;
            }
            List<EntityID> path = this.pathPlanning
                    .setFrom(this.agentInfo.getPosition())
                    .setDestination(this.scoutTargets)
                    .calc()
                    .getResult();
            if (path != null) {
                this.result = new ActionMove(path);
            }
        }
        return this;
    }


    /**
     * 判断命令是否执行完毕
     *
     * @return true:执行完毕 || false:未执行完毕
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isCommandCompleted() {
        if (this.commandType == ACTION_SCOUT) {
            this.scoutTargets.removeAll(this.worldInfo.getChanged().getChangedEntities());
            return this.scoutTargets.isEmpty();
        }
        return true;
    }
}