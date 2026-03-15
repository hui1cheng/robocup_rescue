package rangel.module.complex.fire;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.module.complex.FireTargetAllocator;
import rescuecore2.worldmodel.EntityID;

import java.util.HashMap;
import java.util.Map;
/**
 * 消防队的目标分配器
 *
 * @author <a href="mailto:me@kinnrai.com">Kinnrai</a>
 */
public class RangelFireTargetAllocator extends FireTargetAllocator {

    public RangelFireTargetAllocator(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
    }


    @Override
    public FireTargetAllocator calc() {
        return this;
    }


    @Override
    public Map<EntityID, EntityID> getResult() {
        return new HashMap<>();
    }

}