package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.ScenariosTools;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.utils.Tools.downloadFile;

public class RandomSampling implements MATSimAppCommand {
    private static final Logger log = LogManager.getLogger(RandomSampling.class);

    @CommandLine.Option(names = "--target", description = "target folder of the scenario", required = true)
    private Path target;

    @CommandLine.Option(names = "--scenario", description = "Scenario used, choose from: " +
            "BERLIN, LEIPZIG, MANHATTAN_TAXI, VULKANEIFEL, KELHEIM", defaultValue = "BERLIN")
    private ScenariosTools.RawScenarios rawScenario;

    @CommandLine.Option(names = "--samples", description = "Desired down-sampled sizes in (0, 1]", arity = "1..*", required = true)
    private List<Double> samples;

    @CommandLine.Option(names = "--seed", description = "random seed for down sampling", defaultValue = "4711")
    private long seed;

    public static void main(String[] args) {
        new RandomSampling().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        log.info("Begin creating random sampled DRT scenario from " + rawScenario + " scenario");
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        String svnFolder = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/world/drt-scenario-library/drt-open-scenarios/";
        String configUrl;
        String plansUrl;
        double baseSample;
        switch (rawScenario) {
            case BERLIN -> {
                configUrl = svnFolder + "berlin/berlin_drt_config.xml";
                plansUrl = svnFolder + "berlin/berlin-10pct-trips.plans.xml.gz";
                baseSample = 0.1;
            }
            case LEIPZIG -> {
                configUrl = svnFolder + "leipzig/leipzig-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "leipzig/leipzig-25pct-trips.plans.xml.gz";
                baseSample = 0.25;
            }
            case VULKANEIFEL -> {
                configUrl = svnFolder + "vulkaneifel/vulkaneifel-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "vulkaneifel/vulkaneifel-25pct-trips.plans.xml.gz";
                baseSample = 0.25;
            }
            case KELHEIM -> {
                configUrl = svnFolder + "kelheim/kelheim-drt-open-scenario.config.xml";
                plansUrl = svnFolder + "kelheim/kelheim-25pct-trips.plans.xml.gz";
                baseSample = 0.25;
            }
            case MANHATTAN_TAXI -> {
                configUrl = svnFolder + "new-york-manhattan/nyc-drt.config.xml";
                plansUrl = svnFolder + "new-york-manhattan/manhattan-taxi-100pct.plans.xml.gz";
                baseSample = 1.0;
            }
            default -> throw new RuntimeException("Not implemented. Please choose from " +
                    Arrays.toString(ScenariosTools.RawScenarios.values()));
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
        Population inputPlans = PopulationUtils.readPopulation(plansUrl);
        Path plansFolder = Path.of(target + "/plans/");
        if (!Files.exists(plansFolder)) {
            Files.createDirectories(plansFolder);
        }

        samples.sort(Comparator.comparingDouble(Double::doubleValue).reversed());
        String orig = String.format("%dpct", Math.round(baseSample * 100));

        String originalPlansName = config.plans().getInputFile();
        for (Double sample : samples) {
            if (sample > baseSample) {
                log.warn(sample + " is larger than the maximum available sample size for this scenario, which is "
                        + baseSample + ". Skip this sample...");
                continue;
            }

            // down-sample previous samples
            sampleDownPopulation(inputPlans, sample / baseSample, seed);
            baseSample = sample;
            String newPlansName;
            double outputPct = sample * 100;
            if (outputPct % 1 == 0) {
                newPlansName = originalPlansName.replace(orig, String.format("%dpct-seed-%d", Math.round(outputPct), seed));
            } else {
                newPlansName = originalPlansName.replace(orig, String.format("%spct-seed-%d", outputPct, seed));
            }
            PopulationUtils.writePopulation(inputPlans, target + "/plans/" + newPlansName);
        }

        return 0;
    }

    private void sampleDownPopulation(Population population, double sample, long seed) {
        log.info("population size before down sampling=" + population.getPersons().size());
        Random random = new Random(seed);
        int toRemove = (int) ((1 - sample) * population.getPersons().size());
        List<Id<Person>> personList = new ArrayList<>(population.getPersons().keySet());
        Collections.shuffle(personList, random);
        for (int i = 0; i < toRemove; i++) {
            population.removePerson(personList.get(i));
        }
        log.info("population size after down sampling=" + population.getPersons().size());
    }
}
