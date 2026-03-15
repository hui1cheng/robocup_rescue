package rangel.extaction.police;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.police.ActionClear;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rangel.module.algorithm.data.DataModule;
import rangel.utils.ConfigUtils;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.*;

import static java.util.stream.Collectors.toSet;
import static rescuecore2.standard.entities.StandardEntityURN.POLICE_FORCE;

/**
 * 扩展动作:清理
 * <p>
 * 包含以下两个动作:
 * <ul>
 *     <li>{@link ActionClear}
 *     <li>{@link ActionMove}
 * </ul>
 * 调用流程:{@link #setTarget(EntityID)} -> {@link #calc()} -> {@link #getAction()}
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
@Slf4j
public class RangelExtActionClear extends ExtAction {

    /**
     * agent的体型半径
     */
    private static final double HUMAN_RADIUS = ConfigUtils.getDouble("humanRadius", 500.0);

    /**
     * 路径规划算法模块
     */
    private final PathPlanning pathPlanning;

    /**
     * 计算无法移动的人的算法模块
     */
    private final DataModule immovableHuman;

    /**
     * 计算被困在障碍里的人的算法模块
     */
    private final DataModule blockedHuman;

    /**
     * 可能作为目标的实体的EntityID的集合 <br>
     * key:可能作为目标的实体的EntityID <br>
     * value:应执行的动作
     */
    private final Map<EntityID, Action> targetCache;

    /**
     * 警察动作目标的EntityID
     */
    private EntityID targetID;


    public RangelExtActionClear(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.targetID = null;
        this.targetCache = new HashMap<>();

        this.pathPlanning = moduleManager.getModule("RangelExtActionClear.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.immovableHuman = moduleManager.getModule("RangelExtActionClear.ImmovableHuman");
        this.blockedHuman = moduleManager.getModule("RangelExtActionClear.BlockedHuman");
    }


    /**
     * 预计算时执行的方法
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public ExtAction precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }

        this.pathPlanning.precompute(precomputeData);
        this.immovableHuman.precompute(precomputeData);
        this.blockedHuman.precompute(precomputeData);

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
    public ExtAction resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }

        this.pathPlanning.resume(precomputeData);
        this.immovableHuman.resume(precomputeData);
        this.blockedHuman.resume(precomputeData);

        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public ExtAction preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }

        this.pathPlanning.preparate();
        this.immovableHuman.preparate();
        this.blockedHuman.preparate();

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
    public ExtAction updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        this.pathPlanning.updateInfo(messageManager);
        this.immovableHuman.updateInfo(messageManager);
        this.blockedHuman.updateInfo(messageManager);

        this.targetID = null;
        this.targetCache.clear();

        return this;
    }


    /**
     * 设置目标,给{@link #targetID}赋值
     *
     * @param targetID 表示操作目标的EntityID
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public ExtAction setTarget(EntityID targetID) {
        this.targetID = null;
        StandardEntity entity = this.worldInfo.getEntity(targetID);
        if (entity != null) {
            if (entity instanceof Road) {
                this.targetID = targetID;
            } else if (entity instanceof Blockade blockade) {
                this.targetID = blockade.getPosition();
            } else if (entity instanceof Building) {
                this.targetID = targetID;
            }
        }
        return this;
    }


    /**
     * 计算agent应该的动作
     * <p>
     * <ol>
     *     <li>先看缓存中是否存在目标,如果有目标,则直接使用缓存中的目标
     *     <li>判断agent是否在障碍中,如果在障碍中,则调用{@link #getShrinkClearAction(EntityID)}方法清除障碍
     *     <li>判断agent是否被卡住,如果被卡住,则调用{@link #getDefaultAction()}方法获得一个动作
     * </ol>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public ExtAction calc() {
        this.result = null;
        EntityID agentID = this.agentInfo.getID();

        if (this.targetID == null) {
            return this;
        }

        //先看缓存中是否存在目标
        if (this.targetCache.containsKey(this.targetID)) {
            this.result = this.targetCache.get(this.targetID);
            log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",缓存中存在目标:" + this.targetID);
            return this;
        }

        //判断agent是否在障碍中
        if (this.blockedHuman.calc().getBoolean(agentID)) {
            this.result = this.getShrinkClearAction(this.agentInfo.getPosition());
            if (this.result != null) {
                this.targetCache.put(this.targetID, this.result);
                log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",在障碍中,PositionID:" + this.agentInfo.getPosition().getValue());
                return this;
            }
        }

        //判断agent是否被卡住
        if (this.immovableHuman.calc().getBoolean(agentID)) {
            this.result = this.getDefaultAction();
            this.targetCache.put(this.targetID, this.result);
            return this;
        }

        List<EntityID> path = this.pathPlanning
                .setFrom(this.agentInfo.getPosition())
                .setDestination(this.targetID)
                .calc()
                .getResult();
        if (path.isEmpty() || !path.get(0).equals(this.agentInfo.getPosition())) {
            path.add(0, this.agentInfo.getPosition());
        }
        log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",当前位置:" + this.agentInfo.getPosition() + ",根据路径规划计算出的路径:" + path);

        Map<EntityID, List<Line2D>> concretePath = this.getGeometryPath(path);
        for (EntityID id : path) {
            List<Line2D> concrete = concretePath.get(id);
            if (!concrete.isEmpty()) {
                continue;
            }
            List<Line2D> addition = this.getGeometryPathToBlockedHumans(id);
            concrete.addAll(addition);
        }

        List<EntityID> actualPath = new LinkedList<>();
        for (EntityID id : path) {
            actualPath.add(id);
            List<Line2D> concrete = concretePath.get(id);
            Line2D clearLine = this.getClearLine(id, concrete);
            if (clearLine == null) {
                continue;
            }

            this.result = this.getRectangleClearAction(actualPath, clearLine);
            this.targetCache.put(this.targetID, this.result);
            return this;
        }

        if (path.size() >= 2) {
            log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",根据路径规划计算出的路径:" + path);
            this.result = new ActionMove(path);
        } else {
            this.result = this.getDefaultAction();
        }
        this.targetCache.put(this.targetID, this.result);
        return this;
    }


    /**
     * 从要清除的路线列表中计算下一条路线
     *
     * @param path EntityID列表，是判断是否包含agent所在Area的目标路径
     * @return 返回到目的地的路线，不包括agent的位置和目的地位置
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private @NotNull Map<EntityID, List<Line2D>> getGeometryPath(@NotNull List<EntityID> path) {
        Map<EntityID, List<Line2D>> geometryPathMap = new HashMap<>();

        int n = path.size();
        EntityID from = path.get(0);
        EntityID destination = path.get(n - 1);
        Point2D agentPoint = new Point2D(this.agentInfo.getX(), this.agentInfo.getY());
        List<Line2D> geometryPath = new LinkedList<>();

        if (n == 1) {
            if (this.worldInfo.getEntity(from) instanceof Area area) {
                Point2D destinationPoint = new Point2D(area.getX(), area.getY());

                geometryPath = new LinkedList<>();
                geometryPath.add(new Line2D(agentPoint, destinationPoint));
                geometryPath.addAll(this.getGeometryPathToAllNeighbor(from, null));
                geometryPathMap.put(from, this.splitLine2D(geometryPath));
                return geometryPathMap;
            }
        } else {
            for (int i = 1; i < n - 1; i++) {
                EntityID id = path.get(i);            // agent所在的EntityID
                EntityID previous = path.get(i - 1);  // agent之前所在的EntityID
                EntityID next = path.get(i + 1);      // agent下一个移动到的EntityID
                if (this.worldInfo.getEntity(id) instanceof Area area) {
                    Edge previousEdge = area.getEdgeTo(previous);
                    Edge nextEdge = area.getEdgeTo(next);
                    Point2D point = new Point2D(area.getX(), area.getY());
                    geometryPath.add(new Line2D(previousEdge.getLine().getPoint(0.5), point));
                    geometryPath.add(new Line2D(point, nextEdge.getLine().getPoint(0.5)));
                    geometryPathMap.put(id, this.splitLine2D(geometryPath));
                }
            }
            if (this.worldInfo.getEntity(from) instanceof Area area) {
                Point2D point = new Point2D(area.getX(), area.getY());
                Edge nextEdge = area.getEdgeTo(path.get(1));
                Point2D nextPoint = nextEdge.getLine().getPoint(0.5);
                Line2D cn = new Line2D(point, nextPoint);
                Point2D closest = GeometryTools2D.getClosestPointOnSegment(cn, agentPoint); //找到直线上距离最近的点

                geometryPath = new LinkedList<>();
                if (closest.equals(point)) {
                    geometryPath.add(new Line2D(agentPoint, point));
                    geometryPath.add(cn);
                } else {
                    geometryPath.add(new Line2D(agentPoint, nextPoint));
                }
                geometryPathMap.put(from, this.splitLine2D(geometryPath));
            }
            if (this.worldInfo.getEntity(destination) instanceof Area area) {
                Point2D point = new Point2D(area.getX(), area.getY());
                Edge previousEdge = area.getEdgeTo(path.get(n - 2));

                geometryPath = new LinkedList<>();
                geometryPath.add(new Line2D(previousEdge.getLine().getPoint(0.5), point));
                geometryPath.addAll(this.getGeometryPathToAllNeighbor(destination, path.get(n - 2)));
                geometryPathMap.put(destination, this.splitLine2D(geometryPath));
            }
            return geometryPathMap;
        }
        return geometryPathMap;
    }


    /**
     * 计算到相邻EntityID的路线,如果下一个实体 ID 相邻，则将其中心点包含在路径中
     *
     * @param id      目标实体的EntityID
     * @param ignored path(n-2)的EntityID
     * @return 返回到目标实体的路线
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @NotNull List<Line2D> getGeometryPathToAllNeighbor(EntityID id, EntityID ignored) {
        if (this.worldInfo.getEntity(id) instanceof Area area) {
            List<Line2D> geometryPath = new LinkedList<>();
            List<EntityID> neighbors = area.getNeighbours();
            for (EntityID neighbor : neighbors) {
                if (neighbor.equals(ignored)) {
                    continue;
                }
                geometryPath.add(new Line2D(new Point2D(area.getX(), area.getY()), area.getEdgeTo(neighbor).getLine().getPoint(0.5)));
            }
            return geometryPath;
        }
        return new LinkedList<>();
    }


    /**
     * 通往附近被困在瓦砾中的平民的路径列表
     *
     * @param id 到达目的地的路径所经过的实体 ID
     * @return 如果您能找出通往埋在瓦砾中的公民的路线，请将其包含在路径中
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @NotNull List<Line2D> getGeometryPathToBlockedHumans(EntityID id) {
        List<Human> blockedHumans = this.blockedHuman.calc().getData()
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .filter(human -> human.getStandardURN() != POLICE_FORCE)
                .filter(human -> human.getPosition().equals(id))
                .toList();
        List<Line2D> geometryPath = new LinkedList<>();
        for (Human blockedHuman : blockedHumans) {
            Point2D humanPoint = new Point2D(blockedHuman.getX(), blockedHuman.getY());
            Point2D myPoint = new Point2D(this.agentInfo.getX(), this.agentInfo.getY());
            int clearDistance = this.scenarioInfo.getClearRepairDistance();
            Line2D line = new Line2D(myPoint, humanPoint);
            Vector2D vector = line.getDirection().normalised().scale(clearDistance);
            geometryPath.add(new Line2D(myPoint, vector));
        }
        return this.splitLine2D(geometryPath);
    }


    /**
     * 将线段分割为多条子线段({@link Line2D})
     *
     * @param lines 要分割的线的列表
     * @return 拆分后的线的列表
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @NotNull List<Line2D> splitLine2D(@NotNull List<Line2D> lines) {
        List<Line2D> subLines = new LinkedList<>();
        for (Line2D line : lines) {
            double lineLength = line.getDirection().getLength();
            double clearDistance = this.scenarioInfo.getClearRepairDistance() * 0.3;
            int number = (int) Math.ceil(lineLength / clearDistance);

            for (int i = 0; i < number; i++) {
                Point2D originPoint = line.getPoint(clearDistance * i / lineLength);
                Point2D endPoint = line.getPoint(Math.min(clearDistance * (i + 1) / lineLength, 1.0));
                subLines.add(new Line2D(originPoint, endPoint));
            }
        }
        return subLines;
    }


    /**
     * 从要打开的直线列表中，只取出与某个区域内的障碍重叠的一条直线,如果它的延伸部分有直线则加入它们
     *
     * @param id           将成为移动路线的EntityID（Area）
     * @param geometryPath 要打开的范围由直线列表表示
     * @return Line2D 从由concrete表示的行列表中仅提取与由id指示的区域中的瓦砾重叠的一行
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @Nullable Line2D getClearLine(EntityID id, List<Line2D> geometryPath) {
        if (this.worldInfo.getEntity(id) instanceof Area area) {
            if (!area.isBlockadesDefined()) {
                return null;
            }

            List<EntityID> blockadeIDs = area.getBlockades();
            if (blockadeIDs.isEmpty()) {
                return null;
            }

            //将多个障碍区域合并为一个
            java.awt.geom.Area blockadeArea = blockadeIDs
                    .stream()
                    .map(this.worldInfo::getEntity)
                    .filter(Blockade.class::isInstance)
                    .map(Blockade.class::cast)
                    .map(Blockade::getShape)
                    .map(java.awt.geom.Area::new)
                    .reduce((areaA, areaB) -> {
                        areaA.add(areaB);
                        return areaA;
                    })
                    .orElse(null);

            Line2D clearLine = null;
            int i;
            for (i = 0; i < geometryPath.size(); i++) {
                java.awt.geom.Area pathArea = getGeometryArea(geometryPath.get(i));
                pathArea.intersect(blockadeArea);
                //如果路径与障碍重叠
                if (!pathArea.isEmpty()) {
                    clearLine = geometryPath.get(i);
                    break;
                }
            }
            if (clearLine == null) {
                return null;
            }

            for (++i; i < geometryPath.size(); ++i) {
                Line2D next = geometryPath.get(i);
                if (!canLink(clearLine, next)) {
                    break;
                }
                clearLine = new Line2D(clearLine.getOrigin(), next.getEndPoint());
            }
            return clearLine;
        }
        return null;
    }


    /**
     * 将直线({@link Line2D})转换为区域({@link java.awt.geom.Area})
     *
     * @param line 将成为移动路径的路径线
     * @return 转换后的几何图形
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Contract("_ -> new")
    private @NotNull java.awt.geom.Area getGeometryArea(@NotNull Line2D line) {
        double originX = line.getOrigin().getX();
        double originY = line.getOrigin().getY();
        double endX = line.getEndPoint().getX();
        double endY = line.getEndPoint().getY();

        double length = Math.hypot(endX - originX, endY - originY);
        double ldx = (endY - originY) * HUMAN_RADIUS / length;
        double ldy = (originX - endX) * HUMAN_RADIUS / length;
        double rdx = (originY - endY) * HUMAN_RADIUS / length;
        double rdy = (endX - originX) * HUMAN_RADIUS / length;

        Point2D p1 = new Point2D(originX + ldx, originY + ldy);
        Point2D p2 = new Point2D(endX + ldx, endY + ldy);
        Point2D p3 = new Point2D(endX + rdx, endY + rdy);
        Point2D p4 = new Point2D(originX + rdx, originY + rdy);

        Point2D[] points = {p1, p2, p3, p4};
        Path2D path = new Path2D.Double();
        path.moveTo(points[0].getX(), points[0].getY());

        for (int i = 1; i < points.length; ++i) {
            path.lineTo(points[i].getX(), points[i].getY());
        }
        path.closePath();
        return new java.awt.geom.Area(path);
    }


    /**
     * 获得矩形框清理动作
     *
     * @param path      路径
     * @param clearLine 清理引导线
     * @return 应执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Contract("_, _ -> new")
    private @NotNull Action getRectangleClearAction(List<EntityID> path, @NotNull Line2D clearLine) {
        Point2D myPositionPoint = new Point2D(this.agentInfo.getX(), this.agentInfo.getY());
        Point2D originPoint = clearLine.getOrigin();
        Point2D endPoint = clearLine.getEndPoint();
        int agentInfoTime = this.agentInfo.getTime();
        Vector2D directionVector = clearLine.getDirection();

        int clearDistance = this.scenarioInfo.getClearRepairDistance();
        Vector2D clearVector = directionVector.normalised().scale(clearDistance);

        double distance = GeometryTools2D.getDistance(myPositionPoint, originPoint);
        if (distance <= HUMAN_RADIUS) {
            log.info("回合:" + agentInfoTime + ",PoliceID:" + this.agentInfo.getID() + ",进行矩形框清理:向量为(" + (int) clearVector.getX() + "," + (int) clearVector.getY() + ")");
            return new ActionClear(this.agentInfo, clearVector);
        } else {
            log.info("回合:" + agentInfoTime + ",PoliceID:" + this.agentInfo.getID() + ",目标为(" + this.targetID + ")");
            log.info("回合:" + agentInfoTime + ",PoliceID:" + this.agentInfo.getID() + ",引导线为(" + clearLine + ")");
            log.info("回合:" + agentInfoTime + ",PoliceID:" + this.agentInfo.getID() + ",路径为(" + path + ")");

            int x = (int) originPoint.getX();
            int y = (int) originPoint.getY();

            if (agentInfoTime > this.scenarioInfo.getKernelAgentsIgnoreuntil()) {
                Action lastAction = this.agentInfo.getExecutedAction(agentInfoTime - 2);
                //如果上上回合执行的动作是移动，并且目的地与本次目的地相同，说明陷入两地来回的循环，直接返回清理动作结束死循环
                if (lastAction instanceof ActionMove actionMove) {
                    if (actionMove.getPosX() == x && actionMove.getPosY() == y) {
                        return new ActionClear((int) endPoint.getX(), (int) endPoint.getY());
                    }
                }
            }

            log.info("回合:" + agentInfoTime + ",PoliceID:" + this.agentInfo.getID() + ",开始移动:目的地为(" + x + "," + y + ")");
            return new ActionMove(path, x, y);
        }
    }


    /**
     * 获得收缩清理动作,一般在代理本身被埋在障碍中时实施
     *
     * @param entityID 要清理的实体的EntityID
     * @return 应执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @Nullable Action getShrinkClearAction(EntityID entityID) {
        EntityID myPositionID = this.worldInfo.getPosition(this.agentInfo.getID()).getID();
        if (this.worldInfo.getEntity(entityID) instanceof Area area) {
            if (!area.isBlockadesDefined()) {
                log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",不知道该区域有没有障碍物");
                return null;
            }

            //对目标区域的障碍按照与自身的距离进行排序
            List<EntityID> blockadeIDs = area.getBlockades()
                    .stream()
                    .sorted(Comparator.comparing(blockadeID -> this.worldInfo.getDistance(myPositionID, blockadeID)))
                    .toList();
            if (blockadeIDs.isEmpty()) {
                log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",目标区域:" + entityID + "障碍为空");
                return null;
            }

            //自身所占的几何区域
            java.awt.geom.Area agentArea = new java.awt.geom.Area(
                    new Ellipse2D.Double(
                            this.agentInfo.getX() - HUMAN_RADIUS,
                            this.agentInfo.getY() - HUMAN_RADIUS,
                            HUMAN_RADIUS * 2,
                            HUMAN_RADIUS * 2
                    )
            );

            for (EntityID blockadeID : blockadeIDs) {
                if (this.worldInfo.getEntity(blockadeID) instanceof Blockade blockade) {
                    java.awt.geom.Area blockadeArea = new java.awt.geom.Area(blockade.getShape());
                    //如果自身与障碍相交,则返回动作清理
                    blockadeArea.intersect(agentArea);
                    if (!blockadeArea.isEmpty()) {
                        log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",自身与障碍相交,开始清理障碍:" + blockadeID);
                        return new ActionClear(blockadeID);
                    }
                }
            }

            // 以下逻辑会导致警察卡住
            //如果没有障碍与自身相交,则对最近的一个障碍进行清理
//            log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",没有障碍与自身相交,则开始清理最近的一个障碍:" + blockadeIDs.get(0));
//            return new ActionClear(blockadeIDs.get(0));
        }
        log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",当前没有目标");
        return null;
    }


    /**
     * 无其它事可做时获得应执行的动作
     *
     * @return 应执行的动作
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private @Nullable Action getDefaultAction() {
        EntityID myPositionID = this.agentInfo.getPosition();
        Set<EntityID> targetIDs = new HashSet<>();
        //添加当前位置
        targetIDs.add(myPositionID);

        if (this.worldInfo.getEntity(myPositionID) instanceof Area area) {
            //添加所有邻居
            List<EntityID> neighborIDs = area.getNeighbours();
            targetIDs.addAll(neighborIDs);

            //添加所有邻居的邻居
            Set<EntityID> neighborAreas = neighborIDs
                    .stream()
                    .map(this.worldInfo::getEntity)
                    .filter(Area.class::isInstance)
                    .map(Area.class::cast)
                    .map(Area::getNeighbours)
                    .flatMap(List::stream)
                    .collect(toSet());
            targetIDs.addAll(neighborAreas);

            //从目标中选出一个最近的障碍
            EntityID blockadeID = targetIDs
                    .stream()
                    .map(this.worldInfo::getEntity)
                    .filter(Area.class::isInstance)
                    .map(Area.class::cast)
                    .filter(Area::isBlockadesDefined)
                    .map(Area::getBlockades)
                    .flatMap(List::stream)
                    .min(Comparator.comparing(this::getDistance))
                    .orElse(null);

            //如果目标存在
            if (blockadeID != null) {
                //如果在自身的清理范围内,则返回动作清理
                if (this.getDistance(blockadeID) <= this.scenarioInfo.getClearRepairDistance()) {
                    log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",开始清理障碍" + blockadeID);
                    return new ActionClear(blockadeID);
                }

                //如果不在自身的清理范围内,则返回动作移动
                if (this.worldInfo.getEntity(blockadeID) instanceof Blockade blockade) {
                    List<EntityID> path = this.pathPlanning
                            .setFrom(myPositionID)
                            .setDestination(blockade.getPosition())
                            .calc()
                            .getResult();
                    log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",开始移动到障碍" + blockadeID + "的位置");
                    return new ActionMove(path, blockade.getX(), blockade.getY());
                }
            }
        }
        log.info("回合:" + this.agentInfo.getTime() + ",PoliceID:" + this.agentInfo.getID() + ",当前没有目标");
        return null;
    }


    /**
     * 判断两条直线({@link Line2D})是否可以连接
     * <p>
     * 当line1的终点和line1的起点相同时，将两条线中的每一条视为一个二维向量 <br>
     * 如果两个向量的x和y分量之差接近0，则确定两条线可以连接
     *
     * @param line1 line1
     * @param line2 line2
     * @return true:可以连接 || false:不可以连接
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private boolean canLink(@NotNull Line2D line1, @NotNull Line2D line2) {
        if (line1.getEndPoint().equals(line2.getOrigin())) {
            Vector2D vector1 = line1.getDirection().normalised();
            Vector2D vector2 = line2.getDirection().normalised();
            return GeometryTools2D.nearlyZero(vector1.getX() - vector2.getX()) && GeometryTools2D.nearlyZero(vector1.getY() - vector2.getY());
        }
        return false;
    }


    /**
     * 获得从自身当前位置到目标障碍的最近距离
     *
     * @param blockadeID 障碍的EntityID
     * @return 从自身当前位置到目标障碍的最近距离
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private double getDistance(EntityID blockadeID) {
        if (this.worldInfo.getEntity(blockadeID) instanceof Blockade blockade) {
            List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(blockade.getApexes()), true);

            Point2D myPositionPoint = new Point2D(this.agentInfo.getX(), this.agentInfo.getY());
            Point2D blockadeClosestPoint = lines
                    .stream()
                    .map(line -> GeometryTools2D.getClosestPointOnSegment(line, myPositionPoint))
                    .min((point1, point2) -> {
                        double distance1 = GeometryTools2D.getDistance(myPositionPoint, point1);
                        double distance2 = GeometryTools2D.getDistance(myPositionPoint, point2);
                        return Double.compare(distance1, distance2);
                    })
                    .orElse(null);
            if (blockadeClosestPoint != null) {
                return GeometryTools2D.getDistance(myPositionPoint, blockadeClosestPoint);
            }
        }
        return Double.MAX_VALUE;
    }

}
