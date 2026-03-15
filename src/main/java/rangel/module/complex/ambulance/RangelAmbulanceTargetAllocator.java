package rangel.module.complex.ambulance;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.complex.AmbulanceTargetAllocator;
import org.jetbrains.annotations.NotNull;
import rangel.module.communication.RangelMessage;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * 救护队的目标分配器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelAmbulanceTargetAllocator extends AmbulanceTargetAllocator {

    private final List<MessageAmbulanceTeam> receivedMessages;


    /**
     * {@link RangelAmbulanceTargetAllocator}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelAmbulanceTargetAllocator(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.receivedMessages = new ArrayList<>();
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
    public AmbulanceTargetAllocator resume(PrecomputeData precomputeData) {
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
    public AmbulanceTargetAllocator preparate() {
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
    public AmbulanceTargetAllocator updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }

        this.receiveMessage(messageManager);
        this.sendMessage(messageManager);

        return this;
    }


    /**
     * 计算
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public AmbulanceTargetAllocator calc() {
        return this;
    }


    /**
     * 获得计算结果
     *
     * @return 计算结果
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Map<EntityID, EntityID> getResult() {
        return new HashMap<>();
    }


    /**
     * 接收消息
     * <p>
     * 仅接收来自救护队({@link MessageAmbulanceTeam})并且为寻找最佳避难所的消息
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void receiveMessage(@NotNull MessageManager messageManager) {
        messageManager.getReceivedMessageList(MessageAmbulanceTeam.class)
                .stream()
                .map(MessageAmbulanceTeam.class::cast)
                .filter(message -> message.getAction() == RangelMessage.BEST_REFUGE)
                .forEach(this.receivedMessages::add);
    }


    /**
     * 发送消息
     * <p>
     * 如果agent的目标避难所的等待人数过多,则调用{@link #getBestRefugeID(EntityID)}方法为其寻找最佳避难所,并发送消息
     *
     * @param messageManager 消息管理器
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private void sendMessage(MessageManager messageManager) {
        for (MessageAmbulanceTeam messageAmbulanceTeam : this.receivedMessages) {
            EntityID agentID = messageAmbulanceTeam.getAgentID();
            EntityID refugeID = messageAmbulanceTeam.getTargetID();
            assert refugeID != null;
            AmbulanceTeam agent = (AmbulanceTeam) this.worldInfo.getEntity(agentID);
            Refuge refuge = (Refuge) this.worldInfo.getEntity(refugeID);
            assert agent != null;
            assert refuge != null;
            if (refuge.getWaitingListSize() > 3) {
                EntityID bestRefugeID = this.getBestRefugeID(agentID);
                if (bestRefugeID != null && !bestRefugeID.equals(refugeID)) {
                    System.out.println(this.agentInfo.getTime() + ":" + this.agentInfo.getID() + "你的最佳避难所是" + bestRefugeID);
                    messageManager.addMessage(new MessageAmbulanceTeam(true, agent, RangelMessage.BEST_REFUGE, bestRefugeID));
                }
            }
        }
    }


    /**
     * 获得最佳避难所
     *
     * @param agentID 想要前往避难所的人
     * @return 最佳避难所的EntityID
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    private EntityID getBestRefugeID(EntityID agentID) {
        return this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE)
                .stream()
                .map(this.worldInfo::getEntity)
                .filter(Refuge.class::isInstance)
                .map(Refuge.class::cast)
                .max(Comparator.comparing(refuge -> {
                    int availableBedNumber = refuge.getCapacity() - refuge.getOccupiedBeds();
                    double distance = 7000.0 / this.worldInfo.getDistance(agentID, refuge.getID());
                    return availableBedNumber + distance;
                }))
                .map(Refuge::getID)
                .orElse(null);
    }
}
