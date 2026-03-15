package rangel.module.algorithm.cluster;

import org.jetbrains.annotations.NotNull;
import rangel.utils.ConfigUtils;
import rescuecore2.standard.entities.Area;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.util.Comparator.comparing;

/**
 * KMeans++算法
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class KMeansPlusAlgorithm {

    private static final long SEED = ConfigUtils.getInteger("clustering.seed", 114514);


    /**
     * 使用KMeans++算法计算聚类
     * <p>
     * <ol>
     *     <li>随机选择一个点作为第一个聚类中心
     *     <li>对于每个点,计算其与最近聚类中心的距离,距离越大,被选中的概率越大
     *     <li>使用轮盘赌算法,选择一个点作为下一个聚类中心
     *     <li>重复2-3步,直到聚类中心数量达到指定数量
     *     <li>对于每个点,计算其与每个聚类中心的距离,将其归入距离最近的聚类
     *     <li>重新确定聚类中心,重复步骤5,直至达到指定的迭代次数
     * </ol>
     *
     * @param repeat 重复计算次数
     * @param areas  想要划分的区域
     * @param k      期望划分的聚类数
     * @return 区域聚类的列表
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @NotNull
    public static List<AreaCluster> calcCluster(int repeat, @NotNull List<Area> areas, int k) {
        int size = areas.size();
        List<AreaCluster> result = new ArrayList<>(size);
        for (int i = 0; i < k; i++) {
            result.add(new AreaCluster());
        }

        //从输入的样本集合中随机选择一个样本作为第一个聚类中心
        Random random = new Random(SEED);
        int r = random.nextInt(size);
        Area randomArea = areas.get(r);
        result.get(0).add(randomArea);
        result.get(0).updateCentroid();

        boolean[] assigned = new boolean[size];
        assigned[r] = true;
        for (int i = 0; i < k - 1; ++i) {
            //计算数据中每个样本点到已有聚类中心的最短距离
            double[] distances = new double[size];
            double sumDistance = 0.0;
            for (int j = 0; j < size; j++) {
                if (assigned[j]) {
                    continue;
                }
                Point centroid = result.get(i).getCentroid();
                double centroidX = centroid.getX();
                double centroidY = centroid.getY();
                Area area = areas.get(j);
                double x = area.getX();
                double y = area.getY();
                distances[j] = Math.hypot(x - centroidX, y - centroidY);
                sumDistance += distances[j];
            }

            for (int j = 0; j < size; ++j) {
                distances[j] /= sumDistance;
            }

            //使用轮盘赌算法选择下一个聚类中心
            double percentage = random.nextDouble();
            double acceptDistance = 0.0;
            for (int j = 0; j < size; ++j) {
                if (assigned[j]) {
                    continue;
                }
                acceptDistance += distances[j];
                if (acceptDistance >= percentage) {
                    result.get(i + 1).add(areas.get(j));
                    result.get(i + 1).updateCentroid();
                    assigned[j] = true;
                    break;
                }
            }
        }

        //开始迭代,重新确定聚类中心
        for (int i = 0; i < repeat; ++i) {
            result.forEach(AreaCluster::clear);
            for (Area area : areas) {
                result.stream()
                        .min(comparing(cluster -> {
                            Point centroid = cluster.getCentroid();
                            double centroidX = centroid.getX();
                            double centroidY = centroid.getY();
                            double x = area.getX();
                            double y = area.getY();
                            return Math.hypot(x - centroidX, y - centroidY);
                        }))
                        .ifPresent(cluster -> cluster.add(area));
            }
            result.forEach(AreaCluster::updateCentroid);
        }

        return result;
    }

}
