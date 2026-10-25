package com.fastdrop.statemachine.ssm;

import com.fastdrop.statemachine.api.StateTransitionFailedException;
import com.fastdrop.statemachine.api.TransitionResult;
import com.fastdrop.statemachine.api.TransitionSubject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {
        AbstractStateMachineAdapterTest.SampleStateMachineConfig.class,
        AbstractStateMachineAdapterTest.SampleStateMachineAdapter.class
})
class AbstractStateMachineAdapterTest {

    enum SampleState { NEW, CHECK, ACTIVE, REJECTED, CANCELLED }
    enum SampleEvent { ACTIVATE, CANCEL, EXPLODE }

    static class SampleSubject implements TransitionSubject<SampleState> {
        final String id;
        final SampleState state;
        final boolean allowed;
        boolean activated;
        boolean cancelled;

        SampleSubject(String id, SampleState state, boolean allowed) {
            this.id = id;
            this.state = state;
            this.allowed = allowed;
        }

        @Override public String machineId() { return id; }
        @Override public SampleState currentState() { return state; }
    }

    @Configuration
    @EnableStateMachineFactory(name = "sampleSmFactory")
    static class SampleStateMachineConfig extends EnumStateMachineConfigurerAdapter<SampleState, SampleEvent> {

        @Override
        public void configure(StateMachineConfigurationConfigurer<SampleState, SampleEvent> config) throws Exception {
            config.withConfiguration()
                    .autoStartup(false)
                    .listener(new MetricsStateMachineListener<>("sample"));
        }

        @Override
        public void configure(StateMachineStateConfigurer<SampleState, SampleEvent> states) throws Exception {
            states.withStates()
                    .initial(SampleState.NEW)
                    .choice(SampleState.CHECK)
                    .states(EnumSet.allOf(SampleState.class))
                    .end(SampleState.REJECTED)
                    .end(SampleState.CANCELLED);
        }

        @Override
        public void configure(StateMachineTransitionConfigurer<SampleState, SampleEvent> transitions) throws Exception {
            transitions
                    .withExternal().source(SampleState.NEW).target(SampleState.CHECK).event(SampleEvent.ACTIVATE)
                    .and()
                    .withChoice().source(SampleState.CHECK)
                    .first(SampleState.ACTIVE,
                            new ContextAwareGuard<SampleState, SampleEvent, SampleSubject>() {
                                @Override
                                protected boolean test(SampleSubject s, StateContext<SampleState, SampleEvent> ctx) {
                                    return s.allowed;
                                }
                            },
                            new ContextAwareAction<SampleState, SampleEvent, SampleSubject>() {
                                @Override
                                protected void run(SampleSubject s, StateContext<SampleState, SampleEvent> ctx) {
                                    s.activated = true;
                                }
                            })
                    .last(SampleState.REJECTED)
                    .and()
                    .withExternal().source(SampleState.ACTIVE).target(SampleState.CANCELLED).event(SampleEvent.CANCEL)
                    .action(new ContextAwareAction<SampleState, SampleEvent, SampleSubject>() {
                        @Override
                        protected void run(SampleSubject s, StateContext<SampleState, SampleEvent> ctx) {
                            s.cancelled = true;
                        }
                    })
                    .and()
                    .withExternal().source(SampleState.ACTIVE).target(SampleState.CANCELLED).event(SampleEvent.EXPLODE)
                    .action(new ContextAwareAction<SampleState, SampleEvent, SampleSubject>() {
                        @Override
                        protected void run(SampleSubject s, StateContext<SampleState, SampleEvent> ctx) {
                            throw new IllegalStateException("Fallo simulado dentro de una action");
                        }
                    });
        }
    }

    @Component
    static class SampleStateMachineAdapter extends AbstractStateMachineAdapter<SampleSubject, SampleState, SampleEvent> {
        SampleStateMachineAdapter(StateMachineFactory<SampleState, SampleEvent> factory) {
            super(factory);
        }
    }

    @Autowired
    private SampleStateMachineAdapter adapter;

    @Test
    @DisplayName("Debe evaluar choice state con guard verdadera y ejecutar action")
    void shouldTransitionThroughChoiceWhenGuardIsTrue() {
        SampleSubject subject = new SampleSubject("ID-1", SampleState.NEW, true);
        TransitionResult<SampleState> result = adapter.fire(subject, SampleEvent.ACTIVATE);

        assertThat(result.accepted()).isTrue();
        assertThat(result.source()).isEqualTo(SampleState.NEW);
        assertThat(result.target()).isEqualTo(SampleState.ACTIVE);
        assertThat(subject.activated).isTrue();
    }

    @Test
    @DisplayName("Debe tomar la rama fallback de choice cuando el guard es falso")
    void shouldTakeFallbackBranchWhenGuardIsFalse() {
        SampleSubject subject = new SampleSubject("ID-2", SampleState.NEW, false);
        TransitionResult<SampleState> result = adapter.fire(subject, SampleEvent.ACTIVATE);

        assertThat(result.accepted()).isTrue();
        assertThat(result.target()).isEqualTo(SampleState.REJECTED);
        assertThat(subject.activated).isFalse();
    }

    @Test
    @DisplayName("Debe rehidratar desde un estado intermedio persistido y transicionar a estado final")
    void shouldRehydrateFromPersistedIntermediateState() {
        SampleSubject subject = new SampleSubject("ID-3", SampleState.ACTIVE, true);
        TransitionResult<SampleState> result = adapter.fire(subject, SampleEvent.CANCEL, Map.of("reason", "test"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.source()).isEqualTo(SampleState.ACTIVE);
        assertThat(result.target()).isEqualTo(SampleState.CANCELLED);
        assertThat(subject.cancelled).isTrue();
    }

    @Test
    @DisplayName("Debe devolver accepted=false cuando el evento no es válido para el estado actual")
    void shouldReturnDeniedWhenTransitionNotAllowed() {
        SampleSubject subject = new SampleSubject("ID-4", SampleState.CANCELLED, true);
        TransitionResult<SampleState> result = adapter.fire(subject, SampleEvent.CANCEL);

        assertThat(result.accepted()).isFalse();
        assertThat(result.target()).isEqualTo(SampleState.CANCELLED);
    }

    @Test
    @DisplayName("Debe propagar excepciones lanzadas dentro de una action como StateTransitionFailedException")
    void shouldPropagateActionExceptions() {
        SampleSubject subject = new SampleSubject("ID-5", SampleState.ACTIVE, true);

        assertThatThrownBy(() -> adapter.fire(subject, SampleEvent.EXPLODE))
                .isInstanceOf(StateTransitionFailedException.class)
                .hasMessageContaining("ID-5")
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
