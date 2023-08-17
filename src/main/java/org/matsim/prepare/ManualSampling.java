package org.matsim.prepare;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.ScenariosTools;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.matsim.utils.Tools.downloadFile;

public class ManualSampling implements MATSimAppCommand {
    private static final Logger log = LogManager.getLogger(ManualSampling.class);

    @CommandLine.Option(names = "--target", description = "target folder of the scenario", required = true)
    private Path target;

    @CommandLine.Option(names = "--scenario", description = "Scenario used, choose from: " +
            "BERLIN, LEIPZIG, MANHATTAN_TAXI, VULKANEIFEL, KELHEIM", defaultValue = "BERLIN")
    private ScenariosTools.RawScenarios rawScenario;

    @CommandLine.Option(names = "--modes", description = "modes of original trips to be converted to DRT", arity = "1..*", required = true)
    private List<String> modes;

    @CommandLine.Option(names = "--samples", description = "percentage of the trips converted to DRT. " +
            "The sequence corresponds to modes", arity = "1..*", required = true)
    private List<Double> percent;

    @CommandLine.Option(names = "--plans-name", description = "name of the output plans", defaultValue = "manual-sampled.plans.xml.gz")
    private String plansName;

    public static void main(String[] args) {
        new ManualSampling().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Preconditions.checkArgument(modes.size() == percent.size(),
                "The length of modes and the length of samples must be equal");

        log.info("Begin creating manual sampled DRT scenario from " + rawScenario + " scenario");
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        String svnFolder = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/world/drt-scenario-library/drt-open-scenarios/";
        String configUrl;
        String plansUrl;
        switch (rawScenario) {
            case BERLIN -> {
                configUrl = svnFolder + "berlin/berlin-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "berlin/berlin-10pct-trips-for-manual-sampling.plans.xml.gz";
            }
            case LEIPZIG -> {
                configUrl = svnFolder + "leipzig/leipzig-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "leipzig/leipzig-25pct-trips-for-manual-sampling.plans.xml.gz";
            }
            case VULKANEIFEL -> {
                configUrl = svnFolder + "vulkaneifel/vulkaneifel-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "vulkaneifel/vulkaneifel-25pct-trips-for-manual-sampling.plans.xml.gz";
            }
            case KELHEIM -> {
                configUrl = svnFolder + "kelheim/kelheim-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "kelheim/kelheim-25pct-trips-for-manual-sampling.plans.xml.gz";
            }
            default -> throw new RuntimeException("MANHATTAN_TAXI does not need to use the manual sampling");
        }

        // Download config file
        log.info("Downloading config file");
        downloadFile(configUrl, target.toString() + "/" + rawScenario.toString().toLowerCase() + ".config.xml");
        Config config = ConfigUtils.loadConfig(configUrl, new MultiModeDrtConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // Download network file
        log.info("Downloading network file");
        String networkFileName = config.network().getInputFile();
        new NetworkWriter(scenario.getNetwork()).write(target.toString() + "/" + networkFileName);

        // Prepare plans file
        // Apply manual selection
        Population inputPlans = PopulationUtils.readPopulation(plansUrl);
        Population outputPlans = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        PopulationFactory populationFactory = outputPlans.getFactory();
        Random random = new Random(4711);
        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();
        int counter = 0;

        Map<String, Double> convertedModesMap = new HashMap<>();
        for (int i = 0; i < modes.size(); i++) {
            convertedModesMap.put(modes.get(i), percent.get(i));
        }

        for (Person person : inputPlans.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                String mode = mainModeIdentifier.identifyMainMode(trip.getTripElements());
                if (convertedModesMap.containsKey(mode)) {
                    if (random.nextDouble() < convertedModesMap.get(mode)) {
                        Person newPerson = populationFactory.createPerson(Id.createPersonId("drt-person-" + counter));
                        Plan plan = populationFactory.createPlan();
                        Activity act0 = populationFactory.createActivityFromLinkId("dummy", trip.getOriginActivity().getLinkId());
                        act0.setEndTime(trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new));
                        Leg leg = populationFactory.createLeg(TransportMode.drt);
                        Activity act1 = populationFactory.createActivityFromLinkId("dummy", trip.getDestinationActivity().getLinkId());
                        plan.addActivity(act0);
                        plan.addLeg(leg);
                        plan.addActivity(act1);
                        newPerson.addPlan(plan);
                        outputPlans.addPerson(newPerson);
                        counter++;
                    }
                }
            }
        }

        // write output plans
        PopulationUtils.writePopulation(outputPlans, target + "/" + plansName);

        return 0;
    }
}
