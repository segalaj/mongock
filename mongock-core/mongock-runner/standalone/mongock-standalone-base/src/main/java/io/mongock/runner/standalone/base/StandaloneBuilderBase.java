package io.mongock.runner.standalone.base;

import io.mongock.runner.core.internal.ChangeLogItem;
import io.mongock.runner.core.internal.ChangeSetItem;
import io.mongock.api.config.MongockConfiguration;
import io.mongock.driver.api.entry.ChangeEntry;
import io.mongock.runner.core.builder.BuilderType;
import io.mongock.runner.core.builder.RunnerBuilderBase;
import io.mongock.runner.core.event.EventPublisher;
import io.mongock.runner.core.event.MigrationFailureEvent;
import io.mongock.runner.core.event.MigrationStartedEvent;
import io.mongock.runner.core.event.MigrationSuccessEvent;
import io.mongock.runner.core.executor.ExecutorFactory;
import io.mongock.runner.core.executor.changelog.ChangeLogServiceBase;
import io.mongock.runner.core.executor.dependency.DependencyManager;

import java.util.function.Consumer;

public abstract class StandaloneBuilderBase<
    SELF extends StandaloneBuilderBase<SELF, CHANGELOG, CHANGESET, CONFIG>,
    CHANGELOG extends ChangeLogItem<CHANGESET>,
    CHANGESET extends ChangeSetItem,
    
    CONFIG extends MongockConfiguration>
    extends RunnerBuilderBase<SELF, CHANGELOG, CHANGESET, CONFIG> {

  protected StandaloneBuilderBase(BuilderType builderType,
                                  ExecutorFactory<CHANGELOG, CHANGESET, CONFIG> executorFactory,
                                  ChangeLogServiceBase<CHANGELOG, CHANGESET> changeLogService,
                                  CONFIG config) {
    super(builderType, executorFactory, changeLogService, new DependencyManager(), config);
  }

  public SELF setMigrationStartedListener(Consumer<MigrationStartedEvent> listener) {
    this.eventPublisher = new EventPublisher(
        () -> listener.accept(new MigrationStartedEvent()),
        eventPublisher.getMigrationSuccessListener(),
        eventPublisher.getMigrationFailedListener());
    return getInstance();
  }

  public SELF setMigrationSuccessListener(Consumer<MigrationSuccessEvent> listener) {
    this.eventPublisher = new EventPublisher(
        eventPublisher.getMigrationStartedListener(),
        result -> listener.accept(new MigrationSuccessEvent(result)),
        eventPublisher.getMigrationFailedListener());
    return getInstance();
  }

  public SELF setMigrationFailureListener(Consumer<MigrationFailureEvent> listener) {
    this.eventPublisher = new EventPublisher(
        eventPublisher.getMigrationStartedListener(),
        eventPublisher.getMigrationSuccessListener(),
        result -> listener.accept(new MigrationFailureEvent(result)));
    return getInstance();
  }


}
