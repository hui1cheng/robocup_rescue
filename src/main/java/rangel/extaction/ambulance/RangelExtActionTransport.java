package rangel.extaction.ambulance;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionLoad;
import adf.core.agent.action.ambulance.ActionUnload;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rangel.module.communication.RangelMessage;
import rangel.utils.ConfigUtils;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 扩展动作:运输
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
@Slf4j
public class RangelExtActionTransport extends ExtAction {

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
     * 消息管理器
     */
    private MessageManager messageManager;

    /**
     * 暂时无法使用的避难所 <br>
     * key:避难所的EntityID <br>
     * value:重新使用所需的回合数
     */
    private final Map<EntityID, Integer> freezeRefugeMap;

    /**
     * 最佳避难所
     */
    private EntityID bestRefugeID;

    /**
     * 动作目标
     */
    private EntityID targetID;

    /**
     * 上一回合所在的区域
     */
    private Area lastArea;

    /**
     * 卡住的回合数
     */
    private int stuckTimes;

    /**
     * 是否是第二次被卡住
     */
    private boolean isTwiceStuck;

    /**
     * 第二次被卡住时记录无法前往的避难所
     */
    private final Set<EntityID> unreachableRefuge;

    /**
     * {@link RangelExtActionTransport}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    public RangelExtActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.targetID = null;
        this.bestRefugeID = null;
        this.freezeRefugeMap = new HashMap<>();
        this.unreachableRefuge = new HashSet<>();
        this.stuckTimes = 0;
        this.isTwiceStuck = false;
        this.thresholdRest = ConfigUtils.getInteger("thresholdRest.transport", 100);
        this.pathPlanning = moduleManager.getModule("RangelExtActionTransport.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
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
     * <p>
     * 更新路径规划和避难所信息
     * <p>
     * 更新卡住的回合数
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    public ExtAction updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        this.messageManager = messageManager;
        this.pathPlanning.updateInfo(messageManager);
        //从世界信息中检索避难所的信息并更新变量
        this.updateFreezeRefuges();
        //从救护中心获得最佳避难所
        this.receiveMessage(messageManager);

        // 如果当前位置区域与上回合相同说明被卡住了
        if (this.agentInfo.getPositionArea().equals(lastArea)) {
            this.stuckTimes++;
            log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",卡住回合数:" + this.stuckTimes);
        } else {
            this.lastArea = this.agentInfo.getPositionArea();
            this.stuckTimes = 0;
        }

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
     * 计算Agent在每个回合中应采取的动作并将其写入{@link #result}<br>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public ExtAction calc() {
        this.result = null;
        //救护队自己
        AmbulanceTeam agent = (AmbulanceTeam) this.agentInfo.me();
        //运输的人
        Human transportHuman = this.agentInfo.someoneOnBoard();

        //如果运输的人不为null,
        if (transportHuman != null) {
            //则计算卸载动作
            this.result = this.calcUnload(agent, this.pathPlanning, transportHuman, this.targetID);
            //如果计算的结果不为null,则可以返回this
            if (this.result != null) {
                return this;
            }
        }

        if (this.needRest(agent)) {
            EntityID areaID = this.convertArea(this.targetID);
            ArrayList<EntityID> targets = new ArrayList<>();
            if (areaID != null) {
                targets.add(areaID);
            }
            this.result = this.calcRefugeAction(agent, this.pathPlanning, targets, false);
            if (this.result != null) {
                return this;
            }
        }

        //如果目标不为空
        if (this.targetID != null) {
            //则计算装载动作
            this.result = this.calcLoad(agent, this.pathPlanning, this.targetID);
        }
        return this;
    }


    /**
     * 计算装载
     * <p>
     * <ol>
     *     <li>如果目标是Human并且可以运输,则装载或移动到目标地点
     *     <li>如果目标是Blockade,则以它的位置为目标
     *     <li>如果目标是Area,则移动到目标地点
     * </ol>
     *
     * @param agent        救护队
     * @param pathPlanning 路径规划
     * @param targetID     目标的EntityID
     * @return 应执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Nullable
    private Action calcLoad(AmbulanceTeam agent, PathPlanning pathPlanning, EntityID targetID) {
        //获得目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        //如果目标不存在,则返回null
        if (targetEntity == null) {
            log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",当前没有目标");
            return null;
        }
        //获得自身所在的位置
        EntityID agentPosition = agent.getPosition();
        //如果目标实体是一个人,
        if (targetEntity instanceof Human human) {
            //如果目标的位置没有确定,则返回null
            if (!human.isPositionDefined()) {
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",不知道" + targetID + "的位置");
                return null;
            }
            //如果目标已经死亡,则返回null
            if (human.isHPDefined() && human.getHP() == 0) {
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + "," + targetID + "已经死亡");
                return null;
            }
            //获得目标所在的位置
            EntityID targetPosition = human.getPosition();
            //如果自己已经到达目标位置,
            if (agentPosition.getValue() == targetPosition.getValue()) {
                //如果目标被掩埋,则返回null
//TODO BEGIN:优化点：如果发现目标被掩埋，可以呼叫fire来救援
                if (human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + "," + targetID + "被埋");
                    return null;
//TODO END
                    //否则目标没有被掩埋并且如果目标是平民,则可以搬起来,返回动作装载
                } else if (human.getStandardURN() == CIVILIAN) {
                    //装载时发送消息去哪里避难
                    this.sendMessage(this.messageManager, this.getDestination());
                    log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",开始装载" + targetID);
                    return new ActionLoad(human.getID());
                }
                //否则没有到达目标位置,需要计算路径
            } else {
                //计算从自身位置到目标位置的路径
                List<EntityID> path = pathPlanning.getResult(agentPosition, targetPosition);
                //如果路径不为null并且路径的长度大于0,返回动作移动
                if (path != null && path.size() > 0) {
                    log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",开始移动到" + targetID);
                    return new ActionMove(path);
                }
            }
            return null;
        }
        //如果目标实体是路径,
        if (targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            //并且路障的位置已确定,则将目标实体更新为路径所在位置的实体
            if (blockade.isPositionDefined()) {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标是区域,
        if (targetEntity instanceof Area) {
            //计算出从自身到目标的路径
            List<EntityID> path = pathPlanning.getResult(agentPosition, targetEntity.getID());
            //并且有路径,则返回动作移动
            if (path != null && path.size() > 0) {
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",开始移动到" + targetID);
                return new ActionMove(path);
            }
        }
        log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",当前没有目标");
        return null;
    }


    /**
     * 计算卸载
     * <p>
     * 如果担架上已经有救护目标并且到达了避难所或者救护目标的HP为0,则卸载他
     *
     * @param agent          救护队
     * @param pathPlanning   路径规划
     * @param transportHuman 运输的人
     * @param targetID       目标的EntityID,可能是人,也可能是区域
     * @return 应执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private Action calcUnload(AmbulanceTeam agent, PathPlanning pathPlanning, Human transportHuman, EntityID targetID) {
        //如果没有运输的人,返回null
        if (transportHuman == null) {
            return null;
        }
        //如果运输的人已经死亡,返回动作卸载
//TODO BEGIN:优化点:提前计算civilian死亡所需回合数 && 到达最近refuge所需回合数
        if (transportHuman.isHPDefined() && transportHuman.getHP() == 0) {
            secondStuckEnd();
            return new ActionUnload();
        }
//TODO END
        //获得自身的位置
        EntityID agentPosition = agent.getPosition();
        log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",自身的位置:" + agentPosition);
        //如果没有目标,或者目标就是自己正在运输的人
        if (targetID == null || transportHuman.getID().getValue() == targetID.getValue()) {
            //获得自身所在位置的实体
            StandardEntity position = this.worldInfo.getEntity(agentPosition);
            //如果自己已经到达避难所,返回动作卸载
            if (position != null && position.getStandardURN() == REFUGE) {
                secondStuckEnd();
                return new ActionUnload();
                //否则前往避难所
            } else {
                //设置起点为自己所在的位置
                pathPlanning.setFrom(agentPosition);
                //设置终点为避难所
                pathPlanning.setDestination(this.getDestination());
                //计算路径
                List<EntityID> path = pathPlanning.calc().getResult();
                //如果路径不为null,并且长度大于0,前往目标地点,返回动作移动
                if (path.isEmpty() || !path.get(0).equals(this.agentInfo.getPosition())) {
                    path.add(0, this.agentInfo.getPosition());
                }

                path = firstStuckHandler(path);

                if (!path.isEmpty()) {
                    log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",没有目标,或者目标就是自己正在运输的人,规划的路径:" + path);
                    return new ActionMove(path);
                }
            }
        }
        if (targetID == null) {
            return null;
        }
        //获得目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        //如果目标实体不为null,并且目标实体是路障,
        if (targetEntity != null && targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            //如果路障的位置已知,
            if (blockade.isPositionDefined()) {
                //获取路障所在位置的实体
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标实体是区域,
        if (targetEntity instanceof Area) {
            //并且自身已经到达目标区域,返回动作卸载
            if (agentPosition.getValue() == targetID.getValue()) {
                secondStuckEnd();
                return new ActionUnload();
                //否则前往目标地点
            } else {
                //设置起点为自己所在的位置
                pathPlanning.setFrom(agentPosition);
                //设置终点为目标区域
                pathPlanning.setDestination(this.getDestination());
                //计算路径
                List<EntityID> path = pathPlanning.calc().getResult();
                //如果路径不为null,并且长度大于0,前往目标地点,返回动作移动
                if (path.isEmpty() || !path.get(0).equals(this.agentInfo.getPosition())) {
                    path.add(0, this.agentInfo.getPosition());
                }

                path = firstStuckHandler(path);

                if (!path.isEmpty()) {
                    log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",实体是区域,规划的路径:" + path);
                    return new ActionMove(path);
                }
            }
            //否则如果目标是人类,
        } else if (targetEntity instanceof Human human) {
            //并且位置确定,则计算前往避难所的动作
            if (human.isPositionDefined()) {
                return calcRefugeAction(agent, pathPlanning, Lists.newArrayList(human.getPosition()), true);
            }
            //没有计算处理,则继续
            //设置起点为自己所在的位置
            pathPlanning.setFrom(agentPosition);
            //设置终点为避难所
            pathPlanning.setDestination(this.getDestination());
            //计算路径
            List<EntityID> path = pathPlanning.calc().getResult();
            //如果路径不为null,并且长度大于0,前往目标地点,返回动作移动
            if (path.isEmpty() || !path.get(0).equals(this.agentInfo.getPosition())) {
                path.add(0, this.agentInfo.getPosition());
            }

            path = firstStuckHandler(path);

            if (!path.isEmpty()) {
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",目标是人类,规划的路径:" + path);
                return new ActionMove(path);
            }
        }
        return null;
    }

    /**
     * 第一次被卡住时的策略
     * @param path 已生成的路径
     * @return 重新规划的路径
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private List<EntityID> firstStuckHandler(List<EntityID> path) {
        // 如果被卡住超过两回合
        if (this.stuckTimes >= 2) {
            if (isTwiceStuck) {
                // 第二次卡住时，将当前目标避难所划归到无法到达，重新选择其他避难所前往
                this.bestRefugeID = null;
                EntityID refuge = path.get(path.size() - 1);
                if (this.worldInfo.getEntity(refuge) instanceof Refuge) {
                    this.unreachableRefuge.add(refuge);
                    log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",第二次卡住超过2回合,移除目标:" + refuge);
                }
            } else {
                // 第一次卡住时，从当前区域的四周选择一个没有障碍的区域前进
                Area positionArea = this.agentInfo.getPositionArea();
                Road dest = positionArea.getNeighbours()
                        .stream()
                        .map(this.worldInfo::getEntity)
                        .filter(Road.class::isInstance)
                        .map(Road.class::cast)
                        .filter(road -> !road.isBlockadesDefined() || road.getBlockades() == null || road.getBlockades().isEmpty())
                        .findAny()
                        .orElse(null);
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",第一次卡住超过2回合,dest:"+ dest +",原来规划的路径:" + path);
                if (dest != null) {
                    pathPlanning.setFrom(positionArea.getID());
                    pathPlanning.setDestination(dest.getID());
                    path = pathPlanning.calc().getResult();
                }
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",第一次卡住超过2回合,重新规划路径:" + path);
                this.isTwiceStuck = true;
            }
        }
        return path;
    }

    /**
     * 第二次卡住后，到达了其他避难所执行卸载动作前或者当前备选避难所为空时调用
     * <p>
     * 重新计算卡住次数
     * 清空不可达避难所
     * 重置卡住的状态
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private void secondStuckEnd() {
        this.unreachableRefuge.clear();
        this.isTwiceStuck = false;
    }


    /**
     * 判断救护队本人是否需要休息
     * <p>
     * 当目标受到等于或大于阈值的伤害时休息
     *
     * @param agent 代表救护队Agent本身的Human
     * @return 一个布尔值，指示对象是否需要休息
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
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


    /**
     * 计算避难行动
     * <p>
     * 到达避难所时,如果isUnload是true则卸载,否则休息
     *
     * @param human        救护队
     * @param pathPlanning 路径规划
     * @param targets      目标的EntityID的集合
     * @param isUnload     是否卸载
     * @return 应执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Nullable
    private Action calcRefugeAction(@NotNull Human human, PathPlanning pathPlanning, Collection<EntityID> targets, boolean isUnload) {
        //获得自身所在的位置
        EntityID position = human.getPosition();
        //获得地图中所有的避难所
        Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        //避难所的数量
        int size = refuges.size();
        //如果已经到达避难所,则把人放下,返回卸载动作
        if (refuges.contains(position)) {
            return isUnload ? new ActionUnload() : new ActionRest();
        }
        List<EntityID> firstResult = null;
        //遍历避难所,找到第一个合适的避难所并前往
        while (refuges.size() > 0) {
            //设置起点为自身所在的位置
            pathPlanning.setFrom(position);
            //设置终点为避难所的位置
            pathPlanning.setDestination(refuges);
            //计算出路径
            List<EntityID> path = pathPlanning.calc().getResult();
            //如果路径不为null并且路径的长度大于0
            if (path != null && path.size() > 0) {
                if (firstResult == null) {
                    firstResult = new ArrayList<>(path);
                    //如果没有别的目标就可以结束循环了,否则需要继续计算出从避难所到目标的路径
                    if (targets == null || targets.isEmpty()) {
                        break;
                    }
                }
                //路径的最后一个点即为避难所的id
                EntityID refugeID = path.get(path.size() - 1);
                //设置起点为避难所的位置
                pathPlanning.setFrom(refugeID);
                //终点为目标的位置
                pathPlanning.setDestination(targets);
                //计算出路径
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                //如果从避难所到目标有路,则可以前往该避难所,返回动作移动
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
                    return new ActionMove(path);
                }
                //否则该避难所不合适,将其移除
                refuges.remove(refugeID);
                //如果移除失败,结束循环
                if (size == refuges.size()) {
                    break;
                }
                //更新避难所的数量
                size = refuges.size();
            } else {
                break;
            }
        }
        //如果找到了,则前往该避难,返回动作移动;如果没找到,则返回null
//TODO BEGIN:没有找到合适的避难所,返回找指令
        return firstResult != null ? new ActionMove(firstResult) : null;
    }


    /**
     * 更新避难所的状态
     * <p>
     * <ol>
     *     <li>当探索到一个避难所时,在变量{@link #freezeRefugeMap}中记录没有空闲床位的避难所
     *     <li>根据床位的占用信息，计算到此避难所的所需的回合数并更新{@link #freezeRefugeMap}
     *     <li>{@link #freezeRefugeMap}的value会随回合的进行而减少,当value为0时,从{@link #freezeRefugeMap}中移除对应的key
     * </ol>
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void updateFreezeRefuges() {
        // 床位占用已满的避难所
        Set<Refuge> refuges = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .map(this.worldInfo::getEntity) //将EntityID映射为StandardEntity
                .filter(Refuge.class::isInstance) //过滤出是Refuge实例的StandardEntity
                .map(Refuge.class::cast) //将StandardEntity映射为Refuge
                .filter(Refuge::isBedCapacityDefined) //过滤出床位容量确定的避难所
                .filter(Refuge::isOccupiedBedsDefined) //过滤出已占用床位确定的避难所
                .filter(refuge -> refuge.getOccupiedBeds() >= refuge.getBedCapacity())
                .collect(Collectors.toSet());

        if (!refuges.isEmpty()) {
            for (Refuge refuge : refuges) {
                //当前避难所内需要治疗的平民
                Set<Civilian> civilians = this.worldInfo.getChanged().getChangedEntities()
                        .stream()
                        .map(this.worldInfo::getEntity) //将EntityID映射为StandardEntity
                        .filter(Civilian.class::isInstance) //过滤出是Civilian实例的StandardEntity
                        .map(Civilian.class::cast) //将StandardEntity映射为Civilian
                        .filter(Civilian::isDamageDefined) //过滤出受到伤害确定的平民
                        .filter(Civilian::isHPDefined) //过滤出HP确定的平民
                        .filter(civilian -> civilian.getHP() > 0) //过滤出还活着的平民
                        .filter(civilian -> civilian.getDamage() > 0) //过滤出正在受到伤害的平民
                        .filter(civilian -> civilian.getPosition().equals(refuge.getID())) //过滤出位置处于当前避难所的平民
                        .collect(Collectors.toSet());

                if (civilians.size() == 0 && !this.freezeRefugeMap.containsKey(refuge.getID())) {
                    this.freezeRefugeMap.put(refuge.getID(), 100);
                } else if (civilians.size() > 0) {
                    ArrayList<Integer> damageList = new ArrayList<>();
                    for (Civilian civilian : civilians) {
                        damageList.add(civilian.getDamage());
                    }
                    Collections.sort(damageList);
                    int reuseTime = 0;
                    int waitNumber = refuge.getBedCapacity() + refuge.getWaitingListSize();
                    if (damageList.size() < waitNumber) {
                        waitNumber = damageList.size();
                    }
                    for (int i = 0; i < waitNumber; i++) {
                        reuseTime += damageList.get(i);
                    }
                    this.freezeRefugeMap.putIfAbsent(refuge.getID(), reuseTime);
                    if (reuseTime < this.freezeRefugeMap.get(refuge.getID()) || this.freezeRefugeMap.get(refuge.getID()) == -1) {
                        this.freezeRefugeMap.put(refuge.getID(), reuseTime);
                    }
                }
            }
        }

        Set<EntityID> ids = new HashSet<>();
        for (Map.Entry<EntityID, Integer> entry : this.freezeRefugeMap.entrySet()) {
            this.freezeRefugeMap.put(entry.getKey(), entry.getValue() - 1);
            if (entry.getValue() - 1 <= 0)
                ids.add(entry.getKey());
        }
        if (this.agentInfo.someoneOnBoard() == null) {
            for (EntityID id : ids) {
                this.freezeRefugeMap.remove(id);
            }
        }
    }


    /**
     * 选择合适的目的地
     *
     * @return 避难所的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private Collection<EntityID> getDestination() {
        //如果已经有最佳避难所了,直接返回
        if (this.bestRefugeID != null) {
            return List.of(this.bestRefugeID);
        }
        //地图上所有的避难所
        Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(REFUGE);
        //移除掉暂时无法使用的避难所
        refuges.removeAll(this.freezeRefugeMap.keySet());
        refuges.removeAll(this.unreachableRefuge);
        log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",无法到达的避难所:" + this.unreachableRefuge);
        //如果没有空余的避难所,则退而求其次
        if (refuges.isEmpty()) {
            secondStuckEnd();
            refuges = this.worldInfo.getEntityIDsOfType(REFUGE);
        }

        int sumBedCapacity = 0;
        Map<EntityID, Integer> bedCapacityMap = new HashMap<>();
        for (EntityID entityID : refuges) {
            StandardEntity entity = this.worldInfo.getEntity(entityID);
            if (entity instanceof Refuge refuge) {
                int bedCapacity = refuge.getBedCapacity();
                sumBedCapacity += bedCapacity;
                bedCapacityMap.put(entityID, bedCapacity);
            }
        }

        Map<EntityID, Double> costList = new HashMap<>();
        for (EntityID refugeID : refuges) {
            EntityID agentID = this.agentInfo.getID();
            int bedCapacity = bedCapacityMap.get(refugeID);
            double cost = this.getCost(agentID, refugeID, bedCapacity, sumBedCapacity);
            costList.put(refugeID, cost);
        }

        double maxCost = Double.MIN_VALUE;
        EntityID refugeID = null;
        for (Map.Entry<EntityID, Double> entry : costList.entrySet()) {
            if (maxCost < entry.getValue()) {
                maxCost = entry.getValue();
                refugeID = entry.getKey();
            }
        }

        return refugeID == null ? this.worldInfo.getEntityIDsOfType(REFUGE) : List.of(refugeID);
    }


    /**
     * 获取前往指定避难所的消耗,根据空床的数量和到避难所的距离
     *
     * @param agentID        当前智能体的EntityID
     * @param refugeID       前往避难所的EntityID
     * @param bedCapacity    前往避难所的床位
     * @param sumBedCapacity 所有避难所的总床位
     * @return 消耗
     */
    private double getCost(EntityID agentID, EntityID refugeID, int bedCapacity, int sumBedCapacity) {
        int distance = this.worldInfo.getDistance(agentID, refugeID);
        double bedPercentage = (double) bedCapacity / (double) sumBedCapacity;
        return 7000.0 / distance + bedPercentage / distance;
    }


    /**
     * 向救护中心发送消息,发送最佳避难所的EntityID
     *
     * @param messageManager 消息管理器
     * @param refugeIDs      避难所EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void sendMessage(MessageManager messageManager, Collection<EntityID> refugeIDs) {
        Collection<StandardEntity> ambulanceCentre = this.worldInfo.getEntitiesOfType(AMBULANCE_CENTRE);
        //判断地图上有无救护中心
        if (ambulanceCentre.size() > 0) {
            EntityID refugeID = null;
            for (EntityID id : refugeIDs) {
                refugeID = id;
            }
            StandardEntity entity = this.worldInfo.getEntity(this.agentInfo.getID());
            if (entity instanceof AmbulanceTeam ambulanceTeam) {
                MessageAmbulanceTeam message = new MessageAmbulanceTeam(true, ambulanceTeam, RangelMessage.BEST_REFUGE, refugeID);
                messageManager.addMessage(message);
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",发送消息:最佳避难所是" + refugeID);
            }
        }
    }


    /**
     * 接受来自救护中心的消息,并更新最佳避难所的EntityID
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void receiveMessage(MessageManager messageManager) {
        if (this.agentInfo.someoneOnBoard() == null) {
            this.bestRefugeID = null;
        }

        List<CommunicationMessage> receivedMessageList = messageManager.getReceivedMessageList(MessageAmbulanceTeam.class);
        for (CommunicationMessage message : receivedMessageList) {
            MessageAmbulanceTeam messageAmbulanceTeam = (MessageAmbulanceTeam) message;
            if (this.worldInfo.getEntity(messageAmbulanceTeam.getSenderID()) instanceof AmbulanceCentre
                    && messageAmbulanceTeam.getAgentID().equals(this.agentInfo.getID())
                    && messageAmbulanceTeam.getAction() == RangelMessage.BEST_REFUGE
                    && messageAmbulanceTeam.getTargetID() != null
                    && this.worldInfo.getEntity(messageAmbulanceTeam.getTargetID()) instanceof Refuge) {
                this.bestRefugeID = messageAmbulanceTeam.getTargetID();
                log.info("回合:" + this.agentInfo.getTime() + ",AmbulanceID:" + this.agentInfo.getID() + ",接收到消息:最佳避难所是" + this.bestRefugeID);
            }
        }
    }

}