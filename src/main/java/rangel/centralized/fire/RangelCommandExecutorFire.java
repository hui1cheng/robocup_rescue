package rangel.centralized.fire;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.centralized.CommandFire;
import adf.core.agent.communication.standard.bundle.centralized.MessageReport;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.centralized.CommandExecutor;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

/**
 * 消防队的命令执行器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelCommandExecutorFire extends CommandExecutor<CommandFire> {

    /**
     * 未知命令
     */
    private static final int ACTION_UNKNOWN = -1;

    /**
     * 动作:休息
     */
    private static final int ACTION_REST = CommandFire.ACTION_REST;

    /**
     * 动作:移动
     */
    private static final int ACTION_MOVE = CommandFire.ACTION_MOVE;

    /**
     * 动作:救援
     */
    private static final int ACTION_RESCUE = CommandFire.ACTION_RESCUE;

    /**
     * 动作:自主行动
     */
    private static final int ACTION_AUTONOMY = CommandFire.ACTION_AUTONOMY;

    /**
     * 路径规划算法
     */
    private final PathPlanning pathPlanning;

    /**
     * 扩展动作:救援
     */
    private final ExtAction actionExtRescue;

    /**
     * 扩展动作:移动
     */
    private final ExtAction actionExtMove;

    /**
     * 命令类型
     */
    private int commandType;

    /**
     * 目标的EntityID
     */
    private EntityID targetID;

    /**
     * 命令执行者的EntityID
     */
    private EntityID commanderID;


    /**
     * {@link RangelCommandExecutorFire}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelCommandExecutorFire(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.commandType = ACTION_UNKNOWN;

        this.pathPlanning = moduleManager.getModule("RangelCommandExecutorFire.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.actionExtRescue = moduleManager.getExtAction("RangelCommandExecutorFire.ExtActionRescue", "adf.impl.extaction.DefaultExtActionFireRescue");
        this.actionExtMove = moduleManager.getExtAction("RangelCommandExecutorFire.ExtActionMove", "adf.impl.extaction.DefaultExtActionMove");
    }


    /**
     * 预计算时执行的方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandFire> precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionExtRescue.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
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
    public CommandExecutor<CommandFire> resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionExtRescue.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandFire> preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionExtRescue.preparate();
        this.actionExtMove.preparate();
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
    public CommandExecutor<CommandFire> updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionExtRescue.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        if (this.isCommandCompleted()) {
            if (this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                this.commandType = ACTION_UNKNOWN;
                this.targetID = null;
                this.commanderID = null;
            }
        }
        return this;
    }


    /**
     * 设置命令
     *
     * @param command 消防队命令
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandFire> setCommand(@NotNull CommandFire command) {
        EntityID agentID = this.agentInfo.getID();
        if (command.isToIDDefined() && Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue()) {
            this.commandType = command.getAction();
            this.targetID = command.getTargetID();
            this.commanderID = command.getSenderID();
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
    public CommandExecutor<CommandFire> calc() {
        this.result = null;

        switch (this.commandType) {

            case ACTION_REST -> {
                EntityID positionID = this.agentInfo.getPosition();
                if (this.targetID == null) {
                    Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(REFUGE);
                    if (refuges.contains(positionID)) {
                        this.result = new ActionRest();
                    } else {
                        List<EntityID> path = this.pathPlanning
                                .setFrom(positionID)
                                .setDestination(refuges)
                                .calc()
                                .getResult();
                        if (path != null && path.size() > 0) {
                            this.result = new ActionMove(path);
                        } else {
                            this.result = new ActionRest();
                        }
                    }
                    return this;
                }
                if (!positionID.equals(this.targetID)) {
                    List<EntityID> path = this.pathPlanning.getResult(positionID, this.targetID);
                    if (path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                        return this;
                    }
                }
                this.result = new ActionRest();
                return this;
            }

            case ACTION_MOVE -> {
                if (this.targetID != null) {
                    this.result = this.actionExtMove
                            .setTarget(this.targetID)
                            .calc()
                            .getAction();
                }
                return this;
            }

            case ACTION_RESCUE -> {
                if (this.targetID != null) {
                    this.result = this.actionExtRescue
                            .setTarget(this.targetID)
                            .calc()
                            .getAction();
                }
                return this;
            }

            case ACTION_AUTONOMY -> {
                if (this.targetID == null) {
                    return this;
                }
                StandardEntity targetEntity = this.worldInfo.getEntity(this.targetID);
                if (targetEntity instanceof Area) {
                    this.result = this.actionExtMove
                            .setTarget(this.targetID)
                            .calc()
                            .getAction();
                } else if (targetEntity instanceof Human) {
                    this.result = this.actionExtRescue
                            .setTarget(this.targetID)
                            .calc()
                            .getAction();
                }
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
        Human agent = (Human) this.agentInfo.me();
        EntityID positionID = this.agentInfo.getPosition();
        StandardEntity targetEntity = this.worldInfo.getEntity(this.targetID);

        switch (this.commandType) {

            case ACTION_REST -> {
                if (this.targetID == null) {
                    return agent.getDamage() == 0;
                }
                if (targetEntity instanceof Refuge) {
                    if (positionID.equals(this.targetID)) {
                        return agent.getDamage() == 0;
                    }
                }
                return false;
            }

            case ACTION_MOVE -> {
                return this.targetID == null || positionID.equals(this.targetID);
            }

            case ACTION_RESCUE -> {
                if (this.targetID == null) {
                    return true;
                }
                if (targetEntity instanceof Human human) {
                    return human.isBuriednessDefined() && human.getBuriedness() == 0 || (human.isHPDefined() && human.getHP() == 0);
                }
            }

            case ACTION_AUTONOMY -> {
                if (this.targetID != null) {
                    if (targetEntity instanceof Area) {
                        this.commandType = ACTION_MOVE;
                        return this.isCommandCompleted();
                    } else if (targetEntity instanceof Human human) {
                        if ((human.isHPDefined() && human.getHP() == 0)) {
                            return true;
                        }
                        if (human instanceof Civilian) {
                            this.commandType = ACTION_RESCUE;
                        }
                        return this.isCommandCompleted();
                    }
                }
                return true;
            }

        }
        return true;
    }
}