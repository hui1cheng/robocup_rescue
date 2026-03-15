package rangel.module.communication;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.ChannelSubscriber;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.stream.IntStream;

/**
 * 频道订阅者
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelChannelSubscriber extends ChannelSubscriber {

    /**
     * 订阅频道
     *
     * @param agentInfo      代理信息
     * @param worldInfo      世界信息
     * @param scenarioInfo   场景信息
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public void subscribe(@NotNull AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        // 只订阅一次
        if (agentInfo.getTime() == 1) {
            //获得代理类型
            StandardEntityURN agentType = agentInfo.me().getStandardURN();
            //计算所有的频道号
            int[] channels = getChannelsByAgentType(agentType, scenarioInfo);
            //订阅频道
            messageManager.subscribeToChannels(channels);
        }
    }


    /**
     * 根据智能体类型获得所有频道号
     *
     * @param agentType    代理类型
     * @param scenarioInfo 场景信息
     * @return 频道号数组
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public static int[] getChannelsByAgentType(@NotNull StandardEntityURN agentType, @NotNull ScenarioInfo scenarioInfo) {
        //第0个频道是语音频道
        int numChannels = scenarioInfo.getCommsChannelsCount() - 1;
        //最大频道数
        int maxChannelCount;
        switch (agentType) {
            case FIRE_BRIGADE, POLICE_FORCE, AMBULANCE_TEAM ->
                    maxChannelCount = scenarioInfo.getCommsChannelsMaxPlatoon();
            case FIRE_STATION, POLICE_OFFICE, AMBULANCE_CENTRE ->
                    maxChannelCount = scenarioInfo.getCommsChannelsMaxOffice();
            default -> maxChannelCount = 0;
        }
        //计算所有的频道号
        return IntStream.range(0, maxChannelCount)
                .map(channelIndex -> RangelChannelSubscriber.assignChannel(agentType, channelIndex, numChannels))
                .toArray();
    }


    /**
     * 为智能体分配频道号
     *
     * @param agentType    代理类型
     * @param channelIndex 频道索引
     * @param numChannels  频道总数
     * @return 分配的频道号
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Contract(pure = true)
    public static int assignChannel(@NotNull StandardEntityURN agentType, int channelIndex, int numChannels) {
        int agentIndex;
        switch (agentType) {
            //使得各类中心代理与其对应的排级代理(platoon agent)分配至同一频道
            case FIRE_BRIGADE, FIRE_STATION -> agentIndex = 0;
            case POLICE_FORCE, POLICE_OFFICE -> agentIndex = 1;
            case AMBULANCE_TEAM, AMBULANCE_CENTRE -> agentIndex = 2;
            default -> agentIndex = -1;
        }

        //根据代理类型的不同分配不同的频道号
        int index = (3 * channelIndex) + agentIndex;
        if ((index % numChannels) == 0) {
            index = numChannels;
        } else {
            index = index % numChannels;
        }
        return index;
    }

    /**
     * 频道订阅测试
     * @author <a href="https://roozen.top">Roozen</a>
     */
    public static void main(String[] args) {
        int numChannels = 10; // 总信道数，包含语音信道
        int platoonMaxChannels = 3;
        int centerMaxChannels = 3;
        for (int i = 0; i < platoonMaxChannels; i++) {
            System.out.println("FIRE_BRIGADE-" + i + ":" + assignChannel(StandardEntityURN.FIRE_BRIGADE, i, numChannels));
        }
        for (int i = 0; i < platoonMaxChannels; i++) {
            System.out.println("POLICE_FORCE-" + i + ":" + assignChannel(StandardEntityURN.POLICE_FORCE, i, numChannels));
        }
        for (int i = 0; i < platoonMaxChannels; i++) {
            System.out.println("AMBULANCE_TEAM-" + i + ":" + assignChannel(StandardEntityURN.AMBULANCE_TEAM, i, numChannels));
        }
        for (int i = 0; i < centerMaxChannels; i++) {
            System.out.println("FIRE_STATION-" + i + ":" + assignChannel(StandardEntityURN.FIRE_STATION, i, numChannels));
        }
        for (int i = 0; i < centerMaxChannels; i++) {
            System.out.println("POLICE_OFFICE-" + i + ":" + assignChannel(StandardEntityURN.POLICE_OFFICE, i, numChannels));
        }
        for (int i = 0; i < centerMaxChannels; i++) {
            System.out.println("AMBULANCE_CENTRE-" + i + ":" + assignChannel(StandardEntityURN.AMBULANCE_CENTRE, i, numChannels));
        }
    }
}
