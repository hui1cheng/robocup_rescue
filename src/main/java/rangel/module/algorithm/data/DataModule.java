package rangel.module.algorithm.data;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.AbstractModule;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

/**
 * 抽象的数据算法模块
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public abstract class DataModule extends AbstractModule {

    public DataModule(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
    }


    /**
     * 获得进行判断的布尔值
     *
     * @param entityID 要判断的实体的EntityID
     * @return 判断结果, 为一个布尔值
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public abstract boolean getBoolean(EntityID entityID);


    /**
     * 获得计算出的数据
     *
     * @return 实体的EntityID的集合
     * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
     */
    public abstract Collection<EntityID> getData();


    @Override
    public DataModule precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }


    @Override
    public DataModule resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }


    @Override
    public DataModule preparate() {
        super.preparate();
        return this;
    }


    @Override
    public DataModule updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        return this;
    }


    @Override
    public abstract DataModule calc();

}
