package io.mongock.runner.core.builder;

import io.mongock.api.config.MongockConfiguration;
import io.mongock.runner.core.builder.roles.ChangeLogScanner;
import io.mongock.runner.core.builder.roles.DependencyInjectable;
import io.mongock.runner.core.builder.roles.DriverConnectable;
import io.mongock.runner.core.builder.roles.LegacyMigrator;
import io.mongock.runner.core.builder.roles.MigrationWriter;
import io.mongock.runner.core.builder.roles.MongockRunnable;
import io.mongock.runner.core.builder.roles.ServiceIdentificable;
import io.mongock.runner.core.builder.roles.SystemVersionable;
import io.mongock.runner.core.builder.roles.TransactionStrategiable;
import io.mongock.runner.core.builder.roles.Transactionable;
import io.mongock.runner.core.event.EventPublisher;

@SuppressWarnings("all")
public interface RunnerBuilder<
    SELF extends RunnerBuilder<SELF, CONFIG>,
    CONFIG extends MongockConfiguration>
    extends
		ChangeLogScanner<SELF, CONFIG>,
		MigrationWriter<SELF, CONFIG>,
		LegacyMigrator<SELF, CONFIG>,
		DriverConnectable<SELF, CONFIG>,
		SystemVersionable<SELF, CONFIG>,
		DependencyInjectable<SELF>,
		ServiceIdentificable<SELF, CONFIG>,
    MongockRunnable<SELF, CONFIG>,
		Transactionable<SELF, CONFIG>,
		TransactionStrategiable<SELF, CONFIG> {
  
  SELF setEventPublisher(EventPublisher eventPublisher);
}
