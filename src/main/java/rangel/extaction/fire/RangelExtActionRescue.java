package rangel.extaction.fire;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionRescue;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rangel.utils.ConfigUtils;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.BLOCKADE;

/**
 * 扩展动作:救援
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
@Slf4j
public class RangelExtActionRescue extends ExtAction {

    /**
     * 路径规划算法
     */
    private final PathPlanning pathPlanning;

    /**
     * 表示判断目标agent是否需要休息的阈值
     */
    private final int thresholdRest;

    /**
     * 内核时间
     */
    private int kernelTime;

    /**
     * 动作目标
     */
    private EntityID targetID;


    /**
     * {@link RangelExtActionRescue}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelExtActionRescue(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.targetID = null;
        this.thresholdRest = ConfigUtils.getInteger("thresholdRest.rescue", 100);
        this.pathPlanning = moduleManager.getModule("RangelExtActionRescue.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
    }


    /**
     * 预计算的方法
     * <p>
     * 执行路径规划模块的预计算并获取内核时间
     *
     * @param precomputeData 预计算的数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public ExtAction precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        try {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        } catch (NoSuchConfigOptionException e) {
            this.kernelTime = -1;
        }
        return this;
    }


    /**
     * 预计算模式的初始化处理方法
     * <p>
     * 执行路径规划模块的预计算并获取内核时间
     *
     * @param precomputeData 预计算的数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public ExtAction resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        try {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        } catch (NoSuchConfigOptionException e) {
            this.kernelTime = -1;
        }
        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     * <p>
     * 执行路径规划模块的预计算并获取内核时间
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public ExtAction preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        try {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        } catch (NoSuchConfigOptionException e) {
            this.kernelTime = -1;
        }
        return this;
    }


    /**
     * 一种更新agent内部信息的方法，每一回合都执行
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public ExtAction updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        //判断是否过多调用路径规划模块，确保不超过两次重新规划
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        return this;
    }


    /**
     * 设置目标({@link #targetID})
     *
     * @param target 表示操作目标的实体ID
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public ExtAction setTarget(EntityID target) {
        this.targetID = null;
        if (target != null) {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if (entity instanceof Human || entity instanceof Area) {
                this.targetID = target;
                return this;
            }
        }
        return this;
    }


    /**
     * 计算Agent在每个回合中应采取的动作并将其写入{@link #result}
     * <ol>
     *     <li>如果消防队Agent需要休息则留在原地
     *     <li>如果不需要休息且有目标，则获取目标对应的Action写入{@link #result}
     *     <li>否则返回null
     * </ol>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public ExtAction calc() {
        this.result = null;
        FireBrigade agent = (FireBrigade) this.agentInfo.me();

        //没有使用
        if (this.needRest(agent)) {
            EntityID areaID = this.convertArea(this.targetID);
            ArrayList<EntityID> targets = new ArrayList<>();
            if (areaID != null) {
                targets.add(areaID);
            }
        }

        if (this.targetID != null) {
            this.result = this.calcRescue(agent, this.pathPlanning, this.targetID);
        }
        return this;
    }


    /**
     * 计算救援，返回一个对应于动作目标的动作
     * <p>
     * <ol>
     *     <li>如果目标是{@link Human}并且与消防队({@link FireBrigade})位于同一位置，则返回{@link ActionRescue};如果目标位于不同位置且可到达，则返回{@link ActionMove}
     *     <li>如果目标是{@link Blockade}并且可到达，则返回{@link ActionMove}
     *     <li>否则返回 null
     * </ol>
     *
     * @param agent        执行动作的消防队
     * @param pathPlanning 路径规划
     * @param targetID     当前目标的EntityID
     * @return 应该执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Nullable
    private Action calcRescue(FireBrigade agent, PathPlanning pathPlanning, EntityID targetID) {
        //根据实体ID获得目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        if (targetEntity == null) {
            log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",当前没有目标");
            return null;
        }
        //获得消防队的位置
        EntityID agentPosition = agent.getPosition();
        //如果目标实体是个人,
        if (targetEntity instanceof Human human) {
            //如果目标的位置没有确定,没法去救,直接返回空
            if (!human.isPositionDefined()) {
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",不知道" + targetID + "的位置");
                return null;
            }
            //如果目标的血量为0,救不了了,直接返回空
            if (human.isHPDefined() && human.getHP() == 0) {
                log.info("回合:" + this.agentInfo.getTime() + ",FireBrigadeID:" + this.agentInfo.getID() + "," + targetID + "已经死亡");
                return null;
            }
            //获得目标人类的位置
            EntityID targetPosition = human.getPosition();
            //如果消防队到达目标人类所在的位置,
            if (agentPosition.getValue() == targetPosition.getValue()) {
                //并且该人类的被掩埋程度大于0,开始救援,返回救援动作
                if (human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    log.info("回合:" + this.agentInfo.getTime() + ",FireBrigadeID:" + this.agentInfo.getID() + ",救援目标:" + targetID);
                    return new ActionRescue(human);
                }
                //否则没有到达目标人类的所在的位置,
            } else {
                //计算出从自己到目标的路径
                List<EntityID> path = pathPlanning.getResult(agentPosition, targetPosition);
                //如果该路径不为空,并且路径的长度大于0,移动到目标所在的位置,返回动作移动
                if (path != null && path.size() > 0) {
                    log.info("回合:" + this.agentInfo.getTime() + ",FireBrigadeID:" + this.agentInfo.getID() + ",前往" + targetID + "所在的位置");
                    return new ActionMove(path);
                }
            }
            return null;
        }
        //如果目标实体是路障,
        if (targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            //并且路障的位置是已知的,将目标实体赋值为该路障的所在位置的实体
            if (blockade.isPositionDefined()) {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标实体是一个区域,
        if (targetEntity instanceof Area) {
            //计算出从自己到目标的路径
            List<EntityID> path = pathPlanning.getResult(agentPosition, targetEntity.getID());
            //如果该路径不为空,并且路径的长度大于0,移动到目标所在的位置,返回动作移动
            if (path != null && path.size() > 0) {
                log.info("回合:" + this.agentInfo.getTime() + ",FireBrigadeID:" + this.agentInfo.getID() + ",前往" + targetID + "所在的位置");
                return new ActionMove(path);
            }
        }
        log.info("回合:" + this.agentInfo.getTime() + ",FireBrigadeID:" + this.agentInfo.getID() + ",当前没有目标");
        return null;
    }


    /**
     * 判断消防员本人是否需要休息
     * <p>
     * 当目标受到等于或大于阈值的伤害时休息
     *
     * @param agent 代表消防队Agent本身的Human
     * @return 一个布尔值，指示对象是否需要休息
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean needRest(@NotNull Human agent) {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if (hp == 0 || damage == 0) {
            return false;
        }
        int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
        if (this.kernelTime == -1) {
            try {
                this.kernelTime = this.scenarioInfo.getKernelTimesteps();
            } catch (NoSuchConfigOptionException e) {
                this.kernelTime = -1;
            }
        }
        return damage >= this.thresholdRest || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
    }


    /**
     * 转换为区域
     * <p>
     * 将EntityID转换为EntityID所指示的区域或EntityID所指示的Human所在区域的EntityID
     * <ol>
     *     <li>如果EntityID所指示的实体是区域({@link Area}),则返回该区域的EntityID
     *     <li>如果是{@link Human}或 {@link Blockade}，则返回目标所在区域的 EntityID
     *     <li>否则返回null
     * </ol>
     *
     * @param targetID 目标的EntityID
     * @return 目标区域的实体ID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Nullable
    private EntityID convertArea(EntityID targetID) {
        StandardEntity entity = this.worldInfo.getEntity(targetID);
        if (entity == null) {
            return null;
        }
        if (entity instanceof Human human) {
            if (human.isPositionDefined()) {
                EntityID position = human.getPosition();
                if (this.worldInfo.getEntity(position) instanceof Area) {
                    return position;
                }
            }
        } else if (entity instanceof Area) {
            return targetID;
        } else if (entity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) entity;
            if (blockade.isPositionDefined()) {
                return blockade.getPosition();
            }
        }
        return null;
    }
}