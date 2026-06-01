package org.project.config;

import org.project.data.UploadsSessionData;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachine(name = "uploadsStateMachine")
public class UploadsStateMachineConfigure extends EnumStateMachineConfigurerAdapter<UploadsSessionData.UploadsSessionStatus, UploadsSessionData.UploadsSessionEvent> {
    @Override
    public void configure(StateMachineStateConfigurer<UploadsSessionData.UploadsSessionStatus, UploadsSessionData.UploadsSessionEvent> statesConfigurer) throws Exception {
        statesConfigurer.withStates()
                .initial(UploadsSessionData.UploadsSessionStatus.uploading)
                .states(EnumSet.allOf(UploadsSessionData.UploadsSessionStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<UploadsSessionData.UploadsSessionStatus, UploadsSessionData.UploadsSessionEvent> transitionsConfigurer) throws Exception {
        transitionsConfigurer.withExternal()
                    .source(UploadsSessionData.UploadsSessionStatus.uploading)
                    .target(UploadsSessionData.UploadsSessionStatus.merging)
                    .event(UploadsSessionData.UploadsSessionEvent.Merge)
                .and()
                .withExternal()
                    .source(UploadsSessionData.UploadsSessionStatus.merging)
                    .target(UploadsSessionData.UploadsSessionStatus.completed)
                    .event(UploadsSessionData.UploadsSessionEvent.Complete)
                .and()
                .withExternal()
                    .source(UploadsSessionData.UploadsSessionStatus.merging)
                    .target(UploadsSessionData.UploadsSessionStatus.failed)
                    .event(UploadsSessionData.UploadsSessionEvent.Fail);
    }
}
