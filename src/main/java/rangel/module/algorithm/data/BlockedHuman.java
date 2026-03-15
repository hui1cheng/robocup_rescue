package rangel.module.algorithm.data;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import org.jetbrains.annotations.NotNull;
import rangel.utils.ConfigUtils;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Human;
import rescuecore2.worldmodel.EntityID;

import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 计算被困在障碍里的人的算法模块
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class BlockedHuman extends DataModule {

    /**
     * 人的体型范围半径
     */
    private final double humanRadius;

    /**
     * 被困在障碍里的人的EntityID的集合
     */
    private final Set<EntityID> blockedHumans;

    /**
     * 感知范围内的所有人类的EntityID的集合
     */
    private final Set<EntityID> changedHumanEntityIDs;

    /**
     * 无法到达的人类的EntityID的集合
     */
    private final Set<EntityID> unreachableHumans;


    /**
     * {@link BlockedHuman}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public BlockedHuman(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.blockedHumans = new HashSet<>();
        this.changedHumanEntityIDs = new HashSet<>();
        this.unreachableHumans = new HashSet<>();

        this.humanRadius = ConfigUtils.getDouble("humanRadius", 500.0);
    }


    /**
     * {@inheritDoc}
     * <p>
     * 判断人是否被困在障碍里
     *
     * @param entityID 人的EntityID
     * @return true: 被困在障碍里 || false: 不在障碍里
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public boolean getBoolean(EntityID entityID) {
        return this.blockedHumans.contains(entityID);
    }


    /**
     * {@inheritDoc}
     * <p>
     * 获取被困在障碍里的人的EntityID的集合
     *
     * @return 被困在障碍里的人的EntityID集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Collection<EntityID> getData() {
        return this.blockedHumans;
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

        //感知范围内的所有人类
        Set<EntityID> changedHumanEntityIDs = this.worldInfo.getChanged().getChangedEntities()
                .stream()
                .filter(entityID -> this.worldInfo.getEntity(entityID) instanceof Human)
                .collect(Collectors.toSet());
        this.changedHumanEntityIDs.addAll(changedHumanEntityIDs);
        this.unreachableHumans.removeAll(changedHumanEntityIDs);

        EntityID agentPosition = this.agentInfo.getPosition();
        Set<EntityID> cannotTracks = this.worldInfo.getEntityIDsOfType(
                        FIRE_BRIGADE,
                        AMBULANCE_TEAM,
                        POLICE_FORCE,
                        CIVILIAN)
                .stream()
                .filter(id -> !changedHumanEntityIDs.contains(id))
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .filter(Human::isPositionDefined)
                .filter(human -> human.getPosition().equals(agentPosition))
                .map(Human::getID)
                .collect(Collectors.toSet());
        this.unreachableHumans.addAll(cannotTracks);

        return this;
    }


    /**
     * 计算被困在障碍里的人({@link #blockedHumans})
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public DataModule calc() {
        this.changedHumanEntityIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .forEach(human -> {
                    if (this.isBlocked(human)) {
                        this.blockedHumans.add(human.getID());
                    } else {
                        this.blockedHumans.remove(human.getID());
                    }
                });
        this.blockedHumans.removeAll(this.unreachableHumans);
        this.changedHumanEntityIDs.clear();

        return this;
    }


    /**
     * 判断人是否被困在障碍里
     *
     * @param human 要判断的人
     * @return true: 被困在障碍里 || false: 不在障碍里
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean isBlocked(@NotNull Human human) {
        if (this.worldInfo.getEntity(human.getPosition()) instanceof Area area) {
            if (area.isBlockadesDefined()) {
                final Optional<java.awt.geom.Area> blockadeArea = area.getBlockades()
                        .stream()
                        .map(this.worldInfo::getEntity)
                        .filter(Blockade.class::isInstance)
                        .map(Blockade.class::cast)
                        .map(Blockade::getShape)
                        .map(java.awt.geom.Area::new)
                        .reduce((sum, a) -> {
                            sum.add(a);
                            return sum;
                        });
                if (blockadeArea.isPresent()) {
                    Point2D humanPoint = new Point2D(human.getX(), human.getY());

                    Rectangle2D.Double humanArea = new Rectangle2D.Double(
                            humanPoint.getX() - this.humanRadius,
                            humanPoint.getY() - this.humanRadius,
                            humanRadius * 2,
                            humanRadius * 2);

                    return blockadeArea.get().intersects(humanArea);
                }
            }
        }
        return false;
    }
}
