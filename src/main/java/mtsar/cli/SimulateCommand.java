package mtsar.cli;

import io.dropwizard.Application;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.setup.Environment;
import mtsar.api.ProcessDefinition;
import mtsar.api.sql.ProcessDAO;
import mtsar.dropwizard.MechanicalTsarApplication;
import mtsar.dropwizard.MechanicalTsarConfiguration;
import net.sourceforge.argparse4j.inf.Namespace;

public class SimulateCommand extends EnvironmentCommand<MechanicalTsarConfiguration> {
    private final MechanicalTsarApplication application;

    public SimulateCommand(Application<MechanicalTsarConfiguration> application) {
        super(application, "simulate", "Runs the simulation");
        this.application = (MechanicalTsarApplication) application;
    }

    protected void run(Environment environment, Namespace namespace, MechanicalTsarConfiguration configuration) {
        final ProcessDAO processDAO = application.getInjector().getInstance(ProcessDAO.class);
    }
}