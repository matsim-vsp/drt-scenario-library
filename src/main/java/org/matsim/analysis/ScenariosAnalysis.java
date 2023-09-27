package org.matsim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.utils.ScenariosTools;
import picocli.CommandLine;

import java.util.Arrays;

import static org.matsim.utils.ScenariosTools.SVN_FOLDER;

public class ScenariosAnalysis implements MATSimAppCommand {
    private static final Logger log = LogManager.getLogger(ScenariosAnalysis.class);

    @CommandLine.Option(names = "--scenario", description = "Scenario used, choose from: " +
            "BERLIN, LEIPZIG, MANHATTAN_TAXI, VULKANEIFEL, KELHEIM", defaultValue = "BERLIN")
    private ScenariosTools.RawScenarios rawScenario;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    public static void main(String[] args) {
        new ScenariosAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {

        Geometry serviceArea;
        if (!shp.isDefined()) {
            throw new RuntimeException("Please define the service area by input the shp");
        }
        serviceArea = shp.getGeometry();
        //TODO the size of the area is not correct!!!
        double serviceAreaSize = serviceArea.getArea() / 10e6;

        String configUrl;
        String plansUrl;
        switch (rawScenario) {
            case BERLIN -> {
                configUrl = SVN_FOLDER + "berlin/berlin-drt-open-scenario.config.xml";
                plansUrl = SVN_FOLDER + "berlin/berlin-10pct-trips.plans.xml.gz";
            }
            case LEIPZIG -> {
                configUrl = SVN_FOLDER + "leipzig/leipzig-drt-open-scenario.config.xml";
                plansUrl = SVN_FOLDER + "leipzig/leipzig-25pct-trips.plans.xml.gz";
            }
            case VULKANEIFEL -> {
                configUrl = SVN_FOLDER + "vulkaneifel/vulkaneifel-drt-open-scenario.config.xml";
                plansUrl = SVN_FOLDER + "vulkaneifel/vulkaneifel-25pct-trips.plans.xml.gz";
            }
            case KELHEIM -> {
                configUrl = SVN_FOLDER + "kelheim/kelheim-drt-open-scenario.config.xml";
                plansUrl = SVN_FOLDER + "kelheim/kelheim-25pct-trips.plans.xml.gz";
            }
            case MANHATTAN_TAXI -> {
                configUrl = SVN_FOLDER + "new-york-manhattan/nyc-drt.config.xml";
                plansUrl = SVN_FOLDER + "new-york-manhattan/manhattan-taxi-100pct.plans.xml.gz";
            }
            default -> throw new RuntimeException("Not implemented. Please choose from " +
                    Arrays.toString(ScenariosTools.RawScenarios.values()));
        }

        Config config = ConfigUtils.loadConfig(configUrl);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        Population plans = PopulationUtils.readPopulation(plansUrl);
        int numDemands = plans.getPersons().size();
        double demandDensity = numDemands / serviceAreaSize;

        double totalLinkLength = 0;
        for (Link link : network.getLinks().values()) {
            if (!link.getAllowedModes().contains(TransportMode.car)) {
                continue;
            }
            Coord coord = link.getToNode().getCoord();
            if (MGC.coord2Point(coord).within(serviceArea)) {
                totalLinkLength += link.getLength();
            }
        }

        totalLinkLength /= 1000;
        double networkDensity = totalLinkLength / serviceAreaSize;

        log.info("Max number of demands = " + numDemands);
        log.info("Size of the service area = " + serviceAreaSize + " km^2");
        log.info("Max demand density = " + demandDensity + " per km^2");
        log.info("Network density = " + networkDensity + " km / km^2");

        return 0;
    }
}
