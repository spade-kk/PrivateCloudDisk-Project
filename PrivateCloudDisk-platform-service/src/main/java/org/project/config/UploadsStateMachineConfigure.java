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
                    .source(UploadsSessionEntity.UploadsSessionStatus.uploading)
                    .target(UploadsSessionEntity.UploadsSessionStatus.merging)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Merge)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.merging)
                    .target(UploadsSessionEntity.UploadsSessionStatus.merge_failed)
                    .event(UploadsSessionEntity.UploadsSessionEvent.MergeFailed)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.merging)
                    .target(UploadsSessionEntity.UploadsSessionStatus.calculating)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Calculate)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.calculating)
                    .target(UploadsSessionEntity.UploadsSessionStatus.calculating_failed)
                    .event(UploadsSessionEntity.UploadsSessionEvent.CalculateFailed)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.uploading)
                    .target(UploadsSessionEntity.UploadsSessionStatus.canceled)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Cancel)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.calculating)
                    .target(UploadsSessionEntity.UploadsSessionStatus.scanning)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Scan)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.scanning)
                    .target(UploadsSessionEntity.UploadsSessionStatus.scanning_failed)
                    .event(UploadsSessionEntity.UploadsSessionEvent.ScanFailed)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.scanning)
                    .target( UploadsSessionEntity.UploadsSessionStatus.processing)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Process)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.processing)
                    .target(UploadsSessionEntity.UploadsSessionStatus.processing_failed)
                    .event(UploadsSessionEntity.UploadsSessionEvent.ProcessFailed)
                .and()
                .withExternal()
                    .source(UploadsSessionEntity.UploadsSessionStatus.processing)
                    .target(UploadsSessionEntity.UploadsSessionStatus.completed)
                    .event(UploadsSessionEntity.UploadsSessionEvent.Complete);
    }
}
