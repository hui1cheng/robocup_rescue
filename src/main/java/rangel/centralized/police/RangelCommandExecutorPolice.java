package rangel.centralized.police;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessagePriority;
import adf.core.agent.communication.standard.bundle.centralized.CommandPolice;
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

import java.util.List;
import java.util.Objects;

import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

/**
 * 警察的命令执行器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelCommandExecutorPolice extends CommandExecutor<CommandPolice> {

    /**
     * 未知动作
     */
    private static final int ACTION_UNKNOWN = -1;

    /**
     * 动作:休息
     */
    private static final int ACTION_REST = CommandPolice.ACTION_REST;

    /**
     * 动作:移动
     */
    private static final int ACTION_MOVE = CommandPolice.ACTION_MOVE;

    /**
     * 动作:清理
     */
    private static final int ACTION_CLEAR = CommandPolice.ACTION_CLEAR;

    /**
     * 动作:自主行动
     */
    private static final int ACTION_AUTONOMY = CommandPolice.ACTION_AUTONOMY;

    /**
     * 路径规划算法
     */
    private final PathPlanning pathPlanning;

    /**
     * 扩展动作:清理
     */
    private final ExtAction actionExtClear;

    /**
     * 扩展动作:移动
     */
    private final ExtAction actionExtMove;

    /**
     * 命令类型
     */
    private int commandType;

    /**
     * 命令目标的EntityID
     */
    private EntityID targetID;

    /**
     * 命令执行者的EntityID
     */
    private EntityID commanderID;


    /**
     * {@link RangelCommandExecutorPolice}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelCommandExecutorPolice(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.commandType = ACTION_UNKNOWN;

        this.pathPlanning = moduleManager.getModule("RangelCommandExecutorPolice.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.actionExtClear = moduleManager.getExtAction("RangelCommandExecutorPolice.ExtActionClear", "adf.impl.extaction.DefaultExtActionClear");
        this.actionExtMove = moduleManager.getExtAction("RangelCommandExecutorPolice.ExtActionMove", "adf.impl.extaction.DefaultExtActionMove");
    }


    /**
     * 预计算时执行的方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public CommandExecutor<CommandPolice> precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionExtClear.precompute(precomputeData);
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
    public CommandExecutor<CommandPolice> resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionExtClear.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public CommandExecutor<CommandPolice> preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionExtClear.preparate();
        this.actionExtMove.preparate();
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
    public CommandExecutor<CommandPolice> updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionExtClear.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        if (this.isCommandCompleted()) {
            if (this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, StandardMessagePriority.HIGH, true, false, this.commanderID));
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
     * @param command 警察命令
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandPolice> setCommand(@NotNull CommandPolice command) {
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
    public CommandExecutor<CommandPolice> calc() {
        this.result = null;
        EntityID positionID = this.agentInfo.getPosition();
        StandardEntity targetEntity = this.worldInfo.getEntity(this.targetID);

        switch (this.commandType) {

            case ACTION_REST -> {
                if (this.targetID == null) {
                    StandardEntity positionEntity = this.worldInfo.getEntity(positionID);
                    if (!(positionEntity instanceof Refuge)) {
                        List<EntityID> path = this.pathPlanning
                                .setFrom(positionID)
                                .setDestination(this.worldInfo.getEntityIDsOfType(REFUGE))
                                .calc()
                                .getResult();
                        if (path != null && path.size() > 0) {
                            Action action = this.actionExtClear
                                    .setTarget(path.get(path.size() - 1))
                                    .calc()
                                    .getAction();
                            if (action == null) {
                                action = new ActionMove(path);
                            }
                            this.result = action;
                            return this;
                        }
                    }
                } else if (!positionID.equals(this.targetID)) {
                    List<EntityID> path = this.pathPlanning.getResult(positionID, this.targetID);
                    if (path != null && path.size() > 0) {
                        Action action = this.actionExtClear
                                .setTarget(path.get(path.size() - 1))
                                .calc()
                                .getAction();
                        if (action == null) {
                            action = new ActionMove(path);
                        }
                        this.result = action;
                        return this;
                    }
                }
                this.result = new ActionRest();
                return this;
            }

            case ACTION_MOVE, ACTION_CLEAR -> {
                if (this.targetID != null) {
                    this.result = this.actionExtClear
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
                if (targetEntity instanceof Refuge) {
                    PoliceForce agent = (PoliceForce) this.agentInfo.me();
                    if (agent.getDamage() > 0) {
                        if (!positionID.equals(this.targetID)) {
                            List<EntityID> path = this.pathPlanning.getResult(positionID, this.targetID);
                            if (path != null && path.size() > 0) {
                                Action action = this.actionExtClear
                                        .setTarget(path.get(path.size() - 1))
                                        .calc()
                                        .getAction();
                                if (action == null) {
                                    action = new ActionMove(path);
                                }
                                this.result = action;
                                return this;
                            }
                        }
                        this.result = new ActionRest();
                    } else {
                        this.result = this.actionExtClear
                                .setTarget(this.targetID)
                                .calc()
                                .getAction();
                    }
                } else if (targetEntity instanceof Area) {
                    this.result = this.actionExtClear
                            .setTarget(this.targetID)
                            .calc()
                            .getAction();
                    return this;
                } else if (targetEntity instanceof Human human) {
                    if (human.isHPDefined() && human.getHP() == 0) {
                        return this;
                    }
                    if (human.isPositionDefined() && this.worldInfo.getPosition(human) instanceof Area) {
                        this.targetID = human.getPosition();
                        this.result = this.actionExtClear
                                .setTarget(this.targetID)
                                .calc()
                                .getAction();
                    }
                } else if (targetEntity instanceof Blockade blockade) {
                    if (blockade.isPositionDefined()) {
                        this.targetID = blockade.getPosition();
                        this.result = this.actionExtClear
                                .setTarget(this.targetID)
                                .calc()
                                .getAction();
                    }
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
        PoliceForce me = (PoliceForce) this.agentInfo.me();
        EntityID positionID = this.agentInfo.getPosition();
        StandardEntity targetEntity = this.worldInfo.getEntity(this.targetID);

        switch (this.commandType) {

            case ACTION_REST -> {
                if (this.targetID == null) {
                    return me.getDamage() == 0;
                }
                if (targetEntity instanceof Refuge) {
                    if (positionID.equals(this.targetID)) {
                        return me.getDamage() == 0;
                    }
                }
                return false;
            }

            case ACTION_MOVE -> {
                return this.targetID == null || (this.agentInfo.getPosition().getValue() == this.targetID.getValue());
            }

            case ACTION_CLEAR -> {
                if (this.targetID == null) {
                    return true;
                }
                if (targetEntity instanceof Road road) {
                    if (road.isBlockadesDefined()) {
                        return road.getBlockades().isEmpty();
                    }
                    return positionID.equals(this.targetID);
                }
                return true;
            }

            case ACTION_AUTONOMY -> {
                if (this.targetID != null) {
                    if (targetEntity instanceof Refuge) {
                        this.commandType = me.getDamage() > 0 ? ACTION_REST : ACTION_CLEAR;
                        return this.isCommandCompleted();
                    } else if (targetEntity instanceof Area) {
                        this.commandType = ACTION_CLEAR;
                        return this.isCommandCompleted();
                    } else if (targetEntity instanceof Human human) {
                        if ((human.isHPDefined() && human.getHP() == 0)) {
                            return true;
                        }
                        if (human.isPositionDefined() && this.worldInfo.getPosition(human) instanceof Area) {
                            this.targetID = human.getPosition();
                            this.commandType = ACTION_CLEAR;
                            return this.isCommandCompleted();
                        }
                    } else if (targetEntity instanceof Blockade blockade) {
                        if (blockade.isPositionDefined()) {
                            this.targetID = blockade.getPosition();
                            this.commandType = ACTION_CLEAR;
                            return this.isCommandCompleted();
                        }
                    }
                }
                return true;
            }

        }
        return true;
    }
}