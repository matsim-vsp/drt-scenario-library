package org.matsim.run;

import org.matsim.contrib.drt.analysis.afterSimAnalysis.DrtVehicleStoppingTaskWriter;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.modules.LinearDrtStopDurationModule;
import org.matsim.utils.ScenariosTools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class RunUseCase {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new RuntimeException("Please specify the use case in the argument. Choose from: "
                    + Arrays.toString(ScenariosTools.UseCases.values()));
        }

        ScenariosTools.UseCases useCase = ScenariosTools.UseCases.valueOf(args[0].toUpperCase());

        String configPath;
        switch (useCase) {
            case BERLIN_DRT -> configPath = "scenarios/berlin-drt/berlin-drt.config.xml";
            case LEIPZIG_DRT -> configPath = "scenarios/leipzig-drt/leipzig-drt.config.xml";
            case VULKANEIFEL_SCHOOL_TRANSPORT -> configPath = "scenarios/vulkaneifel-school-transport/vulkaneifel-school-transport.config.xml";
            case KELHEIM_KEXI -> configPath = "scenarios/kelheim-kexi/kelheim-drt.config.xml";
            case ORANIENBURG -> configPath = "scenarios/oranienburg/oranienburg-drt.config.xml";
            default -> throw new RuntimeException("Not implemented. Please choose from " +
                    "[BERLIN_DRT, LEIPZIG_DRT, VULKANEIFEL_SCHOOL_TRANSPORT, KELHEIM_KEXI, ORANIENBURG]");
        }

        Config config = ConfigUtils.loadConfig(configPath, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        Controler controler = DrtControlerCreator.createControler(config, false);

        MultiModeDrtConfigGroup multiModeDrtConfigGroup = MultiModeDrtConfigGroup.get(config);
        for (DrtConfigGroup drtCfg : multiModeDrtConfigGroup.getModalElements()) {
            // Add linear stop duration module
            controler.addOverridingModule(new LinearDrtStopDurationModule(drtCfg));
        }

        controler.run();

        // Plot DRT stopping tasks
        new DrtVehicleStoppingTaskWriter(Path.of(config.controler().getOutputDirectory())).run();

    }

}
