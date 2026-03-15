package rangel.utils;

import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.*;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Edge;

import java.awt.*;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 几何工具类
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class GeometryUtils {

    /**
     * 人的体型半径
     */
    private static final double HUMAN_RADIUS = ConfigUtils.getDouble("humanRadius", 500.0);

    /**
     * 几何体工厂
     */
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();


    /**
     * 获取区域内的可通行区域
     *
     * @param area      区域
     * @param blockades 区域内的障碍物
     * @return 可通行区域的几何体
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public static Geometry getPassableGeometryInArea(@NotNull Area area, @NotNull Collection<Blockade> blockades) {
        //区域的几何体
        Geometry areaGeometry = GeometryUtils.convertShapeToGeometry(area.getShape());

        //所有障碍物构成的几何体
        Geometry blockadeGeometry = blockades
                .stream()
                .map(Blockade::getShape)
                .map(GeometryUtils::convertShapeToGeometry)
                .map(geometry -> geometry.buffer(HUMAN_RADIUS))
                .reduce(Geometry::union)
                .orElse(GEOMETRY_FACTORY.createPolygon());

        //区域不可通过的边构成的几何体
        Geometry impassableEdgeGeometry = area.getEdges()
                .stream()
                .filter(edge -> !edge.isPassable())
                .map(Edge::getLine)
                .map(GeometryUtils::convertLineToGeometry)
                .map(geometry -> geometry.buffer(HUMAN_RADIUS))
                .reduce(Geometry::union)
                .orElse(GEOMETRY_FACTORY.createPolygon());

        //区域几何体减去所有障碍物构成的几何体，再减去区域不可通过的边构成的几何体，得到区域可通过的几何体
        return areaGeometry
                .difference(blockadeGeometry)
                .difference(impassableEdgeGeometry)
                .norm()
                .buffer(1.0);
    }


    /**
     * 判断是否可从from经passableGeometry到达next
     *
     * @param passableGeometry 可通行区域的几何体
     * @param from             起点的{@link Line2D}
     * @param next             下一个点的{@link Line2D}
     * @return true:可到达，false:不可到达
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public static boolean isPassable(Geometry passableGeometry, Line2D from, Line2D next) {
        Geometry lineGeometry1 = convertLineToGeometry(from);
        Geometry lineGeometry2 = convertLineToGeometry(next);

        List<Geometry> geometries = new ArrayList<>();
        if (passableGeometry instanceof GeometryCollection geometryCollection) {
            for (int i = 0; i < geometryCollection.getNumGeometries(); ++i) {
                geometries.add(geometryCollection.getGeometryN(i));
            }
        } else {
            geometries.add(passableGeometry);
        }

        for (Geometry geometry : geometries) {
            if (geometry.intersects(lineGeometry1) && geometry.intersects(lineGeometry2)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 将Shape转换为Geometry
     *
     * @param shape java.awt.Shape
     * @return org.locationtech.jts.geomGeometry
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private static Geometry convertShapeToGeometry(Shape shape) {
        Polygon polygon = (Polygon) shape;
        //获取该多边形的所有顶点坐标
        Coordinate[] coordinates = new Coordinate[polygon.npoints + 1];
        for (int i = 0; i < polygon.npoints; ++i) {
            coordinates[i] = new Coordinate(polygon.xpoints[i], polygon.ypoints[i]);
        }
        coordinates[polygon.npoints] = coordinates[0];
        //以此多边形的所有顶点为坐标点，构造一个几何体
        return GEOMETRY_FACTORY.createPolygon(coordinates);
    }


    /**
     * 将Line2D转换为Geometry
     *
     * @param line rescuecore2.misc.geometry.Line2D
     * @return org.locationtech.jts.geomGeometry
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private static Geometry convertLineToGeometry(@NotNull Line2D line) {
        LineString lineString = GEOMETRY_FACTORY.createLineString(
                new Coordinate[]{
                        new Coordinate(line.getOrigin().getX(), line.getOrigin().getY()),
                        new Coordinate(line.getEndPoint().getX(), line.getEndPoint().getY())
                }
        );
        return lineString.getLength() > 0.0 ? lineString : lineString.getPointN(0);
    }

}