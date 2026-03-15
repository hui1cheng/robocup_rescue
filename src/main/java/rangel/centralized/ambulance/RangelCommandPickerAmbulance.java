package rangel.centralized.ambulance;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.centralized.CommandPicker;
import adf.core.component.communication.CommunicationMessage;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 救护队的命令选择器
 * <p>
 * 调用流程:{@link #setAllocatorResult(Map)} -> {@link #calc()} ->  {@link #getResult()}
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelCommandPickerAmbulance extends CommandPicker {

    /**
     * 想要分配的数据
     */
    private final Map<EntityID, EntityID> allocationData;

    /**
     * 分配完毕的命令的集合
     */
    private final Collection<CommunicationMessage> messages;


    /**
     * {@link RangelCommandPickerAmbulance}的构造函数
     *
     * @param agentInfo     代理信息
     * @param worldInfo     世界信息
     * @param scenarioInfo  场景信息
     * @param moduleManager 模块管理器
     * @param developData   开发数据
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public RangelCommandPickerAmbulance(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);

        this.allocationData = new HashMap<>();
        this.messages = new ArrayList<>();
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
    public CommandPicker precompute(PrecomputeData precomputeData) {
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
    public CommandPicker resume(PrecomputeData precomputeData) {
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
    public CommandPicker preparate() {
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
    public CommandPicker updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }


    /**
     * 为命令选择器设置想要分配的数据
     *
     * @param allocationData 想要分配的数据
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandPicker setAllocatorResult(Map<EntityID, EntityID> allocationData) {
        this.allocationData.clear();
        this.allocationData.putAll(allocationData);
        return this;
    }


    /**
     * 计算数据,分配好命令,并添加到{@link #messages}中
     * <p>
     * 根据每条数据agent的目标类型,分配不同的命令:
     * <ul>
     *     <li>如果目标是人类({@link Human}),则让agent自主行动
     *     <li>如果目标是区域({@link Area}),则让agent去侦查
     * </ul>
     *
     * @return this
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public CommandPicker calc() {
        this.messages.clear();

        for (EntityID agentID : this.allocationData.keySet()) {
            EntityID targetID = this.allocationData.get(agentID);
            StandardMessage command = new CommandAmbulance(true, agentID, targetID, CommandAmbulance.ACTION_AUTONOMY);
            this.messages.add(command);
        }
        return this;
    }


    /**
     * 获得分配的结果
     *
     * @return 分配完毕的命令的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    @Override
    public Collection<CommunicationMessage> getResult() {
        return this.messages;
    }

}