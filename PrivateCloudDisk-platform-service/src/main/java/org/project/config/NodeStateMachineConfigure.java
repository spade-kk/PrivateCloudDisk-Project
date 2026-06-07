package org.project.config;

import org.project.model.entity.FolderNodeEntity;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import java.util.EnumSet;

@Configuration
@EnableStateMachine(name = "nodeStateMachine")
public class NodeStateMachineConfigure extends EnumStateMachineConfigurerAdapter<FolderNodeEntity.NodeStatus, FolderNodeEntity.NodeEvent> {
    @Override
    public void configure(StateMachineStateConfigurer<FolderNodeEntity.NodeStatus, FolderNodeEntity.NodeEvent> statesConfigurer) throws Exception {
        statesConfigurer.withStates()
                .initial(FolderNodeEntity.NodeStatus.pending)
                .states(EnumSet.allOf(FolderNodeEntity.NodeStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<FolderNodeEntity.NodeStatus, FolderNodeEntity.NodeEvent> transitionsConfigurer) throws Exception {
        transitionsConfigurer.withExternal()
                    .source(FolderNodeEntity.NodeStatus.pending)
                    .target(FolderNodeEntity.NodeStatus.active)
                    .event(FolderNodeEntity.NodeEvent.Active)
                .and()
                .withExternal()
                    .source(FolderNodeEntity.NodeStatus.active)
                    .target(FolderNodeEntity.NodeStatus.lock)
                    .event(FolderNodeEntity.NodeEvent.Lock)
                .and()
                .withExternal()
                    .source(FolderNodeEntity.NodeStatus.lock)
                    .target(FolderNodeEntity.NodeStatus.active)
                    .event(FolderNodeEntity.NodeEvent.Unlock);
    }
}
