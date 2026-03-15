package rangel.centralized.ambulance;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.centralized.CommandAmbulance;
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

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 救护队的命令执行器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelCommandExecutorAmbulance extends CommandExecutor<CommandAmbulance> {

    /**
     * 未知动作
     */
    private static final int ACTION_UNKNOWN = -1;

    /**
     * 动作:休息
     */
    private static final int ACTION_REST = CommandAmbulance.ACTION_REST;

    /**
     * 动作:移动
     */
    private static final int ACTION_MOVE = CommandAmbulance.ACTION_MOVE;

    /**
     * 动作:装载
     */
    private static final int ACTION_LOAD = CommandAmbulance.ACTION_LOAD;

    /**
     * 动作:卸载
     */
    private static final int ACTION_UNLOAD = CommandAmbulance.ACTION_UNLOAD;

    /**
     * 动作:自主行动
     */
    private static final int ACTION_AUTONOMY = CommandAmbulance.ACTION_AUTONOMY;

    /**
     * 路径规划算法
     */
    private final PathPlanning pathPlanning;

    /**
     * 复合动作:运输
     */
    private final ExtAction actionTransport;

    /**
     * 复合动作:移动
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
     * {@link RangelCommandExecutorAmbulance}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelCommandExecutorAmbulance(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.commandType = ACTION_UNKNOWN;

        //使用模块管理器读取module.cfg中的配置项对应的全限定类名通过反射来获取对应类的对象
        this.pathPlanning = moduleManager.getModule("RangelCommandExecutorAmbulance.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.actionTransport = moduleManager.getExtAction("RangelCommandExecutorAmbulance.ExtActionTransport", "adf.impl.extaction.DefaultExtActionTransport");
        this.actionExtMove = moduleManager.getExtAction("RangelCommandExecutorAmbulance.ExActionMove", "adf.impl.extaction.DefaultExtActionMove");
    }


    /**
     * 预计算时执行的方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandAmbulance> precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionTransport.precompute(precomputeData);
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
    public CommandExecutor<CommandAmbulance> resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionTransport.resume(precomputeData);
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
    public CommandExecutor<CommandAmbulance> preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionTransport.preparate();
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
    public CommandExecutor<CommandAmbulance> updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionTransport.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        if (this.isCommandCompleted()) {
            if (this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                if (this.commandType == ACTION_LOAD) {
                    this.commandType = ACTION_UNLOAD;
                    this.targetID = null;
                } else {
                    this.commandType = ACTION_UNKNOWN;
                    this.targetID = null;
                    this.commanderID = null;
                }
            }
        }
        return this;
    }


    /**
     * 设置命令
     *
     * @param command 救护队命令
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandExecutor<CommandAmbulance> setCommand(@NotNull CommandAmbulance command) {
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
    public CommandExecutor<CommandAmbulance> calc() {
        this.result = null; // 初始化变量result为null

        switch (this.commandType) { // 根据命令类型执行不同操作

            case ACTION_REST -> { // 如果是休息命令
                EntityID positionID = this.agentInfo.getPosition(); // 获取救护队个体当前位置的实体ID
                if (this.targetID == null) { // 如果目标位置为空
                    Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(REFUGE); // 获取所有避难所的实体ID
                    if (refuges.contains(positionID)) { // 如果救护队个体已经在避难所内
                        this.result = new ActionRest(); // 设置行动结果为休息操作
                    } else { // 如果救护车不在避难所内
                        List<EntityID> path = this.pathPlanning
                                .setFrom(positionID) //设置起点为当前位置
                                .setDestination(refuges) //设置终点为所有避难所的集合
                                .calc() //计算到最近避难所的路径
                                .getResult(); // 获取到最近避难所的路径
                        if (path != null && path.size() > 0) { // 如果存在可通行的路径
                            this.result = new ActionMove(path); // 设置行动结果为移动操作
                        } else { // 如果不存在可通行的路径
                            this.result = new ActionRest(); // 设置行动结果为休息操作
                        }
                    }
                    return this;
                }
                if (!positionID.equals(this.targetID)) { // 如果当前位置不是目标位置
                    List<EntityID> path = this.pathPlanning.getResult(positionID, this.targetID); // 计算到目标位置的路径
                    if (path != null && path.size() > 0) { // 如果存在可通行的路径
                        this.result = new ActionMove(path); // 设置行动结果为移动操作
                        return this;
                    }
                }
                this.result = new ActionRest(); // 如果没有找到可通行的路径，则设置行动结果为休息操作
                return this;
            }

            case ACTION_MOVE -> { // 如果是移动命令
                if (this.targetID != null) { // 如果目标位置不为空
                    this.result = this.actionExtMove
                            .setTarget(this.targetID)
                            .calc()
                            .getAction(); // 委托actionExtMove对象计算移动操作并设置行动结果
                }
                return this;
            }

            case ACTION_LOAD, ACTION_UNLOAD -> { // 如果是装载或卸载命令
                if (this.targetID != null) { // 如果目标位置不为空
                    this.result = this.actionTransport
                            .setTarget(this.targetID)
                            .calc()
                            .getAction(); // 委托actionTransport对象计算装载或卸载操作并设置行动结果
                }
                return this;
            }

            case ACTION_AUTONOMY -> { // 如果是自主行动命令
                if (this.targetID == null) { // 如果目标位置为空
                    return this; // 直接返回
                }
                StandardEntity targetEntity = this.worldInfo.getEntity(this.targetID); // 获取目标实体
                if (targetEntity instanceof Area) { // 如果目标实体是区域
                    if (this.agentInfo.someoneOnBoard() == null) { // 如果没有人员在车上
                        this.result = this.actionExtMove
                                .setTarget(this.targetID)
                                .calc()
                                .getAction(); // 委托actionExtMove对象计算移动操作并设置行动结果
                    } else { // 如果有人员在车上
                        this.result = this.actionTransport
                                .setTarget(this.targetID)
                                .calc()
                                .getAction(); // 委托actionTransport对象计算装载或卸载操作并设置行动结果
                    }
                } else if (targetEntity instanceof Human) { // 如果目标实体是人员
                    this.result = this.actionTransport
                            .setTarget(this.targetID)
                            .calc()
                            .getAction(); // 委托actionTransport对象计算装载或卸载操作并设置行动结果
                }
            }

        }
        return this; // 返回自身对象以支持链式调用
    }


    /**
     * 判断命令是否执行完毕
     *
     * @return true:执行完毕 || false:未执行完毕
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isCommandCompleted() {
        // 获取当前agentInfo所代表的人类实体对象
        Human agent = (Human) this.agentInfo.me();
        // 获取代表当前位置的EntityID对象
        EntityID positionID = this.agentInfo.getPosition();
        // 获取目标实体对象
        StandardEntity targetEntity = this.worldInfo.getEntity(this.targetID);

        // 根据不同的命令类型进行处理
        switch (this.commandType) {

            // 如果命令类型为休息，则需要检查是否达到状态要求
            case ACTION_REST -> {
                // 如果目标ID为空，则需判断代理是否处于完全健康状态
                if (this.targetID == null) {
                    return agent.getDamage() == 0;
                }
                // 如果目标实体为避难所，则需判断代理是否在该避难所内
                if (targetEntity instanceof Refuge) {
                    if (positionID.equals(this.targetID)) {
                        return agent.getDamage() == 0;
                    }
                }
                // 其它情况下不满足要求，返回false
                return false;
            }

            // 如果命令类型为移动，则需要检查是否到达目的地
            case ACTION_MOVE -> {
                return this.targetID == null || positionID.equals(this.targetID);
            }

            // 如果命令类型为装载，则需要检查是否达到状态要求
            case ACTION_LOAD -> {
                // 如果目标ID为空，则说明命令已经完成
                if (this.targetID == null) {
                    return true;
                }
                if (targetEntity instanceof Human human) {
                    // 如果目标实体为人类并且健康状况为死亡，则命令完成
                    if ((human.isHPDefined() && human.getHP() == 0)) {
                        return true;
                    }
                    if (human.isPositionDefined()) {
                        EntityID targetPositionID = human.getPosition();
                        StandardEntity targetPositionEntity = this.worldInfo.getEntity(targetPositionID);
                        //判断所有救护队agent的位置集合中是否包含目标人类位置
                        if (this.worldInfo.getEntityIDsOfType(AMBULANCE_TEAM).contains(targetPositionID)) {
                            //包含说明已经装载完成
                            return true;
                        } else {
                            //如果人类当前位置在避难所，则命令完成
                            return targetPositionEntity instanceof Refuge;
                        }
                    }
                }
                // 其它情况下不满足要求，返回false
                return false;
            }

            // 如果命令类型为卸载，则需要检查是否达到状态要求
            case ACTION_UNLOAD -> {
                // 如果目标ID存在且为区域类型，则需判断代理是否到达该区域
                if (this.targetID != null) {
                    if (targetEntity instanceof Area) {
                        if (!this.targetID.equals(positionID)) {
                            return false;
                        }
                    }
                }
                // 检查是否有人类在担架上，如果没有，则命令已完成
                return (this.agentInfo.someoneOnBoard() == null);
            }

            // 如果命令类型为自主运动，则需要根据目标实体类型进行相应处理
            case ACTION_AUTONOMY -> {
                // 如果目标ID存在且为区域类型，则需根据代理情况判断继续执行的命令类型
                if (this.targetID != null) {
                    if (targetEntity instanceof Area) {
                        // 检查是否有人类在担架上，如果没有，则更改命令类型为移动，否则改为卸载
                        this.commandType = this.agentInfo.someoneOnBoard() == null ? ACTION_MOVE : ACTION_UNLOAD;
                        return this.isCommandCompleted(); //进一步判断命令是否完成
                    }
                    // 如果目标实体为人类，则根据人类状态判断继续执行的命令类型
                    else if (targetEntity instanceof Human human) {
                        //人类死亡，命令完成
                        if (human.isHPDefined() && human.getHP() == 0) {
                            return true;
                        }
                        //如果目标实体为人类平民，则更改类型为装载
                        if (human instanceof Civilian) {
                            this.commandType = ACTION_LOAD;
                            return this.isCommandCompleted();
                        }
                    }
                }
                // 如果没有目标ID或者目标实体不是以上类型，则命令已完成
                return true;
            }
        }
        // 如果以上所有case都不满足要求，则命令已完成，返回true
        return true;
    }
}