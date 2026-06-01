package org.project.config;

import org.project.data.FolderNodeData;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import java.util.EnumSet;

@Configuration
@EnableStateMachine(name = "nodeStateMachine")
public class NodeStateMachineConfigure extends EnumStateMachineConfigurerAdapter<FolderNodeData.NodeStatus, FolderNodeData.NodeEvent> {
    @Override
    public void configure(StateMachineStateConfigurer<FolderNodeData.NodeStatus, FolderNodeData.NodeEvent> statesConfigurer) throws Exception {
        statesConfigurer.withStates()
                .initial(FolderNodeData.NodeStatus.pending)
                .states(EnumSet.allOf(FolderNodeData.NodeStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<FolderNodeData.NodeStatus, FolderNodeData.NodeEvent> transitionsConfigurer) throws Exception {
        transitionsConfigurer.withExternal()
                    .source(FolderNodeData.NodeStatus.pending)
                    .target(FolderNodeData.NodeStatus.active)
                    .event(FolderNodeData.NodeEvent.Active)
                .and()
                .withExternal()
                    .source(FolderNodeData.NodeStatus.active)
                    .target(FolderNodeData.NodeStatus.lock)
                    .event(FolderNodeData.NodeEvent.Lock)
                .and()
                .withExternal()
                    .source(FolderNodeData.NodeStatus.lock)
                    .target(FolderNodeData.NodeStatus.active)
                    .event(FolderNodeData.NodeEvent.Unlock);
    }
}
