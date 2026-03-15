package rangel.module.algorithm.cluster;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.algorithm.DynamicClustering;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.Queue;
import java.util.*;

/**
 * 改良的聚类模块
 * <p>
 * 基于{@link KMeansPlusClustering},对聚类进行扩展
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @see KMeansPlusClustering
 */
public class RangelClustering extends DynamicClustering {

    /**
     * 用于存储聚类间相邻关系{@link #neighborClusterIndexMap}的关键字
     */
    private static final String KEY_NEIGHBOR = "clustering.neighbor.";

    /**
     * 原始聚类模块
     */
    private final Clustering clustering;

    /**
     * 原始的聚类 <br>
     * key:聚类的索引 <br>
     * value:聚类
     */
    private final Map<Integer, AdvancedAreaCluster> originalClusterMap;

    /**
     * 扩展的聚类 <br>
     * key:聚类的索引 <br>
     * value:聚类
     */
    private final Map<Integer, AdvancedAreaCluster> expandedClusterMap;

    /**
     * 聚类的相邻关系 <br>
     * key:聚类的索引 <br>
     * value:与其相邻的聚类的索引
     */
    private final Map<Integer, Set<Integer>> neighborClusterIndexMap;

    /**
     * 聚类的数量
     */
    private final int clusterSize;


    /**
     * {@link RangelClustering}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     地图信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelClustering(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.originalClusterMap = new HashMap<>();
        this.expandedClusterMap = new HashMap<>();
        this.neighborClusterIndexMap = new HashMap<>();

        this.clustering = moduleManager.getModule("RangelClustering.Clustering", "adf.impl.module.algorithm.KMeansClustering");
        this.registerModule(this.clustering);

        this.clusterSize = clustering.getClusterNumber();
    }


    /**
     * 预计算时执行的方法
     * <p>
     * 计算好聚类的相邻关系({@link #neighborClusterIndexMap}),并将其存储到{@link PrecomputeData}中
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }

        this.initCluster();
        this.initNeighbor();
        for (int clusterIndex = 0; clusterIndex < this.clusterSize; clusterIndex++) {
            precomputeData.setIntegerList(KEY_NEIGHBOR + clusterIndex, new ArrayList<>(this.neighborClusterIndexMap.get(clusterIndex)));
        }

        return this;
    }


    /**
     * 预计算模式的初始化处理方法
     * <p>
     * <ul>
     *     <li>直接调用{@link #initCluster()}方法初始化原始聚类({@link #originalClusterMap})
     *     <li>使用从{@link PrecomputeData}中读取到的预计算数据初始化相邻关系({@link #neighborClusterIndexMap})
     * </ul>
     *
     * @param precomputeData 预计算数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }

        this.initCluster();
        for (int clusterIndex = 0; clusterIndex < this.clusterSize; clusterIndex++) {
            this.neighborClusterIndexMap.put(clusterIndex, new HashSet<>(precomputeData.getIntegerList(KEY_NEIGHBOR + clusterIndex)));
        }

        return this;
    }


    /**
     * 无预计算模式的初始化处理方法
     * <p>
     * <ul>
     *     <li>直接调用{@link #initCluster()}方法初始化原始聚类({@link #originalClusterMap})
     *     <li>直接调用{@link #initNeighbor()}方法初始化相邻关系({@link #neighborClusterIndexMap})
     * </ul>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }

        this.initCluster();
        this.initNeighbor();

        return this;
    }


    /**
     * 每个回合都会执行这个方法来更新agent所持有的信息
     * <p>
     * 仅重写了这个方法
     *
     * @param messageManager 消息管理器
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }


    /**
     * 计算
     * <p>
     * 仅重写了这个方法
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Clustering calc() {
        return this;
    }


    /**
     * 获得聚类的数量
     *
     * @return 聚类的数量
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public int getClusterNumber() {
        return this.clustering.getClusterNumber();
    }


    /**
     * 获得指定agent所属聚类的索引
     *
     * @param agent agent的{@link StandardEntity}
     * @return 所在聚类的索引
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public int getClusterIndex(StandardEntity agent) {
        return this.clustering.getClusterIndex(agent);
    }


    /**
     * 获得指定agent所属聚类的索引
     *
     * @param agentID agent的{@link EntityID}
     * @return 所在聚类的索引
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public int getClusterIndex(EntityID agentID) {
        return this.clustering.getClusterIndex(agentID);
    }


    /**
     * 获得指定索引的聚类中的所有区域({@link Area})的{@link StandardEntity}
     *
     * @param clusterIndex 簇的索引
     * @return 聚类中的所有区域实体的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see #getClusterEntityIDsFromExpandedCluster(int)
     */
    @Override
    public Collection<StandardEntity> getClusterEntities(int clusterIndex) {
        Collection<EntityID> areaIDs = this.getClusterEntityIDs(clusterIndex);
        if (areaIDs == null) {
            return null;
        }
        return areaIDs
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Objects::nonNull)
                .toList();
    }


    /**
     * 获得指定索引的聚类中的所有区域({@link Area})的EntityID
     *
     * @param clusterIndex 聚类的索引
     * @return 聚类中的所有区域的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @see #getClusterEntityIDsFromExpandedCluster(int)
     */
    @Override
    public Collection<EntityID> getClusterEntityIDs(int clusterIndex) {
        if (clusterIndex < 0) {
            return null;
        } else if (clusterIndex < this.clusterSize) {
            return this.clustering.getClusterEntityIDs(clusterIndex);
        } else {
            return this.getClusterEntityIDsFromExpandedCluster(clusterIndex);
        }
    }


    /**
     * 从扩展的聚类中获得指定索引的聚类中的所有区域({@link Area})的EntityID
     * <p>
     * <ul>
     *     <li>如果参数小于0, 则返回null
     *     <li>如果参数在0~{@link #clusterSize}之间, 则直接返回原始聚类({@link #originalClusterMap})中的结果
     *     <li>如果参数大于等于{@link #clusterSize}, 则从扩展的聚类({@link #expandedClusterMap})中获得结果
     *     <li>如果参数大于等于{@link #clusterSize},但是在{@link #expandedClusterMap}中没有找到, 则调用{@link #expandToAdvantageRank(int, int)}方法扩展聚类, 然后将其保存到{@link #expandedClusterMap}中,再返回结果
     * </ul>
     *
     * @param clusterIndex 聚类的索引,值=原始聚类的索引+扩展次数*聚类的数量
     * @return 聚类中的所有区域的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private Collection<EntityID> getClusterEntityIDsFromExpandedCluster(int clusterIndex) {
        if (this.expandedClusterMap.containsKey(clusterIndex)) {
            return this.expandedClusterMap.get(clusterIndex).getMemberIDs();
        } else {
            //原始聚类的索引
            int originalClusterIndex = clusterIndex % this.clusterSize;
            //目标阶级
            int targetRank = (clusterIndex / this.clusterSize) + 1;
            //对聚类进行扩展
            AdvancedAreaCluster expandedCluster = this.expandToAdvantageRank(originalClusterIndex, targetRank);
            this.expandedClusterMap.put(clusterIndex, expandedCluster);
            return expandedCluster.getMemberIDs();
        }
    }


    /**
     * 初始化原始聚类({@link #originalClusterMap})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void initCluster() {
        StandardEntityURN agentURN = agentInfo.me().getStandardURN();
        this.worldInfo.getEntityIDsOfType(agentURN)
                .stream()
                .map(this.clustering::getClusterIndex)
                .forEach(clusterIndex -> {
                    List<Area> areas = this.clustering.getClusterEntityIDs(clusterIndex)
                            .stream()
                            .map(this.worldInfo::getEntity)
                            .filter(Objects::nonNull)
                            .map(Area.class::cast)
                            .toList();
                    this.originalClusterMap.put(clusterIndex, new AdvancedAreaCluster(areas));
                });
    }


    /**
     * 初始化聚类的相邻关系({@link #neighborClusterIndexMap})
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void initNeighbor() {
        Map<Integer, Shape> clusterShapeMap = new HashMap<>();
        for (Map.Entry<Integer, AdvancedAreaCluster> entry : this.originalClusterMap.entrySet()) {
            Shape areaShape = this.gatherAreaShape(entry.getValue().getMembers());
            clusterShapeMap.put(entry.getKey(), areaShape);
        }
        for (int i = 0; i < this.clusterSize - 1; i++) {
            for (int j = i + 1; j < this.clusterSize; j++) {
                Shape shapeA = clusterShapeMap.get(i);
                Shape shapeB = clusterShapeMap.get(j);
                //对两个几何图形进行略微放大,以便于判断是否相交
                java.awt.geom.Area geomAreaA = this.scaleGeomArea(new java.awt.geom.Area(shapeA));
                java.awt.geom.Area geomAreaB = this.scaleGeomArea(new java.awt.geom.Area(shapeB));
                //如果两个几何图形相交,则认为这两个聚类是相邻的
                geomAreaA.intersect(geomAreaB);
                if (!geomAreaA.isEmpty()) {
                    this.neighborClusterIndexMap.computeIfAbsent(i, k -> new HashSet<>()).add(j);
                    this.neighborClusterIndexMap.computeIfAbsent(j, k -> new HashSet<>()).add(i);
                }
            }
        }
    }


    /**
     * 将多个区域({@link Area})的形状({@link Area#getShape()})合并成一个形状({@link Shape})
     *
     * @param areas 区域({@link Area})的集合
     * @return 几何形状
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */

    @Contract("_ -> new")
    private @NotNull Shape gatherAreaShape(@NotNull Collection<Area> areas) {
        //几何工厂
        GeometryFactory geometryFactory = new GeometryFactory();
        //创建一个空的几何对象
        Geometry geometry = geometryFactory.createPolygon();
        for (Area area : areas) {
            //区域的顶点集
            int[] apexList = area.getApexList();
            //通过顶点集生成坐标集
            Coordinate[] coordinates = new Coordinate[apexList.length / 2];
            for (int i = 0; i < apexList.length; i += 2) {
                coordinates[i / 2] = new Coordinate(apexList[i], apexList[i + 1]);
            }
            //通过坐标集和几何工厂生成几何体
            ConvexHull convexHull = new ConvexHull(coordinates, geometryFactory);
            //将几何体连接起来
            geometry = geometry.union(convexHull.getConvexHull());
        }

        //获得几何体的凸包
        geometry = geometry.convexHull();
        //获得凸包的坐标集
        Coordinate[] coordinates = geometry.getCoordinates();
        int nPoints = coordinates.length;              //坐标的数量
        int[] xPoints = new int[coordinates.length];   //x轴坐标集
        int[] yPoints = new int[coordinates.length];   //y轴坐标集
        for (int i = 0; i < coordinates.length; i++) {
            xPoints[i] = (int) coordinates[i].x;
            yPoints[i] = (int) coordinates[i].y;
        }
        //通过坐标集生成多边形
        return new Polygon(xPoints, yPoints, nPoints);
    }


    /**
     * 对集合区域({@link java.awt.geom.Area}进行缩放
     *
     * @param geomArea 想要缩放的几何区域
     * @return 缩放后的几何区域
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Contract("_ -> param1")
    private @NotNull java.awt.geom.Area scaleGeomArea(@NotNull java.awt.geom.Area geomArea) {
        AffineTransform transform = new AffineTransform();
        Rectangle rectangle = geomArea.getBounds();
        double centerX = rectangle.getCenterX();
        double centerY = rectangle.getCenterY();
        transform.translate(centerX, centerY);
        transform.scale(1.1, 1.1);
        transform.translate(-centerX, -centerY);
        geomArea.transform(transform);
        return geomArea;
    }


    /**
     * 扩展聚类到指定的阶级
     *
     * @param originalClusterIndex 原始聚类的索引
     * @param targetRank           目标阶级
     * @return 扩展完毕的聚类
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @NotNull
    private AdvancedAreaCluster expandToAdvantageRank(int originalClusterIndex, int targetRank) {
        List<Integer> originalClusterIndexList = new ArrayList<>(this.originalClusterMap.keySet());
        Queue<Integer> allNeighborClusterIndexQueue = new ArrayDeque<>();
        originalClusterIndexList.remove(originalClusterIndex);
        allNeighborClusterIndexQueue.add(originalClusterIndex);
        AdvancedAreaCluster expandedCluster = new AdvancedAreaCluster(this.originalClusterMap.get(originalClusterIndex));

        while (expandedCluster.getRank() < targetRank) {
            if (allNeighborClusterIndexQueue.isEmpty()) {
                break;
            }
            int neighborClusterIndex = allNeighborClusterIndexQueue.poll();
            AdvancedAreaCluster originalCluster = this.originalClusterMap.get(neighborClusterIndex);
            List<Integer> neighborClusterIndexList = originalClusterIndexList.stream()
                    .filter(this.neighborClusterIndexMap.get(neighborClusterIndex)::contains)
                    .sorted(Comparator.comparing(clusterIndex -> {
                        Point centroid1 = originalCluster.getCentroid();
                        Point centroid2 = this.originalClusterMap.get(clusterIndex).getCentroid();
                        return Math.hypot(centroid1.getX() - centroid2.getX(), centroid1.getY() - centroid2.getY());
                    }))
                    .limit(targetRank - expandedCluster.getRank())
                    .toList();
            expandedCluster = expandedCluster.expand(neighborClusterIndexList.stream().map(this.originalClusterMap::get).toList());
            originalClusterIndexList.removeAll(neighborClusterIndexList);
            allNeighborClusterIndexQueue.addAll(neighborClusterIndexList);
        }
        return expandedCluster;
    }

}
