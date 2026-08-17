package org.project.config;

import org.project.model.entity.UploadsSessionEntity;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachine(name = "uploadsStateMachine")
public class UploadsStateMachineConfigure extends EnumStateMachineConfigurerAdapter<UploadsSessionEntity.UploadsSessionStatus, UploadsSessionEntity.UploadsSessionEvent> {
    @Override
    public void configure(StateMachineStateConfigurer<UploadsSessionEntity.UploadsSessionStatus, UploadsSessionEntity.UploadsSessionEvent> statesConfigurer) throws Exception {
        statesConfigurer.withStates()
                .initial(UploadsSessionEntity.UploadsSessionStatus.uploading)
                .states(EnumSet.allOf(UploadsSessionEntity.UploadsSessionStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<UploadsSessionEntity.UploadsSessionStatus, UploadsSessionEntity.UploadsSessionEvent> transitionsConfigurer) throws Exception {
        transitionsConfigurer.withExternal()
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.uploading)
                    .target(UploadsSessionEntity.UploadsSessionStatus.completed)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Merge)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.uploading)
                    .target(UploadsSessionEntity.UploadsSessionStatus.canceled)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Cancel);
    }
}
