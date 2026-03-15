package rangel.module.algorithm.data;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import rangel.utils.ConfigUtils;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * 计算无法移动的人的算法模块
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class ImmovableHuman extends DataModule {

    /**
     * 人的体型范围半径
     */
    private final double humanRadius;

    /**
     * 路径点缓存
     */
    private final LinkedList<Point2D> movePointCache;

    /**
     * 自身是否无法移动
     */
    private boolean isImmovable;


    /**
     * {@link ImmovableHuman}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public ImmovableHuman(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.isImmovable = false;
        this.movePointCache = new LinkedList<>();
        this.humanRadius = ConfigUtils.getDouble("humanRadius", 500.0);
    }


    /**
     * {@inheritDoc}
     * <p>
     * 判断自身是否无法移动
     *
     * @param entityID 未用到
     * @return true: 无法移动 || false: 可以移动
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public boolean getBoolean(EntityID entityID) {
        return this.isImmovable;
    }


    /**
     * {@inheritDoc}
     * <p>
     * 未用到
     *
     * @return null
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Deprecated
    @Override
    public Collection<EntityID> getData() {
        return null;
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
    public DataModule precompute(PrecomputeData precomputeData) {
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
    public DataModule resume(PrecomputeData precomputeData) {
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
    public DataModule preparate() {
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
    public DataModule updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        //在路径点缓存中添加当前位置
        this.movePointCache.addFirst(new Point2D(this.agentInfo.getX(), this.agentInfo.getY()));
        return this;
    }


    /**
     * 计算自身的移动状态({@link #isImmovable})
     *
     * @return this
     * @author Kinnrai
     */
    @Override
    public DataModule calc() {
        if (this.agentInfo.getTime() <= this.scenarioInfo.getKernelAgentsIgnoreuntil()) {
            return this;
        }
        //上一回合执行的动作
        Action lastAction = this.agentInfo.getExecutedAction(this.agentInfo.getTime() - 1);
        //如果上一回合执行的动作是移动
        if (lastAction instanceof ActionMove actionMove) {
            List<EntityID> path = actionMove.getPath();
            //如果上一回合移动路径为空或者起点是当前位置
            if (path.isEmpty() || path.get(0).equals(this.agentInfo.getPosition())) {
                double d1 = Double.POSITIVE_INFINITY;
                Point2D point0 = this.movePointCache.get(0);
                Point2D point1 = this.movePointCache.get(1);
                if (actionMove.getUsePosition()) {
                    Point2D destination = new Point2D(actionMove.getPosX(), actionMove.getPosY());
                    d1 = GeometryTools2D.getDistance(point0, destination);
                }

                double d2 = GeometryTools2D.getDistance(point0, point1);
                this.isImmovable = d1 > humanRadius && d2 <= humanRadius * 6;
                return this;
            }
        }
        this.isImmovable = false;
        return this;
    }
}
