package rangel.module.communication;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.communication.standard.bundle.StandardMessagePriority;
import adf.core.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.core.agent.communication.standard.bundle.centralized.MessageReport;
import adf.core.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.core.agent.communication.standard.bundle.information.MessageCivilian;
import adf.core.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.core.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.MessageCoordinator;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.IntStream;

import static adf.core.agent.communication.standard.bundle.StandardMessagePriority.*;
import static java.util.Comparator.comparingInt;
import static rangel.module.communication.RangelMessage.HELP_CLEAR;
import static rangel.module.communication.RangelMessage.HELP_RESCUE;
import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 消息协调者
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 * @author <a href="https://roozen.top">Roozen</a>
 */
public class RangelMessageCoordinator extends MessageCoordinator {

    /**
     * 协调发送消息至频道
     *
     * @param agentInfo              代理信息
     * @param worldInfo              世界信息
     * @param scenarioInfo           场景信息
     * @param messageManager         消息管理器
     * @param sendMessageList        待发送消息列表
     * @param channelSendMessageList 已经分配到频道发送消息的列表
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    @Override
    public void coordinate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo,
                           MessageManager messageManager, @NotNull ArrayList<CommunicationMessage> sendMessageList,
                           List<List<CommunicationMessage>> channelSendMessageList) {
        List<StandardMessage> standardMessages = sendMessageList
                .stream()
                //从CommunicationMessage对象列表中过滤出StandardMessage对象
                .filter(StandardMessage.class::isInstance)
                //将CommunicationMessage对象强制类型转换为StandardMessage对象
                .map(StandardMessage.class::cast)
                .toList();

        List<MessageChannel> messageChannelList = Arrays.asList(
                new MessageChannel(ChannelType.VOICE_CHANNEL, new int[]{0}),
                new MessageChannel(ChannelType.POLICE_FORCE_CHANNEL,
                        RangelChannelSubscriber.getChannelsByAgentType(POLICE_FORCE, scenarioInfo)),
                new MessageChannel(ChannelType.FIRE_BRIGADE_CHANNEL,
                        RangelChannelSubscriber.getChannelsByAgentType(FIRE_BRIGADE, scenarioInfo)),
                new MessageChannel(ChannelType.AMBULANCE_TEAM_CHANNEL,
                        RangelChannelSubscriber.getChannelsByAgentType(AMBULANCE_TEAM, scenarioInfo))

        );

        for (StandardMessage message : standardMessages) {
            //消息是否是无线电消息
            if (message.isRadio()) {
                if (message instanceof CommandPolice) {
                    messageChannelList
                            .stream()
                            .filter(messageChannel -> messageChannel.getType() == ChannelType.POLICE_FORCE_CHANNEL)
                            .forEach(messageChannel -> messageChannel.addMessage(message));
                } else if (message instanceof MessageReport) {
                    messageChannelList
                            .stream()
                            .filter(messageChannel -> messageChannel.getType() == ChannelType.POLICE_FORCE_CHANNEL)
                            .forEach(messageChannel -> messageChannel.addMessage(message));
                } else if (message instanceof MessageAmbulanceTeam messageAmbulanceTeam) {
                    int action = messageAmbulanceTeam.getAction();
                    if (action == HELP_CLEAR) {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() == ChannelType.POLICE_FORCE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    } else if (action == HELP_RESCUE) {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() == ChannelType.FIRE_BRIGADE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    } else {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() != ChannelType.VOICE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    }
                } else if (message instanceof MessageCivilian) {
                    messageChannelList
                            .stream()
                            .filter(messageChannel -> messageChannel.getType() != ChannelType.VOICE_CHANNEL)
                            .forEach(messageChannel -> messageChannel.addMessage(message));
                } else if (message instanceof MessageFireBrigade messageFireBrigade) {
                    int action = messageFireBrigade.getAction();
                    if (action == HELP_CLEAR) {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() == ChannelType.POLICE_FORCE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    } else if (action == HELP_RESCUE) {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() == ChannelType.FIRE_BRIGADE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    } else {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() != ChannelType.VOICE_CHANNEL)
                                .filter(messageChannel -> messageChannel.getType() != ChannelType.AMBULANCE_TEAM_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    }
                } else if (message instanceof MessagePoliceForce messagePoliceForce) {
                    int action = messagePoliceForce.getAction();
                    if (action == HELP_CLEAR) {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() == ChannelType.POLICE_FORCE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    } else if (action == HELP_RESCUE) {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() == ChannelType.FIRE_BRIGADE_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    } else {
                        messageChannelList
                                .stream()
                                .filter(messageChannel -> messageChannel.getType() != ChannelType.VOICE_CHANNEL)
                                .filter(messageChannel -> messageChannel.getType() != ChannelType.AMBULANCE_TEAM_CHANNEL)
                                .forEach(messageChannel -> messageChannel.addMessage(message));
                    }
                }
            } else {
                if (message instanceof MessageCivilian
                        || message instanceof MessageAmbulanceTeam
                        || message instanceof MessageFireBrigade
                        || message instanceof MessagePoliceForce) {
                    messageChannelList.get(0).addMessage(message);
                }
            }
        }
        messageChannelList.forEach(MessageChannel::sort);
        messageChannelList.forEach(messageChannel -> assignMessageToChannel(messageChannel, scenarioInfo, channelSendMessageList, HIGH));
        messageChannelList.forEach(messageChannel -> assignMessageToChannel(messageChannel, scenarioInfo, channelSendMessageList, NORMAL));
        messageChannelList.forEach(messageChannel -> assignMessageToChannel(messageChannel, scenarioInfo, channelSendMessageList, LOW));
    }


    /**
     * 将消息分配到合适的频道
     *
     * @param messageChannel         消息频道
     * @param scenarioInfo           场景信息
     * @param channelSendMessageList 已经分配到频道发送消息的列表
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     * @author <a href="https://roozen.top">Roozen</a>
     */
    private static void assignMessageToChannel(
            @NotNull MessageChannel messageChannel, ScenarioInfo scenarioInfo,
            List<List<CommunicationMessage>> channelSendMessageList,
            StandardMessagePriority standardMessagePriority) {
        int[] channels = messageChannel.getChannels();
        int[] bandwidths = IntStream.of(channels)
                .map(index -> {
                    if (index == 0) {
                        return scenarioInfo.getVoiceMessagesSize();
                    } else {
                        return scenarioInfo.getCommsChannelBandwidth(index);
                    }
                })
                .toArray();

        for (StandardMessage message : messageChannel.getMessages()) {
            if (!message.getSendingPriority().equals(standardMessagePriority)) {
                continue;
            }
            int bytes = message.getByteArraySize();
            int index = IntStream.range(0, channels.length)
                    .boxed()
                    .max(comparingInt(i -> bandwidths[i]))
                    .orElse(-1);
            if (index < 0 || bandwidths[index] < bytes) {
                break;
            }

            bandwidths[index] -= bytes;
            channelSendMessageList.get(channels[index]).add(message);
        }
    }


    /**
     * 消息频道
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private static class MessageChannel {

        /**
         * 消息频道的类型
         */
        private final ChannelType type;

        /**
         * 所有频道通道的列表
         */
        private final int[] channels;

        /**
         * 频道内的消息列表
         */
        private final List<StandardMessage> messages;


        /**
         * {@link MessageChannel}的构造函数
         *
         * @param type     消息频道类型
         * @param channels 所有频道通道的列表
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public MessageChannel(ChannelType type, int[] channels) {
            this.type = type;
            this.channels = channels;
            this.messages = new ArrayList<>();
        }


        /**
         * 向频道内添加消息
         *
         * @param message 消息
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public void addMessage(StandardMessage message) {
            this.messages.add(message);
        }


        /**
         * 对频道内的消息进行排序
         *
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public void sort() {
            this.messages.sort(Comparator.comparing(StandardMessage::getSendingPriority).reversed());
        }


        /**
         * 获取消息频道的类型
         *
         * @return 消息频道的类型
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public ChannelType getType() {
            return type;
        }


        /**
         * 获取所有频道通道的列表
         *
         * @return 所有频道通道的列表
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public int[] getChannels() {
            return channels;
        }


        /**
         * 获取频道内的所有消息
         *
         * @return 频道内的消息列表
         * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
         */
        public List<StandardMessage> getMessages() {
            return messages;
        }
    }


    /**
     * 消息频道的类型
     *
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    protected enum ChannelType {

        /**
         * 语音频道
         */
        VOICE_CHANNEL,

        /**
         * 救护频道
         */
        AMBULANCE_TEAM_CHANNEL,

        /**
         * 消防频道
         */
        FIRE_BRIGADE_CHANNEL,

        /**
         * 警察频道
         */
        POLICE_FORCE_CHANNEL,
    }
}
