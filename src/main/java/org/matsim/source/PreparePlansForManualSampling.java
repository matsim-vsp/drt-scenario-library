package org.matsim.source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.locationtech.jts.geom.Geometry;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.DefaultAnalysisMainModeIdentifier;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PreparePlansForManualSampling implements MATSimAppCommand {
    @CommandLine.Option(names = "--input", description = "path to input plans", required = true)
    private String inputPopulation;

    @CommandLine.Option(names = "--output", description = "output drt plans", required = true)
    private String outputPopulation;

    @CommandLine.Option(names = "--network", description = "path to network file", required = true)
    private String networkPath;

    @CommandLine.Option(names = "--start-time", description = "Service hour start time", defaultValue = "3600")
    private double startTime;

    @CommandLine.Option(names = "--end-time", description = "Service hour end time", defaultValue = "86400")
    private double endTime;

    @CommandLine.Option(names = "--min-euclidean-distance", description = "filter out short trips", defaultValue = "500")
    private double minTripEuclideanDistance;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    private final Logger log = LogManager.getLogger(PreparePlansForManualSampling.class);

    public static void main(String[] args) {
        new PreparePlansForManualSampling().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Geometry serviceArea = shp.isDefined() ? shp.getGeometry() : null;
        if (serviceArea == null) {
            log.warn("Service area is not defined! Will use the whole plans. " +
                    "This may cause problem (if it is not deliberately set in this way)");
        }

        Population inputPlans = PopulationUtils.readPopulation(inputPopulation);
        Population outputPlans = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        PopulationFactory populationFactory = inputPlans.getFactory();
        Network network = NetworkUtils.readNetwork(networkPath);
        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        List<TripStructureUtils.Trip> allRelevantTrips = PrepareAllPossibleDrtTrips.collectAllRelevantTripsFromInputPlans
                (inputPlans, network, serviceArea, startTime, endTime, minTripEuclideanDistance, log);
        PrepareAllPossibleDrtTrips.processNetwork(network);

        int counter = 0;
        for (TripStructureUtils.Trip trip : allRelevantTrips) {
            Coord fromCoord = trip.getOriginActivity().getCoord();
            Coord toCoord = trip.getDestinationActivity().getCoord();
            Link fromLink = NetworkUtils.getNearestLink(network, fromCoord);
            Link toLink = NetworkUtils.getNearestLink(network, toCoord);

            Activity fromAct = populationFactory.createActivityFromLinkId("dummy", fromLink.getId());
            fromAct.setEndTime(trip.getOriginActivity().getEndTime().orElseThrow(RuntimeException::new));
            Leg leg = populationFactory.createLeg(mainModeIdentifier.identifyMainMode(trip.getTripElements()));
            Activity toAct = populationFactory.createActivityFromLinkId("dummy", toLink.getId());

            Person person = populationFactory.createPerson(Id.createPersonId("drt_person_" + counter));
            Plan plan = populationFactory.createPlan();
            plan.addActivity(fromAct);
            plan.addLeg(leg);
            plan.addActivity(toAct);
            person.addPlan(plan);
            outputPlans.addPerson(person);
            counter++;
        }

        Path folder = Path.of(outputPopulation).getParent();
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }
        PopulationWriter populationWriter = new PopulationWriter(outputPlans);
        populationWriter.write(outputPopulation);

        return 0;
    }
}
